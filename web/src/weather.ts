// Previsión a lo largo de la ruta con Open-Meteo, como en la app
// (RouteWeather.kt y WeatherDetail.kt del núcleo)
import { estimateMs, haversine, pointAt, stats, type Activity, type GeoPoint, type Segments } from './geo'

/** Un punto de la ruta y cuándo llegarás a él desde la salida. */
export interface RouteSample { distanceMeters: number; lat: number; lon: number; ele?: number; offsetMs: number }

/** El tiempo previsto en un punto de la ruta a la hora en que pasarás por él. */
export interface WeatherAt {
  sample: RouteSample
  timeMs: number
  tempC: number
  precipProbability?: number
  precipMm: number
  code: number
  windKmh: number
  gustKmh: number
}

/** El tiempo en un momento de la salida, donde estarás entonces. */
export interface DetailRow {
  timeMs: number
  distanceMeters: number
  tempC: number
  /** Sensación térmica: con el viento y la humedad. */
  feelsC: number
  windKmh: number
  gustKmh: number
  precipMm: number
  precipProbability?: number
  cloudPct?: number
  uv?: number
  isDay: boolean
  code: number
}

export const FORECAST_DAYS = 7
export const STEP_MIN = 15
const HOUR = 3_600_000

/** La ruta desde el principio hasta [meters]. */
function upTo(segments: Segments, meters: number): Segments {
  const out: Segments = []
  let acc = 0
  for (const seg of segments) {
    const cur: GeoPoint[] = []
    let over = false
    for (let i = 0; i < seg.length; i++) {
      if (i > 0) acc += haversine(seg[i - 1], seg[i])
      if (acc > meters) { over = true; break }
      cur.push(seg[i])
    }
    if (cur.length) out.push(cur)
    if (over) break
  }
  return out
}

function nearestEle(segments: Segments, p: GeoPoint): number | undefined {
  let best: GeoPoint | undefined
  let bd = Infinity
  for (const q of segments.flat()) {
    if (q.ele == null) continue
    const d = Math.abs(q.lat - p.lat) + Math.abs(q.lon - p.lon)
    if (d < bd) { bd = d; best = q }
  }
  return best?.ele
}

/** Puntos repartidos por la ruta: uno cada ~5 km, entre 2 y 8, con la hora a la que pasarás. */
export function samples(segments: Segments, activity: Activity, count?: number): RouteSample[] {
  const total = stats(segments).distanceMeters
  if (total <= 0) return []
  const n = count ?? Math.min(8, Math.max(2, Math.round(total / 5000) + 1))
  return Array.from({ length: n }, (_, k) => {
    const d = (total * k) / (n - 1)
    const part = stats(upTo(segments, d))
    const p = pointAt(segments, d) ?? segments[0][0]
    return {
      distanceMeters: d, lat: p.lat, lon: p.lon, ele: p.ele ?? nearestEle(segments, p),
      offsetMs: k === 0 ? 0 : estimateMs(activity, part.distanceMeters, part.gainMeters, part.lossMeters),
    }
  })
}

export function forecastUrl(ss: RouteSample[]): string {
  const list = (f: (s: RouteSample) => number) => ss.map((s) => f(s).toFixed(4)).join(',')
  const ele = ss.every((s) => s.ele != null) ? '&elevation=' + ss.map((s) => s.ele!.toFixed(0)).join(',') : ''
  return `https://api.open-meteo.com/v1/forecast?latitude=${list((s) => s.lat)}&longitude=${list((s) => s.lon)}${ele}` +
    '&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,' +
    'wind_speed_10m,wind_gusts_10m,cloud_cover,uv_index,is_day' +
    `&timeformat=unixtime&forecast_days=${FORECAST_DAYS}`
}

type Hourly = Record<string, (number | null)[]>

/** Las series horarias de cada punto (Open-Meteo devuelve una lista con varios puntos o un objeto con uno). */
function locations(json: unknown): { times: number[]; values: Hourly }[] {
  const arr = Array.isArray(json) ? json : json && typeof json === 'object' ? [json] : []
  return arr.map((loc) => {
    const h = ((loc as { hourly?: Record<string, unknown> })?.hourly ?? {}) as Record<string, unknown>
    const times = ((h.time as number[] | undefined) ?? []).map((t) => t * 1000)
    const values: Hourly = {}
    for (const [k, v] of Object.entries(h)) if (k !== 'time' && Array.isArray(v)) values[k] = v as (number | null)[]
    return { times, values }
  })
}

/** Para cada punto, la hora de la previsión más cercana a cuando pasarás por él. */
export function parse(json: unknown, ss: RouteSample[], startMs: number): WeatherAt[] {
  const locs = locations(json)
  return ss.flatMap((s, k) => {
    const loc = locs[k]
    if (!loc || !loc.times.length) return []
    const at = startMs + s.offsetMs
    if (at < loc.times[0] - HOUR || at > loc.times[loc.times.length - 1] + HOUR) return []
    let i = 0
    for (let j = 1; j < loc.times.length; j++) if (Math.abs(loc.times[j] - at) < Math.abs(loc.times[i] - at)) i = j
    const num = (key: string) => loc.values[key]?.[i] ?? undefined
    const temp = num('temperature_2m')
    if (temp == null) return []
    const pp = num('precipitation_probability')
    return [{
      sample: s, timeMs: loc.times[i], tempC: temp,
      precipProbability: pp == null ? undefined : Math.round(pp),
      precipMm: num('precipitation') ?? 0, code: Math.round(num('weather_code') ?? 0),
      windKmh: num('wind_speed_10m') ?? 0, gustKmh: num('wind_gusts_10m') ?? 0,
    }]
  })
}

/** Código WMO del tiempo → texto e icono. */
export function describe(code: number): [string, string] {
  if (code === 0) return ['Despejado', '☀️']
  if (code === 1 || code === 2) return ['Poco nuboso', '🌤️']
  if (code === 3) return ['Nublado', '☁️']
  if (code === 45 || code === 48) return ['Niebla', '🌫️']
  if (code >= 51 && code <= 57) return ['Llovizna', '🌦️']
  if (code >= 61 && code <= 67) return ['Lluvia', '🌧️']
  if (code >= 71 && code <= 77) return ['Nieve', '🌨️']
  if (code >= 80 && code <= 82) return ['Chubascos', '🌦️']
  if (code === 85 || code === 86) return ['Chubascos de nieve', '🌨️']
  if (code >= 95 && code <= 99) return ['Tormenta', '⛈️']
  return ['—', '🌡️']
}

const kmOf = (meters: number) => `km ${Math.round(meters / 1000)}`

/** Avisos para la salida: tormenta, nieve, hielo, viento, lluvia o calor, con el km donde empieza. */
export function warnings(points: WeatherAt[]): string[] {
  const out: string[] = []
  const first = (f: (w: WeatherAt) => boolean) => points.find(f)
  const km = (w: WeatherAt) => kmOf(w.sample.distanceMeters)
  let w = first((p) => p.code >= 95 && p.code <= 99)
  if (w) out.push(`⛈️ Tormenta prevista desde el ${km(w)}. Mejor otro día.`)
  w = first((p) => (p.code >= 71 && p.code <= 77) || p.code === 85 || p.code === 86)
  if (w) out.push(`🌨️ Nieve desde el ${km(w)}.`)
  w = first((p) => p.tempC <= 0)
  if (w) out.push(`🧊 Bajo cero en el ${km(w)}: puede haber hielo.`)
  w = first((p) => p.gustKmh >= 50)
  if (w) out.push(`💨 Rachas de ${Math.round(w.gustKmh)} km/h en el ${km(w)}. Cuidado en crestas y collados.`)
  w = first((p) => (p.precipProbability ?? 0) >= 60 && !(p.code >= 95 && p.code <= 99))
  if (w) out.push(`🌧️ Lluvia probable (${w.precipProbability} %) desde el ${km(w)}.`)
  w = first((p) => p.tempC >= 32)
  if (w) out.push(`🥵 ${Math.round(w.tempC)} °C en el ${km(w)}: lleva agua de sobra.`)
  return out
}

/**
 * Una fila cada [stepMinutes] desde la salida hasta el final: dónde estarás
 * y el tiempo que hará ahí. Entre horas se interpola en el tiempo y entre dos
 * puntos de la ruta se mezclan según lo cerca que estés de cada uno.
 */
export function detailRows(json: unknown, ss: RouteSample[], startMs: number, stepMinutes = STEP_MIN): DetailRow[] {
  const locs = locations(json)
  if (!ss.length || locs.length < ss.length) return []
  const covers = (l: (typeof locs)[number], at: number) => l.times.length > 0 && at >= l.times[0] && at <= l.times[l.times.length - 1]
  const interp = (l: (typeof locs)[number], key: string, at: number): number | undefined => {
    const v = l.values[key]
    if (!v || !covers(l, at)) return undefined
    let i = 0
    for (let j = 0; j < l.times.length; j++) if (l.times[j] <= at) i = j
    if (i === l.times.length - 1) i--
    if (i < 0) return v[0] ?? undefined
    const a = v[i], b = v[i + 1]
    if (a == null) return b ?? undefined
    if (b == null) return a
    return a + ((b - a) * (at - l.times[i])) / (l.times[i + 1] - l.times[i])
  }
  const nearest = (l: (typeof locs)[number], key: string, at: number): number | undefined => {
    const v = l.values[key]
    if (!v || !l.times.length) return undefined
    let i = 0
    for (let j = 1; j < l.times.length; j++) if (Math.abs(l.times[j] - at) < Math.abs(l.times[i] - at)) i = j
    return v[i] ?? undefined
  }
  const total = ss[ss.length - 1].offsetMs
  const step = stepMinutes * 60_000
  const offsets: number[] = []
  for (let t = 0; t <= total; t += step) offsets.push(t)
  if (offsets[offsets.length - 1] !== total) offsets.push(total)
  return offsets.flatMap((t) => {
    let k = 0
    for (let j = 0; j < ss.length; j++) if (ss[j].offsetMs <= t) k = j
    const k2 = Math.min(k + 1, ss.length - 1)
    const span = ss[k2].offsetMs - ss[k].offsetMs
    const f = span <= 0 ? 0 : Math.min(1, Math.max(0, (t - ss[k].offsetMs) / span))
    const at = startMs + t
    const a = locs[k], b = locs[k2]
    if (!covers(a, at) || !covers(b, at)) return []
    const mix = (key: string) => {
      const va = interp(a, key, at), vb = interp(b, key, at)
      return va == null ? vb : vb == null ? va : va + (vb - va) * f
    }
    const near = f < 0.5 ? a : b
    const temp = mix('temperature_2m')
    if (temp == null) return []
    const pp = mix('precipitation_probability'), cl = mix('cloud_cover')
    return [{
      timeMs: at,
      distanceMeters: ss[k].distanceMeters + (ss[k2].distanceMeters - ss[k].distanceMeters) * f,
      tempC: temp,
      feelsC: mix('apparent_temperature') ?? temp,
      windKmh: mix('wind_speed_10m') ?? 0,
      gustKmh: mix('wind_gusts_10m') ?? 0,
      precipMm: mix('precipitation') ?? 0,
      precipProbability: pp == null ? undefined : Math.round(pp),
      cloudPct: cl == null ? undefined : Math.round(cl),
      uv: mix('uv_index'),
      isDay: (nearest(near, 'is_day', at) ?? 1) >= 0.5,
      code: Math.round(nearest(near, 'weather_code', at) ?? 0),
    }]
  })
}

/**
 * Qué llevarte puesto, por capas: según la sensación más fría y más cálida
 * de la salida, la lluvia, el viento, el sol y si se hace de noche. En bici
 * se cuenta con unos grados menos por el aire de la marcha.
 */
export function whatToWear(rows: DetailRow[], activity: Activity): string[] {
  if (!rows.length) return []
  const bike = activity === 'bike'
  const chill = bike ? 4 : 0
  const coldest = Math.min(...rows.map((r) => r.feelsC)) - chill
  const warmest = Math.max(...rows.map((r) => r.feelsC)) - chill / 2
  const out: string[] = []
  const km = (r: DetailRow) => kmOf(r.distanceMeters)
  out.push(
    coldest >= 20 ? 'Arriba: manga corta transpirable.'
      : coldest >= 14 ? (bike ? 'Arriba: manga corta, con chaleco o manguitos para las bajadas.' : 'Arriba: manga corta y una capa fina en la mochila.')
      : coldest >= 8 ? 'Arriba: manga larga fina y cortavientos.'
      : coldest >= 2 ? 'Arriba: camiseta térmica de manga larga y chaqueta cortavientos o softshell.'
      : 'Arriba: tres capas: térmica, forro polar y chaqueta cortavientos o impermeable.',
  )
  out.push(
    coldest >= 14 ? (bike ? 'Abajo: culotte corto.' : 'Abajo: pantalón corto o fino.')
      : coldest >= 6 ? (bike ? 'Abajo: culotte corto con perneras o rodilleras.' : 'Abajo: pantalón largo fino.')
      : bike ? 'Abajo: culotte largo térmico.' : 'Abajo: pantalón largo de abrigo.',
  )
  if (coldest < 2) out.push(bike ? 'Guantes de invierno, gorro bajo el casco, braga de cuello y cubrezapatillas.' : 'Guantes, gorro y braga de cuello.')
  else if (coldest < 10) out.push(bike ? 'Guantes largos y braga de cuello.' : 'Guantes finos.')
  if (warmest - coldest >= 8) out.push(`Por capas: la sensación va de ${Math.round(coldest)} a ${Math.round(warmest)} °C a lo largo de la salida.`)
  const wet = rows.find((r) => r.precipMm >= 0.3 || (r.precipProbability ?? 0) >= 50)
  if (wet) {
    const heavy = rows.some((r) => r.precipMm >= 2 || (r.precipProbability ?? 0) >= 80)
    out.push(heavy ? `Chubasquero puesto desde el ${km(wet)}` + (bike ? ' y guardabarros.' : ' y funda para la mochila.') : `Chubasquero en la mochila: puede llover desde el ${km(wet)}.`)
  }
  const gust = rows.find((r) => r.gustKmh >= 40)
  if (gust && coldest >= 14) out.push(`Cortavientos: rachas de ${Math.round(gust.gustKmh)} km/h en el ${km(gust)}.`)
  const uv = Math.max(0, ...rows.map((r) => r.uv ?? 0))
  if (uv >= 6) out.push(`Sol fuerte (UV ${Math.round(uv)}): crema, gafas de sol y gorra o visera.`)
  else if (uv >= 3) out.push('Gafas de sol y crema.')
  if (Math.max(...rows.map((r) => r.feelsC)) >= 28) out.push('Calor: agua de sobra (medio litro por hora) y sales.')
  const dark = rows.find((r) => !r.isDay)
  if (dark) out.push(`Anochece por el camino (${km(dark)}): ` + (bike ? 'luces delante y detrás.' : 'frontal.'))
  return out
}

/** Salida por defecto: dentro de una hora si aún es pronto; si no, mañana a las 9. */
export function defaultStart(now: Date): Date {
  const d = new Date(now)
  d.setMinutes(0, 0, 0)
  if (now.getHours() < 15) d.setHours(Math.min(20, Math.max(6, now.getHours() + 1)))
  else { d.setDate(d.getDate() + 1); d.setHours(9) }
  return d
}
