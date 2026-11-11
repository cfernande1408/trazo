import type { GeoJSONSource, Map as MlMap, RasterDEMSourceSpecification } from 'maplibre-gl'
import { bearing, bounds, flyMs, pointAt, stats, type Segments } from './geo'

const EXAGGERATION = 1.6
const empty = { type: 'FeatureCollection', features: [] } as GeoJSON.FeatureCollection

/**
 * La vista 3D de la app en el mismo mapa: relieve, cielo y casas con su
 * altura, y el vuelo virtual que recorre la ruta con la cámara.
 */
export class Terrain3D {
  on = false
  private anim = 0
  private onFlyEnd?: () => void
  /** Cada vuelo lleva su número: uno parado no arranca tarde. */
  private run = 0

  constructor(private map: MlMap) {}

  get flying() { return this.anim !== 0 }

  /** Fuente de relieve aparte para el 3D (MapLibre recomienda no compartirla con el sombreado). */
  private ensureLayers() {
    const m = this.map
    if (!m.getSource('terrain3d')) {
      const t = m.getStyle().sources.terrain as RasterDEMSourceSpecification
      // A z13 basta (~19 m/píxel) y pide muchas menos teselas mientras vuela
      m.addSource('terrain3d', { ...t, maxzoom: 13 })
    }
    if (!m.getSource('rider')) {
      m.addSource('rider', { type: 'geojson', data: empty })
      m.addLayer({ id: 'rider', type: 'circle', source: 'rider', paint: { 'circle-radius': 9, 'circle-color': '#C5F26B', 'circle-stroke-color': '#1A1C16', 'circle-stroke-width': 3 } })
    }
    if (!m.getLayer('buildings-3d') && m.getSource('omt')) {
      m.addLayer({
        id: 'buildings-3d', type: 'fill-extrusion', source: 'omt', 'source-layer': 'building', minzoom: 13,
        layout: { visibility: 'none' },
        paint: {
          'fill-extrusion-color': '#E6DFCD',
          'fill-extrusion-height': ['coalesce', ['get', 'render_height'], 6],
          'fill-extrusion-base': ['coalesce', ['get', 'render_min_height'], 0],
          'fill-extrusion-opacity': 0.92,
        },
      }, m.getLayer('place-label') ? 'place-label' : undefined)
    }
  }

  /** Enciende el 3D y encuadra la ruta inclinada (o inclina la vista que haya). */
  enable(segments?: Segments) {
    const m = this.map
    this.ensureLayers()
    m.setTerrain({ source: 'terrain3d', exaggeration: EXAGGERATION })
    try {
      // Cielo azul y niebla solo en el horizonte, como en la app
      m.setSky({ 'sky-color': '#7FB7E3', 'horizon-color': '#DCE8F0', 'fog-color': '#E4E9E6', 'sky-horizon-blend': 0.6, 'horizon-fog-blend': 0.9, 'fog-ground-blend': 1, 'atmosphere-blend': 0 })
    } catch { /* versiones sin cielo */ }
    m.setLayoutProperty('buildings-3d', 'visibility', 'visible')
    if (m.getLayer('building')) m.setLayoutProperty('building', 'visibility', 'none')
    this.on = true
    const b = segments && bounds(segments)
    // Encuadre calculado en plano y luego inclinado: fitBounds con inclinación dejaba la
    // ruta fuera, por arriba. Algo más lejos para que quepa la parte del fondo.
    const cam = b && m.cameraForBounds([[b[0], b[1]], [b[2], b[3]]], { padding: 60, bearing: -20, maxZoom: 15 })
    if (cam?.center && cam.zoom != null) {
      const view = { center: cam.center, zoom: cam.zoom - 0.4, bearing: -20, pitch: 60 }
      m.easeTo({ ...view, duration: 1200 })
      // Al llegar el relieve, el suelo sube (en la sierra, más de 1.500 m con la exageración) y
      // la vista inclinada se va hacia la cámara: se vuelve a centrar con el relieve ya puesto
      m.once('idle', () => { if (this.on && !this.flying) m.easeTo({ ...view, duration: 700 }) })
    } else m.easeTo({ pitch: 60, bearing: -20, duration: 800 })
  }

  disable() {
    const m = this.map
    this.stop()
    if (!this.on) return
    this.on = false
    m.setTerrain(null)
    if (m.getLayer('buildings-3d')) m.setLayoutProperty('buildings-3d', 'visibility', 'none')
    if (m.getLayer('building')) m.setLayoutProperty('building', 'visibility', 'visible')
    m.easeTo({ pitch: 0, bearing: 0, duration: 800 })
  }

  /** Vuelo virtual: la cámara sigue la ruta de principio a fin. */
  fly(segments: Segments, onEnd?: () => void) {
    const m = this.map
    const total = stats(segments).distanceMeters
    const start = pointAt(segments, 0)
    if (!start || total <= 0) return
    if (!this.on) this.enable()
    this.stop()
    const run = ++this.run
    this.onFlyEnd = onEnd
    const duration = flyMs(total)
    // Cámara algo alta: menos teselas por segundo y sin paredes de relieve sin cargar
    const ZOOM = 14, PITCH = 66
    const at = (d: number) => pointAt(segments, Math.min(total, Math.max(0, d)))!
    m.jumpTo({ center: [start.lon, start.lat], zoom: ZOOM, pitch: PITCH, bearing: bearing(start, at(300)) })
    let t0: number | null = null
    const step = (t: number) => {
      if (run !== this.run) return
      t0 ??= t
      const f = Math.min(1, (t - t0) / duration)
      const p = at(f * total)
      const target = bearing(p, at(f * total + 400))
      const cur = m.getBearing()
      const diff = ((target - cur + 540) % 360) - 180
      m.jumpTo({ center: [p.lon, p.lat], bearing: cur + diff * 0.06, pitch: PITCH, zoom: ZOOM })
      ;(m.getSource('rider') as GeoJSONSource | undefined)?.setData({ type: 'Point', coordinates: [p.lon, p.lat] })
      if (f < 1) this.anim = requestAnimationFrame(step)
      else this.finish()
    }
    // Arranca cuando la salida ya está pintada (o a los 4 s como mucho)
    let started = false
    const go = () => {
      if (started || run !== this.run || this.anim !== -1) return
      started = true
      this.anim = requestAnimationFrame(step)
    }
    m.once('idle', go)
    setTimeout(go, 4000)
    this.anim = -1 // en marcha, esperando a que pinte
  }

  private finish() {
    this.anim = 0
    ;(this.map.getSource('rider') as GeoJSONSource | undefined)?.setData(empty)
    const cb = this.onFlyEnd
    this.onFlyEnd = undefined
    cb?.()
  }

  stop() {
    this.run++
    if (this.anim > 0) cancelAnimationFrame(this.anim)
    if (this.anim !== 0) this.finish()
  }
}
