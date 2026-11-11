import { describe, expect, it } from 'vitest'
import type { GeoPoint } from './geo'
import { classify, gradeLabel, osmWaysQuery, parseWays, summarizeDifficulty, summarizeSurface, type OsmWay } from './surface'

describe('classify', () => {
  const k = (tags: Record<string, string>) => classify(tags)
  it('por superficie y tipo de vía', () => {
    expect(k({ highway: 'residential' })).toBe('paved')
    expect(k({ highway: 'track', surface: 'asphalt' })).toBe('paved')
    expect(k({ highway: 'track', tracktype: 'grade1' })).toBe('paved')
    expect(k({ highway: 'track' })).toBe('track')
    expect(k({ highway: 'track', tracktype: 'grade3' })).toBe('track')
    expect(k({ highway: 'unclassified', surface: 'gravel' })).toBe('track')
    expect(k({ highway: 'path' })).toBe('path')
    expect(k({ highway: 'footway', surface: 'dirt' })).toBe('path')
    expect(k({ highway: 'path', surface: 'concrete' })).toBe('paved')
    expect(k({ highway: 'footway', footway: 'sidewalk' })).toBe('paved')
    expect(k({ highway: 'cycleway' })).toBe('paved')
    expect(k({ highway: 'cycleway', surface: 'compacted' })).toBe('path')
    expect(k({ highway: 'steps' })).toBe('steps')
    expect(k({ highway: 'track', surface: 'asphalt;gravel' })).toBe('paved')
    expect(k({ highway: 'construction' })).toBeUndefined()
    expect(k({ building: 'yes' })).toBeUndefined()
  })
})

describe('parseWays', () => {
  it('tipo y dificultad de cada camino', () => {
    const ways = parseWays({
      elements: [
        { type: 'way', tags: { highway: 'path', 'mtb:scale': '2+', sac_scale: 'mountain_hiking' }, geometry: [{ lat: 40, lon: -3 }, { lat: 40.001, lon: -3 }] },
        { type: 'way', tags: { highway: 'construction' }, geometry: [{ lat: 40, lon: -3 }, { lat: 40.001, lon: -3 }] },
        { type: 'way', tags: { highway: 'path' }, geometry: [{ lat: 40, lon: -3 }] },
      ],
    })
    expect(ways).toHaveLength(2)
    expect(ways[0]).toMatchObject({ kind: 'path', mtb: 2, sac: 2 })
    expect(ways[1].kind).toBeUndefined()
  })
})

describe('resúmenes', () => {
  const north = (from: number, m: number): GeoPoint => ({ lat: from + m / 111_195, lon: -3.7 })
  const start = 40.5
  const route = Array.from({ length: 181 }, (_, i) => north(start, i * 10))
  const track: OsmWay = { points: [north(start, 0), north(start, 1000)], kind: 'track', mtb: 0, sac: 1 }
  // A 8 m del track, lo que se desvía un GPS
  const path: OsmWay = { points: [{ lat: start + 1000 / 111_195, lon: -3.7 + 8 / 85_000 }, { lat: start + 1500 / 111_195, lon: -3.7 + 8 / 85_000 }], kind: 'path', mtb: 3, sac: 2 }
  // Una calle a 200 m, que no cuenta
  const far: OsmWay = { points: [{ lat: start, lon: -3.7 + 200 / 85_000 }, { lat: start + 0.02, lon: -3.7 + 200 / 85_000 }], kind: 'paved' }

  it('reparte la ruta por superficie', () => {
    const s = summarizeSurface([route], [track, path, far])
    expect(s.routeMeters).toBeCloseTo(1800, -1)
    expect(Math.abs(s.meters.track! - 1000)).toBeLessThan(15)
    expect(Math.abs(s.meters.path! - 500)).toBeLessThan(15)
    expect(s.meters.paved).toBeUndefined()
  })
  it('dificultad MTB y SAC', () => {
    const mtb = summarizeDifficulty([route], [track, path, far], true)
    expect(mtb.mainGrade).toBe(0)
    expect(mtb.maxGrade).toBe(3)
    expect(Math.abs(mtb.ratedMeters - 1500)).toBeLessThan(20)
    const sac = summarizeDifficulty([route], [track, path, far], false)
    expect(sac.byGrade.map(([g]) => g)).toEqual([1, 2])
    expect(gradeLabel(3, true)).toEqual(['S3', 'bloques, escalones y curvas cerradas'])
    expect(gradeLabel(2, false)).toEqual(['T2', 'senderismo de montaña'])
  })
  it('consulta en recuadros pequeños', () => {
    const r = Array.from({ length: 401 }, (_, i) => ({ lat: 40 + i * 0.0002, lon: -3.7 }))
    const q = osmWaysQuery([r])
    expect(q.startsWith('[out:json]')).toBe(true)
    expect(q.endsWith('out tags geom;')).toBe(true)
    const n = q.split('way[highway](').length - 1
    expect(n).toBeGreaterThanOrEqual(95)
    expect(n).toBeLessThanOrEqual(105)
  })
})
