import type { Map as MlMap, StyleSpecification } from 'maplibre-gl'
// El mismo estilo que la app Android: una sola fuente para los dos
import trazoTopo from '../../app/src/main/assets/trazo_topo.json'
import type { Season } from './season'
import { L } from './i18n'

export type BaseMap = 'trazo' | 'ign' | 'pnoa'

const wmts = (service: string, layer: string) =>
  `https://www.ign.es/wmts/${service}?layer=${layer}&style=default&tilematrixset=GoogleMapsCompatible` +
  '&Service=WMTS&Request=GetTile&Version=1.0.0&Format=image/jpeg&TileMatrix={z}&TileCol={x}&TileRow={y}'

export const BASE_MAPS: Record<BaseMap, { label: string; note?: string; tiles?: string; attribution?: string; maxzoom?: number }> = {
  trazo: { label: 'Trazo' },
  ign: { label: 'Topo IGN', note: L('Solo España', 'Spain only'), tiles: wmts('mapa-raster', 'MTN'), attribution: L('Mapa topográfico © Instituto Geográfico Nacional', 'Topographic map © Instituto Geográfico Nacional'), maxzoom: 17 },
  pnoa: { label: L('Satélite', 'Satellite'), note: L('Solo España', 'Spain only'), tiles: wmts('pnoa-ma', 'OI.OrthoimageCoverage'), attribution: L('Ortofoto PNOA © Instituto Geográfico Nacional', 'PNOA orthophoto © Instituto Geográfico Nacional'), maxzoom: 19 },
}

/** El estilo topo de Trazo con los colores de la estación (como TrazoMapStyle.styleFile). */
export function trazoStyle(season: Season): StyleSpecification {
  const style = structuredClone(trazoTopo) as unknown as StyleSpecification
  for (const l of style.layers) {
    const paint = (l as { paint?: Record<string, unknown> }).paint
    if (!paint) continue
    if (l.id === 'background') paint['background-color'] = season.background
    if (l.id === 'landcover-grass') paint['fill-color'] = season.grass
    if (l.id === 'landcover-wood') paint['fill-color'] = season.wood
    if (l.id === 'park') paint['fill-color'] = season.park
  }
  // Créditos del mapa base (en la app salen en el botón (i) de cada mapa)
  const omt = style.sources.omt as { attribution?: string } | undefined
  if (omt && !omt.attribution) {
    omt.attribution = '<a href="https://openfreemap.org" target="_blank">OpenFreeMap</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a>'
  }
  // El estilo es el de la app, con los créditos en español
  for (const src of Object.values(style.sources) as { attribution?: string }[]) {
    if (src.attribution) src.attribution = src.attribution.replace(/^Relieve:/, L('Relieve:', 'Relief:'))
  }
  return style
}

/**
 * Tipo de mapa: con IGN o satélite, sus teselas van debajo de los caminos y
 * nombres de Trazo, como en la app. [relief] enciende el sombreado.
 */
export function applyBase(map: MlMap, base: BaseMap, relief: boolean) {
  const raster = BASE_MAPS[base]
  if (map.getLayer('base-raster')) map.removeLayer('base-raster')
  if (map.getSource('base-raster')) map.removeSource('base-raster')
  if (raster.tiles) {
    map.addSource('base-raster', { type: 'raster', tiles: [raster.tiles], tileSize: 256, maxzoom: raster.maxzoom, attribution: raster.attribution })
    // Encima de los rellenos del mapa de Trazo y debajo de caminos y nombres
    const before = map.getLayer('waterway') ? 'waterway' : undefined
    map.addLayer({ id: 'base-raster', type: 'raster', source: 'base-raster' }, before)
  }
  const ill = base === 'trazo'
  for (const id of ['landcover-wood-pines']) if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', ill ? 'visible' : 'none')
  if (map.getLayer('hillshade')) map.setLayoutProperty('hillshade', 'visibility', relief && base !== 'pnoa' ? 'visible' : 'none')
}

/** Los árboles del bosque, dibujados como en la app (TrazoMapStyle.treeTile). */
export function treeImage(season: Season, ratio: number): ImageData {
  const s = Math.max(24, Math.round(68 * ratio))
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = s
  const c = canvas.getContext('2d')!
  pine(c, season, s * 0.22, s * 0.38, s * 0.25)
  if (season.broadleafShare > 0) broadleaf(c, season, s * 0.72, s * 0.62, s * 0.22)
  else pine(c, season, s * 0.72, s * 0.62, s * 0.23)
  pine(c, season, s * 0.4, s * 0.95, s * 0.23)
  return c.getImageData(0, 0, s, s)
}

function pine(c: CanvasRenderingContext2D, season: Season, cx: number, baseY: number, h: number) {
  const k = h / 44
  const x = (v: number) => cx + v * k
  const y = (v: number) => baseY - 4 * k + v * k
  const poly = (color: string, pts: [number, number][]) => {
    c.fillStyle = color
    c.beginPath()
    pts.forEach(([px, py], i) => (i ? c.lineTo(x(px), y(py)) : c.moveTo(x(px), y(py))))
    c.closePath()
    c.fill()
  }
  c.fillStyle = '#7A5C3E'
  c.fillRect(x(-1.5), y(-6), 3 * k, 10 * k)
  poly(season.pineLight, [[0, -40], [9, -25], [5, -25], [12, -8], [-12, -8], [-5, -25], [-9, -25]])
  poly(season.pineShade, [[0, -40], [9, -25], [5, -25], [12, -8], [0, -8]])
  if (season.snow) {
    poly('#FFFFFF', [[0, -40], [4.5, -32.5], [-4.5, -32.5]])
    poly('#FFFFFF', [[-9, -25], [9, -25], [7.5, -22.5], [-7.5, -22.5]])
    poly('#FFFFFF', [[-12, -8], [12, -8], [10.5, -5.5], [-10.5, -5.5]])
  }
}

function broadleaf(c: CanvasRenderingContext2D, season: Season, cx: number, baseY: number, h: number) {
  const k = h / 44
  const x = (v: number) => cx + v * k
  const y = (v: number) => baseY - 4 * k + v * k
  const circle = (color: string, px: number, py: number, r: number) => {
    c.fillStyle = color
    c.beginPath()
    c.arc(x(px), y(py), r * k, 0, Math.PI * 2)
    c.fill()
  }
  c.fillStyle = '#7A5C3E'
  c.fillRect(x(-1.8), y(-12), 3.6 * k, 16 * k)
  circle(season.broadleaf, 0, -24, 12)
  circle(season.broadleaf, -6, -17, 8)
  circle(season.broadleafShade, 6, -17, 8)
  circle(season.broadleafShade, 4, -27, 8)
  if (season.blossom) {
    for (const [dx, dy] of [[-6, -27], [2, -32], [7, -21], [-3, -18], [-8, -21]]) circle(season.blossom, dx, dy, 1.8)
  }
}
