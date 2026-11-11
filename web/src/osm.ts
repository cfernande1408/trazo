/**
 * Rutas de OpenStreetMap para Explorar, como en la app: Waymarked Trails
 * (rápido) y Overpass de respaldo, unión de tramos, forma circular o lineal y
 * búsqueda de lugares con Nominatim. Pasado de core/gpx/osm a TypeScript.
 */
import { haversine, pointAt, stats, type Activity, type GeoPoint, type Segments } from './geo'

export interface Bbox { south: number; west: number; north: number; east: number }

export type Shape = 'loop' | 'linear' | 'unknown'
export const SHAPE_LABEL: Record<Shape, string> = { loop: 'Circular', linear: 'Lineal', unknown: '' }

export interface OsmRoute {
  id: number
  name: string
  ref?: string
  network?: string
  lat: number
  lon: number
  /** true para rutas MTB; false para ciclistas o senderos. */
  isMtb: boolean
  shape: Shape
  description?: string
}

export const contains = (a: Bbox, b: Bbox) => b.south >= a.south && b.north <= a.north && b.west >= a.west && b.east <= a.east

export function expand(b: Bbox, fraction: number): Bbox {
  const dLat = (b.north - b.south) * fraction
  const dLon = (b.east - b.west) * fraction
  return { south: b.south - dLat, west: b.west - dLon, north: b.north + dLat, east: b.east + dLon }
}

// ---- Waymarked Trails (coordenadas Web Mercator, EPSG:3857) ----

export type Flavour = 'mtb' | 'cycling' | 'hiking'
export const flavoursFor = (a: Activity): Flavour[] => (a === 'hike' ? ['hiking'] : ['mtb', 'cycling'])

const R = 6_378_137
export const toMerc = (lat: number, lon: number): [number, number] =>
  [R * ((lon * Math.PI) / 180), R * Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360))]
export const fromMerc = (x: number, y: number): GeoPoint =>
  ({ lat: ((2 * Math.atan(Math.exp(y / R)) - Math.PI / 2) * 180) / Math.PI, lon: (x / R) * (180 / Math.PI) })

export function mercBbox(b: Bbox): string {
  const [x1, y1] = toMerc(b.south, b.west)
  const [x2, y2] = toMerc(b.north, b.east)
  // Sin "-0.0" (ln(tan(π/4)) no da 0 exacto en el ecuador)
  return [x1, y1, x2, y2].map((v) => (Math.abs(v) < 0.05 ? 0 : v).toFixed(1)).join(',')
}

const wmt = (f: Flavour) => `https://${f}.waymarkedtrails.org/api/v1`
export const wmtByArea = (f: Flavour, b: Bbox, limit = 100) => `${wmt(f)}/list/by_area?bbox=${mercBbox(b)}&limit=${limit}`
export const wmtSegments = (f: Flavour, merc: string, ids: number[]) => `${wmt(f)}/list/segments?bbox=${merc}&relations=${ids.join(',')}`
export const wmtDetails = (f: Flavour, id: number) => `${wmt(f)}/details/relation/${id}`
export const wmtSearch = (f: Flavour, q: string, limit = 15) => `${wmt(f)}/list/search?limit=${limit}&query=${encodeURIComponent(q.trim())}`

export interface Listed { id: number; name: string; ref?: string }

export function parseWmtList(json: unknown): Listed[] {
  const results = (json as { results?: unknown[] })?.results ?? []
  return results.flatMap((r) => {
    const m = r as { id?: number; name?: string; ref?: string }
    if (typeof m.id !== 'number') return []
    return [{ id: m.id, name: m.name ?? m.ref ?? `Ruta ${m.id}`, ref: m.ref ?? undefined }]
  })
}

/** Trazados por id de relación (líneas sueltas, sin ordenar). */
export function parseWmtSegments(json: unknown): Map<number, Segments> {
  const out = new Map<number, Segments>()
  for (const f of (json as { features?: unknown[] })?.features ?? []) {
    const feat = f as { id?: number; geometry?: { type: string; coordinates: unknown } }
    if (typeof feat.id !== 'number' || !feat.geometry) continue
    const g = feat.geometry
    const lines = (g.type === 'LineString' ? [g.coordinates] : g.type === 'MultiLineString' ? g.coordinates : []) as number[][][]
    for (const line of lines) {
      const pts = line.filter((c) => c.length >= 2).map(([x, y]) => fromMerc(x, y))
      if (pts.length >= 2) {
        if (!out.has(feat.id)) out.set(feat.id, [])
        out.get(feat.id)!.push(pts)
      }
    }
  }
  return out
}

/** Recuadro de la ruta entera (en Mercator, listo para [wmtSegments]) y su descripción. */
export function parseWmtDetails(json: unknown): { merc?: string; description?: string } {
  const m = json as { bbox?: number[]; description?: string; note?: string }
  return {
    merc: m?.bbox?.length === 4 ? m.bbox.map((v) => v.toFixed(1)).join(',') : undefined,
    description: m?.description ?? m?.note ?? undefined,
  }
}

export function mercCenter(merc: string): GeoPoint | undefined {
  const v = merc.split(',').map(Number)
  return v.length === 4 && v.every(Number.isFinite) ? fromMerc((v[0] + v[2]) / 2, (v[1] + v[3]) / 2) : undefined
}

/** Une lo listado con su trazado: el icono va a mitad del tramo más largo que se ve. */
export function summaries(f: Flavour, listed: Listed[], segs: Map<number, Segments>, area?: Bbox): OsmRoute[] {
  return listed.flatMap((l) => {
    const lines = segs.get(l.id)
    if (!lines?.length) return []
    const longest = lines.reduce((a, b) => (stats([b]).distanceMeters > stats([a]).distanceMeters ? b : a))
    const c = pointAt([longest], stats([longest]).distanceMeters / 2) ?? longest[0]
    return [{ id: l.id, name: l.name, ref: l.ref, lat: c.lat, lon: c.lon, isMtb: f === 'mtb', shape: area ? routeShape(lines, area) : 'unknown' }]
  })
}

// ---- Overpass ----

export const OVERPASS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.private.coffee/api/interpreter',
  'https://maps.mail.ru/osm/tools/overpass/api/interpreter',
]

const f5 = (v: number) => v.toFixed(5)
export const overpassArea = (b: Bbox, routes: string) =>
  `[out:json][timeout:20];relation["route"~"^(${routes})$"](${f5(b.south)},${f5(b.west)},${f5(b.north)},${f5(b.east)});out tags center qt 200;`
export const overpassRoute = (id: number) => `[out:json][timeout:60];relation(${id});out tags;way(r);out geom;`
export const osmUrl = (id: number) => `https://www.openstreetmap.org/relation/${id}`

type OsmTags = Record<string, string>
const nameOf = (tags: OsmTags, id: number) => tags['name:es'] ?? tags.name ?? tags.ref ?? `Ruta ${id}`
const shapeOfTag = (v?: string): Shape => (v === 'yes' ? 'loop' : v === 'no' ? 'linear' : 'unknown')

interface OsmElement { type: string; id: number; tags?: OsmTags; center?: { lat: number; lon: number }; geometry?: { lat: number; lon: number }[] }

export function parseOverpassArea(json: unknown): OsmRoute[] {
  return ((json as { elements?: OsmElement[] })?.elements ?? []).flatMap((e) => {
    if (e.type !== 'relation' || !e.center) return []
    const tags = e.tags ?? {}
    return [{
      id: e.id, name: nameOf(tags, e.id), ref: tags.ref, network: tags.network, lat: e.center.lat, lon: e.center.lon,
      isMtb: tags.route !== 'bicycle', shape: shapeOfTag(tags.roundtrip), description: tags.description,
    }]
  })
}

/** El trazado exacto de una relación: sus vías unidas por los extremos. */
export function parseOverpassRoute(json: unknown, id: number): { name: string; description?: string; segments: Segments } {
  const els = (json as { elements?: OsmElement[] })?.elements ?? []
  const tags = els.find((e) => e.type === 'relation')?.tags ?? {}
  const ways = els.filter((e) => e.type === 'way' && e.geometry).map((w) => w.geometry!.map((p) => ({ lat: p.lat, lon: p.lon })))
  const segments = stitch(ways)
  if (!segments.length) throw new Error('La ruta no tiene trazado en OpenStreetMap')
  return { name: nameOf(tags, id), description: tags.description, segments }
}

// ---- Unir tramos y forma ----

/** Encadena vías sin orden por sus extremos, dándoles la vuelta si hace falta (WayStitcher). */
export function stitch(ways: Segments, tolerance = 30): Segments {
  const pending = ways.filter((w) => w.length >= 2).map((w) => [...w])
  const near = (a: GeoPoint, b: GeoPoint) => haversine(a, b) <= tolerance
  const out: Segments = []
  while (pending.length) {
    const chain = pending.shift()!
    let grew = true
    while (grew) {
      grew = false
      for (let i = 0; i < pending.length; i++) {
        const w = pending[i]
        const first = chain[0], last = chain[chain.length - 1]
        if (near(last, w[0])) chain.push(...w.slice(1))
        else if (near(last, w[w.length - 1])) chain.push(...[...w].reverse().slice(1))
        else if (near(first, w[w.length - 1])) chain.unshift(...w.slice(0, -1))
        else if (near(first, w[0])) chain.unshift(...[...w].reverse().slice(0, -1))
        else continue
        pending.splice(i--, 1)
        grew = true
      }
    }
    out.push(chain)
  }
  return out.sort((a, b) => b.length - a.length)
}

/** Distancia de un punto a una polilínea, en metros (aproximación plana local). */
function distanceToLine(p: GeoPoint, line: GeoPoint[]): number {
  const k = 111_195
  const kx = k * Math.cos((p.lat * Math.PI) / 180)
  let best = Infinity
  for (let i = 1; i < line.length; i++) {
    const ax = (line[i - 1].lon - p.lon) * kx, ay = (line[i - 1].lat - p.lat) * k
    const bx = (line[i].lon - p.lon) * kx, by = (line[i].lat - p.lat) * k
    const dx = bx - ax, dy = by - ay
    const len = dx * dx + dy * dy
    const t = len === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len))
    best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy))
  }
  return line.length === 1 ? haversine(p, line[0]) : best
}

/**
 * Circular o lineal por el trazado (RouteShapes): sin finales sueltos o con
 * uno (tramo de acceso), circular; con dos o más, lineal; si un final cae en
 * el borde de la zona cargada, la ruta sigue fuera y no se sabe.
 */
export function routeShape(lines: Segments, area?: Bbox, join = 60, border = 150): Shape {
  const ls = lines.filter((l) => l.length >= 2)
  if (!ls.length) return 'unknown'
  const free: GeoPoint[] = []
  ls.forEach((line, i) => {
    const length = stats([line]).distanceMeters
    for (const [end, other] of [[line[0], line[line.length - 1]], [line[line.length - 1], line[0]]]) {
      const closes = length > 3 * join && haversine(end, other) <= join
      const touches = closes || ls.some((l, j) => j !== i && distanceToLine(end, l) <= join)
      if (!touches) free.push(end)
    }
  })
  if (area) {
    const nearBorder = (p: GeoPoint) => {
      const mLat = 111_195, mLon = 111_195 * Math.cos((p.lat * Math.PI) / 180)
      return (p.lat - area.south) * mLat < border || (area.north - p.lat) * mLat < border ||
        (p.lon - area.west) * mLon < border || (area.east - p.lon) * mLon < border
    }
    if (free.some(nearBorder)) return 'unknown'
  }
  return free.length <= 1 ? 'loop' : 'linear'
}

// ---- Lugares (Nominatim) ----

export interface Place { name: string; detail: string; lat: number; lon: number; bbox?: Bbox; kind?: string }

/** Nominatim pide una consulta por segundo como mucho y nada de autocompletar: se busca al pulsar Intro. */
export const placeUrl = (q: string, limit = 8) =>
  `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=${limit}&accept-language=es&q=${encodeURIComponent(q.trim())}`

export function parsePlaces(json: unknown): Place[] {
  return ((json as unknown[]) ?? []).flatMap((r) => {
    const m = r as { lat?: string; lon?: string; display_name?: string; name?: string; boundingbox?: string[]; type?: string }
    const lat = Number(m.lat), lon = Number(m.lon)
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return []
    const display = m.display_name ?? ''
    const bb = m.boundingbox?.map(Number)
    return [{
      name: m.name?.trim() || display.split(',')[0],
      // "Bola del Mundo, Navacerrada, Madrid, España" → "Navacerrada, Madrid, España"
      detail: display.split(', ').slice(1, 4).join(', '),
      lat, lon,
      bbox: bb?.length === 4 && bb.every(Number.isFinite) ? { south: bb[0], north: bb[1], west: bb[2], east: bb[3] } : undefined,
      kind: m.type,
    }]
  })
}

/** Zoom para ver las rutas de alrededor de un lugar, sin bajar del mínimo de Explorar. */
export function zoomFor(p: Place, minZoom = MIN_ZOOM): number {
  if (!p.bbox) return 13
  const span = Math.max(p.bbox.north - p.bbox.south, p.bbox.east - p.bbox.west)
  return span < 0.02 ? 13 : span < 0.08 ? 12.5 : span < 0.3 ? 11.5 : minZoom + 1
}

const fold = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()

/**
 * Si una ruta encontrada por nombre tiene de verdad lo que se busca: todas las
 * palabras, sin mirar tildes ni mayúsculas, en su nombre o su referencia.
 * Waymarked Trails busca por parecido y devuelve "Cerf" para "Cercedilla".
 */
export function matchesQuery(name: string, ref: string | undefined, q: string): boolean {
  const hay = fold(`${name} ${ref ?? ''}`).replace(/[^a-z0-9ñ]+/g, ' ')
  const words = fold(q).split(/[^a-z0-9ñ]+/).filter((w) => w.length >= 2)
  return words.length > 0 && words.every((w) => hay.includes(w))
}

/** Más alejado que esto la zona es enorme y la consulta tardaría demasiado. */
export const MIN_ZOOM = 10
