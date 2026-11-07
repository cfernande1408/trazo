/**
 * Cálculos de la ruta: los mismos que el núcleo de la app Android
 * (core/gpx), pasados a TypeScript para la web. Si cambian allí, cambiarlos aquí.
 */

import type { Feature, FeatureCollection } from 'geojson'

export interface GeoPoint {
  lat: number
  lon: number
  ele?: number
}

export interface Waypoint extends GeoPoint {
  name?: string
  desc?: string
}

export type Segments = GeoPoint[][]

export interface ProfilePoint {
  distanceMeters: number
  ele: number
}

export interface TrackStats {
  distanceMeters: number
  gainMeters: number
  lossMeters: number
  minEle?: number
  maxEle?: number
  points: number
}

export type Activity = 'bike' | 'hike'

const EARTH_RADIUS_M = 6_371_008.8
const rad = (d: number) => (d * Math.PI) / 180

export function haversine(a: GeoPoint, b: GeoPoint): number {
  const dLat = rad(b.lat - a.lat)
  const dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * Distancia dentro de cada segmento (no se une el hueco entre segmentos) y
 * desnivel con histéresis: solo cuenta un cambio que supera [threshold]
 * respecto al último valor aceptado, para no sumar el ruido del GPS.
 */
export function stats(segments: Segments, threshold = 3): TrackStats {
  let distance = 0, gain = 0, loss = 0, points = 0
  let minEle: number | undefined, maxEle: number | undefined, ref: number | undefined
  for (const seg of segments) {
    seg.forEach((p, i) => {
      points++
      if (i > 0) distance += haversine(seg[i - 1], p)
      if (p.ele == null) return
      minEle = Math.min(minEle ?? p.ele, p.ele)
      maxEle = Math.max(maxEle ?? p.ele, p.ele)
      if (ref == null) ref = p.ele
      else if (p.ele - ref >= threshold) { gain += p.ele - ref; ref = p.ele }
      else if (ref - p.ele >= threshold) { loss += ref - p.ele; ref = p.ele }
    })
  }
  return { distanceMeters: distance, gainMeters: gain, lossMeters: loss, minEle, maxEle, points }
}

/** Altitud frente a distancia acumulada; los puntos sin altitud solo suman distancia. */
export function profile(segments: Segments): ProfilePoint[] {
  const out: ProfilePoint[] = []
  let dist = 0
  for (const seg of segments) {
    seg.forEach((p, i) => {
      if (i > 0) dist += haversine(seg[i - 1], p)
      if (p.ele != null) out.push({ distanceMeters: dist, ele: p.ele })
    })
  }
  return out
}

/** Altitud interpolada a [meters] del inicio. */
export function eleAt(prof: ProfilePoint[], meters: number): number | undefined {
  if (prof.length === 0) return undefined
  if (meters <= prof[0].distanceMeters) return prof[0].ele
  const last = prof[prof.length - 1]
  if (meters >= last.distanceMeters) return last.ele
  let lo = 0, hi = prof.length - 1
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1
    if (prof[mid].distanceMeters <= meters) lo = mid
    else hi = mid
  }
  const a = prof[lo], b = prof[hi]
  const span = b.distanceMeters - a.distanceMeters
  return span <= 0 ? a.ele : a.ele + ((b.ele - a.ele) * (meters - a.distanceMeters)) / span
}

/** El punto de la ruta a [meters] del inicio (para marcarlo en el mapa al recorrer el perfil). */
export function pointAt(segments: Segments, meters: number): GeoPoint | undefined {
  let acc = 0
  let last: GeoPoint | undefined
  for (const seg of segments) {
    for (let i = 1; i < seg.length; i++) {
      const d = haversine(seg[i - 1], seg[i])
      if (acc + d >= meters) {
        const t = d === 0 ? 0 : (meters - acc) / d
        return { lat: seg[i - 1].lat + (seg[i].lat - seg[i - 1].lat) * t, lon: seg[i - 1].lon + (seg[i].lon - seg[i - 1].lon) * t }
      }
      acc += d
      last = seg[i]
    }
  }
  return last ?? segments[0]?.[0]
}

// ---- Pendiente, por tramos de ~100 m (como Slope.kt) ----

export interface Grade {
  startMeters: number
  endMeters: number
  percent: number
}

export const SLOPE_COLORS = ['#2E7D32', '#9E9D24', '#F9A825', '#EF6C00', '#B71C1C']
export const SLOPE_LABELS = ['< 5 %', '5–10 %', '10–15 %', '15–20 %', '> 20 %']

export function bucket(percent: number): number {
  const p = Math.abs(percent)
  return p < 5 ? 0 : p < 10 ? 1 : p < 15 ? 2 : p < 20 ? 3 : 4
}

export function grades(prof: ProfilePoint[], windowMeters = 100): Grade[] {
  if (prof.length < 2) return []
  const out: Grade[] = []
  let i = 0
  while (i < prof.length - 1) {
    let j = i + 1
    while (j < prof.length - 1 && prof[j].distanceMeters - prof[i].distanceMeters < windowMeters) j++
    const d = prof[j].distanceMeters - prof[i].distanceMeters
    if (d > 0) out.push({ startMeters: prof[i].distanceMeters, endMeters: prof[j].distanceMeters, percent: ((prof[j].ele - prof[i].ele) / d) * 100 })
    i = j
  }
  // Un último trozo muy corto da pendientes absurdas: se une al anterior
  if (out.length >= 2) {
    const b = out[out.length - 1]
    if (b.endMeters - b.startMeters < windowMeters / 3) {
      const a = out[out.length - 2]
      out.pop()
      const eleA = (a.percent / 100) * (a.endMeters - a.startMeters)
      const eleB = (b.percent / 100) * (b.endMeters - b.startMeters)
      out[out.length - 1] = { startMeters: a.startMeters, endMeters: b.endMeters, percent: ((eleA + eleB) / (b.endMeters - a.startMeters)) * 100 }
    }
  }
  return out
}

export interface SlopeSummary {
  maxUpPercent: number
  maxUpAtMeters: number
  maxDownPercent: number
  upOver10Meters: number
  upOver15Meters: number
}

export function slopeSummary(gs: Grade[]): SlopeSummary | undefined {
  if (gs.length === 0) return undefined
  const up = gs.reduce((m, g) => (g.percent > m.percent ? g : m))
  const over = (t: number) => gs.filter((g) => g.percent >= t).reduce((s, g) => s + g.endMeters - g.startMeters, 0)
  return {
    maxUpPercent: Math.max(0, up.percent),
    maxUpAtMeters: up.startMeters,
    maxDownPercent: Math.min(0, Math.min(...gs.map((g) => g.percent))),
    upOver10Meters: over(10),
    upOver15Meters: over(15),
  }
}

/** Pendiente en [meters] del inicio (la del tramo que lo contiene). */
export function gradeAt(gs: Grade[], meters: number): number | undefined {
  if (gs.length === 0) return undefined
  let lo = 0, hi = gs.length - 1
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1
    if (gs[mid].startMeters <= meters) lo = mid
    else hi = mid - 1
  }
  return gs[lo].percent
}

/** La ruta en trozos del mismo color de pendiente, con la propiedad "b" (0–4). */
export function slopeLines(segments: Segments, gs: Grade[]): FeatureCollection {
  const features: Feature[] = []
  const feature = (pts: GeoPoint[], b: number): Feature => ({
    type: 'Feature',
    properties: { b },
    geometry: { type: 'LineString', coordinates: pts.map((p) => [p.lon, p.lat]) },
  })
  let dist = 0
  for (const seg of segments) {
    if (seg.length < 2) continue
    let run: GeoPoint[] = [seg[0]]
    let runBucket = -1
    for (let i = 1; i < seg.length; i++) {
      const step = haversine(seg[i - 1], seg[i])
      const b = bucket(gradeAt(gs, dist + step / 2) ?? 0)
      dist += step
      if (runBucket !== -1 && b !== runBucket) {
        features.push(feature(run, runBucket))
        run = [seg[i - 1]]
      }
      runBucket = b
      run.push(seg[i])
    }
    if (run.length >= 2) features.push(feature(run, Math.max(0, runBucket)))
  }
  return { type: 'FeatureCollection', features }
}

// ---- Otros datos del resumen ----

/** Si el final queda a menos de 200 m del principio, es circular (como RouteStart.isLoop). */
export function isLoop(segments: Segments): boolean {
  const a = segments[0]?.[0]
  const last = segments[segments.length - 1]
  const b = last?.[last.length - 1]
  const n = segments.reduce((s, seg) => s + seg.length, 0)
  return !!a && !!b && n > 2 && haversine(a, b) <= 200
}

/** Tiempo estimado: método MIDE a pie y una media de bici de montaña (Activity.estimateMs). */
export function estimateMs(activity: Activity, distanceMeters: number, gainMeters: number, lossMeters: number): number {
  let hours: number
  if (activity === 'hike') {
    const h = distanceMeters / 5_000
    const v = gainMeters / 400 + lossMeters / 600
    hours = Math.max(h, v) + Math.min(h, v) / 2
  } else {
    hours = distanceMeters / 14_000 + gainMeters / 600
  }
  return Math.round(hours * 3_600_000)
}

/** Reduce a unos [max] puntos, conservando el primero y el último. */
export function downsample<T>(pts: T[], max: number): T[] {
  if (pts.length <= max) return pts
  const step = (pts.length - 1) / (max - 1)
  return Array.from({ length: max }, (_, i) => pts[Math.round(i * step)])
}

export function bounds(segments: Segments): [number, number, number, number] | undefined {
  const pts = segments.flat()
  if (pts.length === 0) return undefined
  let w = Infinity, s = Infinity, e = -Infinity, n = -Infinity
  for (const p of pts) {
    w = Math.min(w, p.lon); e = Math.max(e, p.lon)
    s = Math.min(s, p.lat); n = Math.max(n, p.lat)
  }
  return [w, s, e, n]
}
