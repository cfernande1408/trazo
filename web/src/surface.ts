// Dificultad y por dónde va la ruta según OpenStreetMap, como en la app
// (Difficulty.kt y Surface.kt del núcleo). Una sola consulta a Overpass vale
// para las dos: los caminos junto al track traen sus etiquetas.
import { downsample, haversine, type GeoPoint, type Segments } from './geo'
import { L } from './i18n'

export type SurfaceKind = 'paved' | 'track' | 'path' | 'steps'
export const SURFACE: Record<SurfaceKind, { label: string; color: string }> = {
  paved: { label: L('Asfalto', 'Paved'), color: '#8A8F98' },
  track: { label: L('Pista', 'Track'), color: '#C8963E' },
  path: { label: L('Sendero', 'Trail'), color: '#4E8A2E' },
  steps: { label: L('Escaleras', 'Steps'), color: '#B5532A' },
}
const KINDS: SurfaceKind[] = ['paved', 'track', 'path', 'steps']

export const MTB_LABELS: [string, string][] = [
  ['S0', L('pista o sendero fácil', 'easy track or trail')],
  ['S1', L('raíces y piedras pequeñas', 'roots and small rocks')],
  ['S2', L('escalones bajos y piedra suelta', 'low steps and loose rock')],
  ['S3', L('bloques, escalones y curvas cerradas', 'boulders, steps and tight switchbacks')],
  ['S4', L('muy técnico, pasos de trial', 'very technical, trials moves')],
  ['S5', L('extremo', 'extreme')],
  ['S6', L('no ciclable', 'not rideable')],
]
/** Índice 1–6 → T1–T6. */
export const SAC_LABELS: [string, string][] = [
  ['T1', L('senderismo', 'hiking')],
  ['T2', L('senderismo de montaña', 'mountain hiking')],
  ['T3', L('montaña exigente', 'demanding mountain hiking')],
  ['T4', L('alpino', 'alpine hiking')],
  ['T5', L('alpino exigente', 'demanding alpine hiking')],
  ['T6', L('alpino difícil', 'difficult alpine hiking')],
]
const SAC: Record<string, number> = {
  hiking: 1, mountain_hiking: 2, demanding_mountain_hiking: 3,
  alpine_hiking: 4, demanding_alpine_hiking: 5, difficult_alpine_hiking: 6,
}
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v))
export const gradeLabel = (grade: number, mtb: boolean): [string, string] =>
  mtb ? MTB_LABELS[clamp(grade, 0, 6)] : SAC_LABELS[clamp(grade - 1, 0, 5)]

const PAVED = new Set(['asphalt', 'concrete', 'concrete:plates', 'concrete:lanes', 'paved', 'paving_stones', 'sett', 'cobblestone', 'unhewn_cobblestone', 'metal', 'wood', 'chipseal', 'brick', 'bricks'])
const UNPAVED = new Set(['unpaved', 'compacted', 'fine_gravel', 'gravel', 'pebblestone', 'rock', 'dirt', 'earth', 'ground', 'mud', 'sand', 'grass', 'grass_paver', 'woodchips', 'snow', 'ice'])
const ROADS = new Set(['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'unclassified', 'residential', 'living_street', 'service', 'pedestrian', 'road', 'busway', 'motorway_link', 'trunk_link', 'primary_link', 'secondary_link', 'tertiary_link'])
const PATHS = new Set(['path', 'footway', 'bridleway', 'cycleway', 'via_ferrata'])

/** Qué es un camino según sus etiquetas: manda la superficie si la tiene; si no, el tipo de vía. */
export function classify(tags: Record<string, string>): SurfaceKind | undefined {
  const hw = tags.highway
  if (!hw) return undefined
  if (hw === 'steps') return 'steps'
  if (!ROADS.has(hw) && !PATHS.has(hw) && hw !== 'track') return undefined
  const surface = tags.surface?.split(';')[0]
  const paved = surface && PAVED.has(surface) ? true : surface && UNPAVED.has(surface) ? false : hw === 'track' ? tags.tracktype === 'grade1' : undefined
  if (paved === true) return 'paved'
  if (hw === 'track') return 'track'
  if (PATHS.has(hw)) {
    // Aceras, pasos de peatones y carriles bici sin dato van asfaltados casi siempre
    return paved == null && (tags.footway === 'sidewalk' || tags.footway === 'crossing' || hw === 'cycleway') ? 'paved' : 'path'
  }
  // Calle o carretera de tierra: a efectos de la salida, pista
  return paved === false ? 'track' : 'paved'
}

/** Consulta: todos los caminos en recuadros pequeños a lo largo del track. */
export function osmWaysQuery(segments: Segments, marginMeters = 25): string {
  const pts = downsample(segments.flat(), 400)
  const boxes: string[] = []
  for (let i = 0; i < pts.length; i += 4) {
    const chunk = pts.slice(i, i + 5)
    if (!chunk.length) continue
    const lats = chunk.map((p) => p.lat), lons = chunk.map((p) => p.lon)
    const mean = lats.reduce((a, b) => a + b, 0) / lats.length
    const dLat = marginMeters / 110_540
    const dLon = marginMeters / (111_320 * Math.cos((mean * Math.PI) / 180))
    boxes.push([Math.min(...lats) - dLat, Math.min(...lons) - dLon, Math.max(...lats) + dLat, Math.max(...lons) + dLon].map((v) => v.toFixed(5)).join(','))
  }
  return `[out:json][timeout:25];(${boxes.map((b) => `way[highway](${b});`).join('')});out tags geom;`
}

export interface OsmWay { points: GeoPoint[]; kind?: SurfaceKind; mtb?: number; sac?: number }

/** Los caminos de la respuesta, con su tipo y su dificultad si la tienen. */
export function parseWays(json: unknown): OsmWay[] {
  const els = ((json as { elements?: unknown[] })?.elements ?? [])
  return els.flatMap((e) => {
    const m = e as { type?: string; tags?: Record<string, string>; geometry?: { lat?: number; lon?: number }[] }
    if (m.type !== 'way') return []
    const points = (m.geometry ?? []).flatMap((g) => (typeof g?.lat === 'number' && typeof g?.lon === 'number' ? [{ lat: g.lat, lon: g.lon }] : []))
    if (points.length < 2) return []
    const tags = m.tags ?? {}
    // "2+" o "1-" se quedan en su número
    const d = parseInt(tags['mtb:scale']?.charAt(0) ?? '', 10)
    return [{ points, kind: classify(tags), mtb: Number.isNaN(d) ? undefined : clamp(d, 0, 6), sac: SAC[tags.sac_scale ?? ''] }]
  })
}

/** Distancia en metros de un punto a una polilínea (proyección plana local). */
export function distanceToLine(p: GeoPoint, line: GeoPoint[]): number {
  const k = Math.cos((p.lat * Math.PI) / 180)
  const x = (g: GeoPoint) => (g.lon - p.lon) * 111_320 * k
  const y = (g: GeoPoint) => (g.lat - p.lat) * 110_540
  let best = Infinity
  for (let i = 1; i < line.length; i++) {
    const ax = x(line[i - 1]), ay = y(line[i - 1]), bx = x(line[i]), by = y(line[i])
    const dx = bx - ax, dy = by - ay
    const len2 = dx * dx + dy * dy
    const t = len2 === 0 ? 0 : clamp(-(ax * dx + ay * dy) / len2, 0, 1)
    const d = Math.hypot(ax + t * dx, ay + t * dy)
    if (d < best) best = d
  }
  return best
}

const CELL = 0.002
const cellKey = (lat: number, lon: number) => `${Math.floor(lat / CELL)}:${Math.floor(lon / CELL)}`

/**
 * Recorre la ruta tramo a tramo y, para cada uno, busca el camino más cercano
 * (a menos de [maxMeters]) que cumpla [pick]. Los caminos van en una rejilla
 * de ~200 m para no medir contra todas las calles en cada tramo.
 */
function walk<T>(segments: Segments, ways: OsmWay[], pick: (w: OsmWay) => T | undefined, maxMeters: number): { total: number; by: Map<T, number> } {
  const grid = new Map<string, number[]>()
  ways.forEach((w, idx) => {
    if (pick(w) === undefined) return
    const cells = new Set(w.points.map((p) => cellKey(p.lat, p.lon)))
    for (let i = 1; i < w.points.length; i++) {
      const a = w.points[i - 1], b = w.points[i]
      const steps = Math.floor(haversine(a, b) / 100)
      for (let s = 1; s <= steps; s++) {
        const t = s / (steps + 1)
        cells.add(cellKey(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
      }
    }
    for (const c of cells) {
      if (!grid.has(c)) grid.set(c, [])
      grid.get(c)!.push(idx)
    }
  })
  const by = new Map<T, number>()
  let total = 0
  const seenAt = new Int32Array(ways.length).fill(-1)
  let step = 0
  for (const seg of segments) {
    for (let i = 1; i < seg.length; i++) {
      const a = seg[i - 1], b = seg[i]
      const d = haversine(a, b)
      total += d
      if (d === 0 || grid.size === 0) continue
      const mid = { lat: (a.lat + b.lat) / 2, lon: (a.lon + b.lon) / 2 }
      const cy = Math.floor(mid.lat / CELL), cx = Math.floor(mid.lon / CELL)
      let best: T | undefined
      let bestDist = maxMeters
      step++
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        for (const w of grid.get(`${cy + dy}:${cx + dx}`) ?? []) {
          if (seenAt[w] === step) continue
          seenAt[w] = step
          const dist = distanceToLine(mid, ways[w].points)
          if (dist <= bestDist) { bestDist = dist; best = pick(ways[w]) }
        }
      }
      if (best !== undefined) by.set(best, (by.get(best) ?? 0) + d)
    }
  }
  return { total, by }
}

export interface SurfaceSummary { routeMeters: number; meters: Partial<Record<SurfaceKind, number>>; knownMeters: number }

export function summarizeSurface(segments: Segments, ways: OsmWay[], maxMeters = 15): SurfaceSummary {
  const { total, by } = walk(segments, ways, (w) => w.kind, maxMeters)
  const meters: Partial<Record<SurfaceKind, number>> = {}
  for (const k of KINDS) if (by.has(k)) meters[k] = by.get(k)!
  return { routeMeters: total, meters, knownMeters: [...by.values()].reduce((a, b) => a + b, 0) }
}

export interface DifficultySummary { routeMeters: number; byGrade: [number, number][]; ratedMeters: number; mainGrade?: number; maxGrade?: number }

export function summarizeDifficulty(segments: Segments, ways: OsmWay[], mtb: boolean, maxMeters = 15): DifficultySummary {
  const { total, by } = walk(segments, ways, (w) => (mtb ? w.mtb : w.sac), maxMeters)
  const byGrade = [...by.entries()].sort((a, b) => a[0] - b[0])
  const main = byGrade.length ? byGrade.reduce((a, b) => (b[1] > a[1] ? b : a))[0] : undefined
  return {
    routeMeters: total, byGrade, ratedMeters: byGrade.reduce((a, [, m]) => a + m, 0),
    mainGrade: main, maxGrade: byGrade.length ? byGrade[byGrade.length - 1][0] : undefined,
  }
}
