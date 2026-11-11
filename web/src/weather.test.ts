import { describe, expect, it } from 'vitest'
import { defaultStart, detailRows, forecastUrl, parse, samples, warnings, whatToWear, type RouteSample } from './weather'

const t0 = 1_762_502_400 // en segundos, una hora en punto

/** Dos puntos: la salida (km 0) y el final (km 20), al que llegas en 2 h. */
const ss: RouteSample[] = [
  { distanceMeters: 0, lat: 40.7, lon: -3.9, ele: 1000, offsetMs: 0 },
  { distanceMeters: 20_000, lat: 40.8, lon: -3.9, ele: 2000, offsetMs: 2 * 3_600_000 },
]

function loc(temps: number[], feels: number[], o: { precip?: number[]; isDay?: number[]; uv?: number; gust?: number } = {}) {
  const precip = o.precip ?? temps.map(() => 0)
  return {
    hourly: {
      time: temps.map((_, i) => t0 + i * 3600),
      temperature_2m: temps,
      apparent_temperature: feels,
      precipitation_probability: precip.map((p) => (p > 0 ? 70 : 5)),
      precipitation: precip,
      weather_code: precip.map((p) => (p > 0 ? 61 : 1)),
      wind_speed_10m: temps.map(() => 12),
      wind_gusts_10m: temps.map(() => o.gust ?? 20),
      cloud_cover: temps.map(() => 40),
      uv_index: temps.map(() => o.uv ?? 2),
      is_day: o.isDay ?? temps.map(() => 1),
    },
  }
}

describe('detailRows', () => {
  it('una fila cada 15 minutos donde estarás', () => {
    const json = [loc([10, 12, 14, 16], [8, 10, 12, 14]), loc([4, 6, 8, 10], [2, 4, 6, 8])]
    const rows = detailRows(json, ss, t0 * 1000)
    expect(rows).toHaveLength(9)
    expect(rows[0].distanceMeters).toBeCloseTo(0, 1)
    expect(rows[8].distanceMeters).toBeCloseTo(20_000, 1)
    expect(rows[4].distanceMeters).toBeCloseTo(10_000, 1)
    expect(rows[0].tempC).toBeCloseTo(10, 2)
    expect(rows[8].tempC).toBeCloseTo(8, 2)
    expect(rows[4].tempC).toBeCloseTo(9, 2)
    expect(rows[2].tempC).toBeCloseTo(11 + (5 - 11) * 0.25, 2)
  })
  it('se salta lo que queda fuera de la previsión', () => {
    const json = [loc([10, 12], [8, 10]), loc([4, 6], [2, 4])]
    expect(detailRows(json, ss, t0 * 1000)).toHaveLength(5)
    expect(detailRows([], ss, t0 * 1000)).toHaveLength(0)
  })
})

describe('whatToWear', () => {
  it('con frío y lluvia en bici', () => {
    const o = { precip: [0, 2.5, 3, 0], isDay: [1, 1, 0, 0] }
    const json = [loc([2, 2, 2, 2], [-1, -1, -1, -1], o), loc([0, 0, 0, 0], [-3, -3, -3, -3], o)]
    const text = whatToWear(detailRows(json, ss, t0 * 1000), 'bike').join('\n')
    for (const s of ['tres capas', 'culotte largo', 'Guantes de invierno', 'Chubasquero puesto', 'luces']) expect(text).toContain(s)
  })
  it('con calor y sol a pie', () => {
    const json = [loc([26, 28, 30, 31], [27, 29, 31, 32], { uv: 8 }), loc([24, 26, 28, 29], [25, 27, 29, 30], { uv: 8 })]
    const text = whatToWear(detailRows(json, ss, t0 * 1000), 'hike').join('\n')
    for (const s of ['manga corta transpirable', 'Sol fuerte', 'agua de sobra']) expect(text).toContain(s)
    expect(text).not.toContain('Chubasquero')
  })
})

describe('resumen y avisos', () => {
  it('la hora más cercana a cuando pasas y sus avisos', () => {
    const json = [loc([1, 0, -1, -2], [0, 0, 0, 0], { gust: 60 }), loc([1, 0, -1, -2], [0, 0, 0, 0], { gust: 60 })]
    const pts = parse(json, ss, t0 * 1000)
    expect(pts).toHaveLength(2)
    expect(pts[1].timeMs).toBe((t0 + 2 * 3600) * 1000)
    expect(pts[1].tempC).toBe(-1)
    const w = warnings(pts).join('\n')
    expect(w).toContain('Bajo cero en el km 20')
    expect(w).toContain('Rachas de 60 km/h en el km 0')
  })
  it('url con todos los puntos y su altitud', () => {
    const u = forecastUrl(ss)
    expect(u).toContain('latitude=40.7000,40.8000')
    expect(u).toContain('elevation=1000,2000')
    expect(u).toContain('apparent_temperature')
  })
  it('puntos repartidos con la hora de paso', () => {
    const seg = Array.from({ length: 101 }, (_, i) => ({ lat: 40.7 + i * 0.0009, lon: -3.9, ele: 1000 + i * 5 }))
    const s = samples([seg], 'hike')
    expect(s.length).toBeGreaterThanOrEqual(2)
    expect(s[0].offsetMs).toBe(0)
    expect(s[s.length - 1].offsetMs).toBeGreaterThan(0)
    expect(s.every((x, i) => i === 0 || x.offsetMs >= s[i - 1].offsetMs)).toBe(true)
  })
})

describe('defaultStart', () => {
  it('pronto: dentro de una hora; tarde: mañana a las 9', () => {
    const morning = defaultStart(new Date(2026, 10, 11, 10, 39))
    expect([morning.getDate(), morning.getHours(), morning.getMinutes()]).toEqual([11, 11, 0])
    const evening = defaultStart(new Date(2026, 10, 11, 18, 5))
    expect([evening.getDate(), evening.getHours()]).toEqual([12, 9])
  })
})
