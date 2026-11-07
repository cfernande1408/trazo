import { describe, expect, it } from 'vitest'
import { bucket, downsample, eleAt, estimateMs, grades, haversine, isLoop, pointAt, profile, slopeLines, slopeSummary, stats, type GeoPoint } from './geo'

const lat0 = 40.78
const mLon = 111_195 * Math.cos((lat0 * Math.PI) / 180)
/** Punto a [e] m al este del origen, a [ele] m de altitud. */
const p = (e: number, ele?: number, n = 0): GeoPoint => ({ lat: lat0 + n / 111_195, lon: -3.97 + e / mLon, ele })

describe('stats', () => {
  it('mide como el núcleo de la app', () => {
    expect(haversine({ lat: 40, lon: -3.7 }, { lat: 41, lon: -3.7 })).toBeCloseTo(111_195, -1)
  })

  it('no suma el ruido del GPS al desnivel', () => {
    // Llano a 1000 m con ±1 m de ruido y luego 100 m de subida
    const pts = [...Array.from({ length: 50 }, (_, i) => p(i * 10, 1000 + (i % 2))), ...Array.from({ length: 11 }, (_, i) => p(500 + i * 100, 1000 + i * 10))]
    const s = stats([pts])
    expect(s.gainMeters).toBeCloseTo(100, 0)
    expect(s.lossMeters).toBe(0)
    expect(s.minEle).toBe(1000)
    expect(s.maxEle).toBe(1100)
    expect(s.distanceMeters).toBeCloseTo(1500, 0)
  })

  it('no une el hueco entre segmentos', () => {
    expect(stats([[p(0), p(100)], [p(1000), p(1100)]]).distanceMeters).toBeCloseTo(200, 0)
  })
})

describe('perfil y pendiente', () => {
  // 1 km al 6 %, 500 m al 16 % y 500 m bajando al 8 %
  const seg = [
    ...Array.from({ length: 10 }, (_, i) => p(i * 100, 1000 + i * 6)),
    ...Array.from({ length: 6 }, (_, i) => p(1000 + i * 100, 1060 + i * 16)),
    ...Array.from({ length: 5 }, (_, i) => p(1600 + i * 100, 1140 - (i + 1) * 8)),
  ]
  const prof = profile([seg])
  const gs = grades(prof)

  it('reparte en tramos de 100 m', () => {
    expect(gs.length).toBe(20)
    expect(gs[0].percent).toBeCloseTo(6, 0)
    expect(gs[12].percent).toBeCloseTo(16, 0)
  })

  it('resume la rampa más dura y los metros duros', () => {
    const s = slopeSummary(gs)!
    expect(s.maxUpPercent).toBeCloseTo(16, 0)
    expect(s.maxUpAtMeters).toBeGreaterThanOrEqual(1000)
    expect(s.upOver10Meters).toBeCloseTo(500, -1)
    expect(s.upOver15Meters).toBeCloseTo(500, -1)
    expect(s.maxDownPercent).toBeCloseTo(-8, 0)
  })

  it('pinta cada tramo de su color', () => {
    const fc = slopeLines([seg], gs)
    const used = new Set(fc.features.map((f) => (f.properties as { b: number }).b))
    expect(used).toEqual(new Set([1, 3]))
    expect(bucket(4.9)).toBe(0)
    expect(bucket(-12)).toBe(2)
    expect(bucket(25)).toBe(4)
  })

  it('interpola altitud y posición', () => {
    expect(eleAt(prof, 50)).toBeCloseTo(1003, 1)
    expect(pointAt([seg], 250)!.lon).toBeCloseTo(p(250).lon, 6)
  })
})

describe('resumen', () => {
  it('sabe si es circular', () => {
    expect(isLoop([[p(0), p(1000), p(1000, undefined, 1000), p(50)]])).toBe(true)
    expect(isLoop([[p(0), p(1000), p(3000)]])).toBe(false)
  })

  it('estima el tiempo como la app', () => {
    // 20 km y 600 m: bici 20/14 + 1 h; a pie MIDE: max(4, 1.5+1) + min/2
    expect(estimateMs('bike', 20_000, 600, 600) / 3_600_000).toBeCloseTo(20 / 14 + 1, 5)
    expect(estimateMs('hike', 20_000, 600, 600) / 3_600_000).toBeCloseTo(4 + 2.5 / 2, 5)
  })

  it('reduce puntos conservando los extremos', () => {
    const r = downsample(Array.from({ length: 1000 }, (_, i) => i), 10)
    expect(r.length).toBe(10)
    expect(r[0]).toBe(0)
    expect(r[9]).toBe(999)
  })
})
