import { L } from './i18n'
import type { GeoJSONSource, Map as MlMap } from 'maplibre-gl'
import type { Activity, Segments } from './geo'
import { friendly, getJson, overpass } from './net'
import {
  contains, expand, flavoursFor, MIN_ZOOM, mercBbox, mercCenter, osmUrl, overpassArea, overpassRoute, parseOverpassArea,
  matchesQuery, parseOverpassRoute, parsePlaces, parseWmtDetails, parseWmtList, parseWmtSegments, placeUrl, SHAPE_LABEL, stitch,
  summaries, wmtByArea, wmtDetails, wmtSearch, wmtSegments, zoomFor, type Bbox, type Flavour, type OsmRoute, type Place, type Shape,
} from './osm'

/** Lo que se descarga de una ruta de OSM al tocarla. */
export interface LoadedOsmRoute {
  name: string
  description?: string
  segments: Segments
  link: string
}

const ZOOM_HINT = L('Acerca el mapa para ver las rutas · Acercar aquí', 'Zoom in to see the routes · Zoom in here')
const KIND_COLOR = { mtb: '#4A8A1C', cycling: '#1F8A84', hiking: '#B8650A' }
const escapeHtml = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!)

/**
 * Explorar como en la app: al parar el mapa se piden las rutas de la zona
 * (Waymarked Trails y, si falla, Overpass) y salen como iconos; tocando una
 * se descarga su trazado. También el buscador de lugares y rutas.
 */
export class Explorer {
  private routes = new Map<number, OsmRoute>()
  private covered: Bbox[] = []
  private load?: AbortController
  private timer?: ReturnType<typeof setTimeout>
  private onlyMtb = false
  private shape: Shape | null = null
  private status: { text: string; retry: boolean } | null = null
  private selectedId: number | null = null

  constructor(
    private map: MlMap,
    private activity: Activity,
    private el: { filters: HTMLElement; status: HTMLElement; list: HTMLElement },
    private onPick: (r: OsmRoute) => void,
  ) {
    map.on('moveend', () => this.onViewport())
    map.on('click', 'explore', (e) => {
      const id = e.features?.[0]?.properties?.id
      const r = typeof id === 'number' ? this.routes.get(id) : undefined
      if (r) this.onPick(r)
    })
    map.on('mouseenter', 'explore', () => (map.getCanvas().style.cursor = 'pointer'))
    map.on('mouseleave', 'explore', () => (map.getCanvas().style.cursor = ''))
    el.status.addEventListener('click', () => {
      // El aviso de acercar también acerca: al zoom en el que ya salen las rutas
      if (this.status?.text === ZOOM_HINT) this.map.easeTo({ zoom: MIN_ZOOM + 0.6 })
      else if (this.status?.retry) this.onViewport(true)
    })
    this.renderFilters()
  }

  /** Capa de iconos de rutas (se llama cuando el estilo del mapa está listo). */
  addLayers() {
    this.map.addSource('explore', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
    this.map.addLayer({
      id: 'explore', type: 'circle', source: 'explore',
      paint: {
        'circle-radius': ['case', ['boolean', ['get', 'sel'], false], 10, 7],
        'circle-color': ['get', 'color'],
        'circle-stroke-color': '#FFFFFF',
        'circle-stroke-width': 2.5,
      },
    })
    this.map.addLayer({
      id: 'explore-label', type: 'symbol', source: 'explore', minzoom: 12.5,
      layout: { 'text-field': ['get', 'name'], 'text-size': 12, 'text-offset': [0, 1.2], 'text-anchor': 'top', 'text-max-width': 10, 'text-font': ['Noto Sans Regular'] },
      paint: { 'text-color': '#1A1C16', 'text-halo-color': '#FFFFFF', 'text-halo-width': 1.5 },
    })
    this.render()
    this.onViewport()
  }

  setActivity(a: Activity) {
    if (a === this.activity) return
    this.activity = a
    this.routes.clear()
    this.covered = []
    this.renderFilters()
    this.render()
    this.onViewport(true)
  }

  select(id: number | null) {
    this.selectedId = id
    this.render()
  }

  private passes = (r: OsmRoute) => (!this.onlyMtb || r.isMtb) && (this.shape == null || r.shape === this.shape)

  private flavourOf(r: OsmRoute): Flavour {
    return this.activity === 'hike' ? 'hiking' : r.isMtb ? 'mtb' : 'cycling'
  }

  private setStatus(text: string | null, retry = false) {
    this.status = text ? { text, retry } : null
    this.el.status.hidden = !text
    this.el.status.textContent = text ?? ''
    this.el.status.classList.toggle('retry', retry)
    this.el.status.classList.toggle('zoom', text === ZOOM_HINT)
  }

  private view(): Bbox {
    const b = this.map.getBounds()
    return { south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() }
  }

  /** Cada vez que el mapa se para: carga las rutas de la zona si faltan. */
  private onViewport(force = false) {
    clearTimeout(this.timer)
    this.renderList()
    if (this.map.getZoom() < MIN_ZOOM) {
      this.load?.abort()
      this.setStatus(ZOOM_HINT)
      return
    }
    const view = this.view()
    if (!force && this.covered.some((c) => contains(c, view))) {
      if (this.status?.text === ZOOM_HINT) this.setStatus(null)
      return
    }
    // Por si sigues moviendo el mapa
    this.timer = setTimeout(() => this.fetchArea(expand(view, 0.15)), 350)
  }

  private async fetchArea(area: Bbox) {
    this.load?.abort()
    const ctl = new AbortController()
    this.load = ctl
    this.setStatus(L('Buscando rutas en esta zona…', 'Looking for routes in this area…'))
    try {
      const found = await this.findRoutes(area, ctl.signal)
      for (const r of found) {
        // Una ruta vista entera antes sabe su forma; si ahora se ve solo un trozo, no se olvida
        const known = this.routes.get(r.id)?.shape
        this.routes.set(r.id, r.shape === 'unknown' && known && known !== 'unknown' ? { ...r, shape: known } : r)
      }
      this.covered.push(area)
      this.setStatus(this.routes.size ? null : L('No hay rutas mapeadas en esta zona', 'No mapped routes in this area'))
      this.render()
    } catch (e) {
      if (ctl.signal.aborted) return
      this.setStatus(`${friendly(e)} ${L('Toca para reintentar.', 'Tap to retry.')}`, true)
    }
  }

  /** Waymarked Trails (casi instantáneo, MTB y ciclistas a la vez) y, si falla, Overpass. */
  private async findRoutes(area: Bbox, signal: AbortSignal): Promise<OsmRoute[]> {
    const results = await Promise.allSettled(flavoursFor(this.activity).map(async (f) => {
      const listed = parseWmtList(await getJson(wmtByArea(f, area), signal))
      if (!listed.length) return []
      const segs = parseWmtSegments(await getJson(wmtSegments(f, mercBbox(area), listed.map((l) => l.id)), signal))
      return summaries(f, listed, segs, area)
    }))
    if (results.some((r) => r.status === 'fulfilled')) {
      const seen = new Set<number>()
      // Si una ruta sale en las dos listas, gana la de MTB (va primero)
      return results.flatMap((r) => (r.status === 'fulfilled' ? r.value : [])).filter((r) => !seen.has(r.id) && !!seen.add(r.id))
    }
    if (signal.aborted) throw new DOMException(L('Cancelado', 'Cancelled'), 'AbortError')
    const kinds = this.activity === 'hike' ? 'hiking|foot' : 'mtb|bicycle'
    return parseOverpassArea(await overpass(overpassArea(area, kinds), signal))
  }

  /**
   * El trazado de una ruta: primero el simplificado de Waymarked Trails, que
   * llega enseguida, y luego el exacto de Overpass si contesta pronto.
   */
  async loadRoute(r: OsmRoute, signal: AbortSignal, onQuick: (q: LoadedOsmRoute) => void): Promise<LoadedOsmRoute> {
    const f = this.flavourOf(r)
    let quick: LoadedOsmRoute | undefined
    try {
      const d = parseWmtDetails(await getJson(wmtDetails(f, r.id), signal))
      if (d.merc) {
        const lines = parseWmtSegments(await getJson(wmtSegments(f, d.merc, [r.id]), signal)).get(r.id)
        if (lines?.length) {
          quick = { name: r.name, description: d.description ?? r.description, segments: stitch(lines), link: osmUrl(r.id) }
          onQuick(quick)
        }
      }
    } catch (e) {
      if (signal.aborted) throw e
    }
    const timeout = AbortSignal.timeout(quick ? 8_000 : 30_000)
    try {
      const exact = parseOverpassRoute(await overpass(overpassRoute(r.id), AbortSignal.any([signal, timeout])), r.id)
      return { name: r.name || exact.name, description: quick?.description ?? exact.description, segments: exact.segments, link: osmUrl(r.id) }
    } catch (e) {
      if (signal.aborted) throw e
      if (quick) return quick
      throw new Error(L('No se pudo descargar el recorrido', 'Could not download the track'))
    }
  }

  /** Lugares (Nominatim) y rutas con ese nombre (Waymarked Trails), a la vez. */
  async search(q: string, signal: AbortSignal): Promise<{ places: Place[]; routes: OsmRoute[]; error?: string }> {
    const [p, r] = await Promise.allSettled([
      getJson(placeUrl(q), signal).then(parsePlaces),
      this.routesNamed(q, signal),
    ])
    const routes = r.status === 'fulfilled' ? r.value : []
    // Las rutas encontradas se añaden al mapa para poder tocarlas
    for (const x of routes) if (!this.routes.has(x.id)) this.routes.set(x.id, x)
    this.render()
    return {
      places: p.status === 'fulfilled' ? p.value : [],
      routes,
      error: p.status === 'rejected' && r.status === 'rejected' ? friendly(p.reason) : undefined,
    }
  }

  /** La lista no trae posición, así que se pide el recuadro de cada una. */
  private async routesNamed(q: string, signal: AbortSignal): Promise<OsmRoute[]> {
    const lists = await Promise.allSettled(flavoursFor(this.activity).map(async (f) => {
      const listed = parseWmtList(await getJson(wmtSearch(f, q), signal)).filter((l) => matchesQuery(l.name, l.ref, q)).slice(0, 8)
      const found = await Promise.allSettled(listed.map(async (l): Promise<OsmRoute> => {
        const d = parseWmtDetails(await getJson(wmtDetails(f, l.id), signal))
        const c = mercCenter(d.merc!)!
        return { id: l.id, name: l.name, ref: l.ref, lat: c.lat, lon: c.lon, isMtb: f === 'mtb', shape: 'unknown', description: d.description }
      }))
      return found.flatMap((x) => (x.status === 'fulfilled' ? [x.value] : []))
    }))
    const seen = new Set<number>()
    return lists.flatMap((l) => (l.status === 'fulfilled' ? l.value : [])).filter((r) => !seen.has(r.id) && !!seen.add(r.id))
  }

  goToPlace(p: Place) {
    this.map.flyTo({ center: [p.lon, p.lat], zoom: zoomFor(p) })
  }

  // ---- Dibujo ----

  private render() {
    const src = this.map.getSource('explore') as GeoJSONSource | undefined
    src?.setData({
      type: 'FeatureCollection',
      features: [...this.routes.values()].filter(this.passes).map((r) => ({
        type: 'Feature',
        properties: { id: r.id, name: r.name, color: KIND_COLOR[this.flavourOf(r)], sel: r.id === this.selectedId },
        geometry: { type: 'Point', coordinates: [r.lon, r.lat] },
      })),
    })
    this.renderList()
  }

  private renderFilters() {
    const chips: string[] = []
    if (this.activity === 'bike') chips.push(`<button type="button" class="chip" data-f="mtb" aria-pressed="${this.onlyMtb}">${L('Solo MTB', 'MTB only')}</button>`)
    chips.push(
      `<select class="chip" data-f="shape" aria-label="${L('Circular o lineal', 'Loop or one way')}">
        <option value="">${L('Circular o lineal', 'Loop or one way')}</option>
        <option value="loop"${this.shape === 'loop' ? ' selected' : ''}>${L('Solo circulares', 'Loops only')}</option>
        <option value="linear"${this.shape === 'linear' ? ' selected' : ''}>${L('Solo lineales', 'One way only')}</option>
      </select>`,
    )
    this.el.filters.innerHTML = chips.join('')
    this.el.filters.querySelector('[data-f="mtb"]')?.addEventListener('click', () => {
      this.onlyMtb = !this.onlyMtb
      this.renderFilters()
      this.render()
    })
    this.el.filters.querySelector<HTMLSelectElement>('[data-f="shape"]')!.addEventListener('change', (e) => {
      this.shape = ((e.target as HTMLSelectElement).value || null) as Shape | null
      this.render()
    })
  }

  /** Las rutas que se ven, de la más cercana al centro a la más lejana. */
  private renderList() {
    if (this.map.getZoom() < MIN_ZOOM) {
      this.el.list.innerHTML = ''
      return
    }
    const v = this.view()
    const c = this.map.getCenter()
    const visible = [...this.routes.values()]
      .filter((r) => r.lat >= v.south && r.lat <= v.north && r.lon >= v.west && r.lon <= v.east && this.passes(r))
      .sort((a, b) => (a.lat - c.lat) ** 2 + (a.lon - c.lng) ** 2 - ((b.lat - c.lat) ** 2 + (b.lon - c.lng) ** 2))
    this.el.list.innerHTML = visible.length
      ? `<div class="list-head">${visible.length} ${visible.length === 1 ? L('ruta en pantalla', 'route on screen') : L('rutas en pantalla', 'routes on screen')}</div>` +
        visible.slice(0, 40).map((r) => {
          const kind = this.activity === 'hike' ? L('Sendero', 'Hiking') : r.isMtb ? 'MTB' : L('Ciclista', 'Cycling')
          const meta = [kind, SHAPE_LABEL[r.shape], r.ref].filter(Boolean).join(' · ')
          return `<button type="button" data-id="${r.id}"><i style="background:${KIND_COLOR[this.flavourOf(r)]}"></i><span><b>${escapeHtml(r.name)}</b><small>${escapeHtml(meta)}</small></span></button>`
        }).join('')
      : ''
    this.el.list.querySelectorAll<HTMLButtonElement>('button[data-id]').forEach((b) =>
      b.addEventListener('click', () => {
        const r = this.routes.get(Number(b.dataset.id))
        if (r) this.onPick(r)
      }))
  }
}
