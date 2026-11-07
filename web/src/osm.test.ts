import { describe, expect, it } from 'vitest'
import {
  expand, fromMerc, mercBbox, mercCenter, overpassArea, parseOverpassArea, parseOverpassRoute, parsePlaces, parseWmtDetails,
  parseWmtList, parseWmtSegments, routeShape, stitch, summaries, toMerc, wmtSearch, zoomFor, type Bbox,
} from './osm'
import type { GeoPoint } from './geo'

const lat0 = 40.8
const mLon = 111_195 * Math.cos((lat0 * Math.PI) / 180)
const p = (e: number, n: number): GeoPoint => ({ lat: lat0 + n / 111_195, lon: -4 + e / mLon })
const line = (a: GeoPoint, b: GeoPoint) => Array.from({ length: 11 }, (_, k) => ({ lat: a.lat + ((b.lat - a.lat) * k) / 10, lon: a.lon + ((b.lon - a.lon) * k) / 10 }))
const area: Bbox = { south: lat0 - 5_000 / 111_195, west: -4 - 5_000 / mLon, north: lat0 + 5_000 / 111_195, east: -4 + 5_000 / mLon }

describe('Waymarked Trails', () => {
  it('pasa a Mercator y vuelve', () => {
    const [x, y] = toMerc(40.4, -3.7)
    const back = fromMerc(x, y)
    expect(back.lat).toBeCloseTo(40.4, 9)
    expect(back.lon).toBeCloseTo(-3.7, 9)
    expect(mercBbox({ south: 0, west: 0, north: 1, east: 1 })).toBe('0.0,0.0,111319.5,111325.1')
    expect(mercCenter(mercBbox({ south: 40, west: -4, north: 41, east: -3 }))!.lon).toBeCloseTo(-3.5, 6)
  })

  it('lee la lista, los trazados y los detalles', () => {
    expect(parseWmtList({ results: [{ id: 1, name: 'Ruta A', ref: 'PR-M 13' }, { id: 2, ref: 'GR 10' }, { name: 'sin id' }] }))
      .toEqual([{ id: 1, name: 'Ruta A', ref: 'PR-M 13' }, { id: 2, name: 'GR 10', ref: 'GR 10' }])
    const [x1, y1] = toMerc(40.8, -4), [x2, y2] = toMerc(40.81, -3.99)
    const segs = parseWmtSegments({ features: [
      { id: 1, geometry: { type: 'LineString', coordinates: [[x1, y1], [x2, y2]] } },
      { id: 1, geometry: { type: 'MultiLineString', coordinates: [[[x2, y2], [x1, y1]], [[x1, y1]]] } },
    ] })
    expect(segs.get(1)).toHaveLength(2)
    expect(segs.get(1)![0][1].lat).toBeCloseTo(40.81, 6)
    expect(parseWmtDetails({ bbox: [1, 2, 3.04, 4], description: 'Bonita' })).toEqual({ merc: '1.0,2.0,3.0,4.0', description: 'Bonita' })
    expect(wmtSearch('mtb', ' PR-M 13 ')).toContain('query=PR-M%2013')
  })

  it('pone el icono a mitad del tramo más largo y sabe la forma', () => {
    // El tramo de abajo (2 km) es el más largo: el icono va en su mitad
    const a = p(0, 0), b = p(2000, 0), c = p(2000, 1000), d = p(500, 1000)
    const segs = new Map([[7, [line(c, d), line(a, b), line(b, c), line(d, a)]]])
    const [r] = summaries('mtb', [{ id: 7, name: 'Vuelta' }], segs, area)
    expect(r.isMtb).toBe(true)
    expect(r.shape).toBe('loop')
    expect(r.lon).toBeCloseTo(p(1000, 0).lon, 4)
    expect(r.lat).toBeCloseTo(lat0, 4)
  })
})

describe('Overpass', () => {
  it('pide la zona y lee los centros', () => {
    expect(overpassArea({ south: 40, west: -4, north: 41, east: -3 }, 'mtb|bicycle')).toContain('relation["route"~"^(mtb|bicycle)$"](40.00000,-4.00000,41.00000,-3.00000)')
    const r = parseOverpassArea({ elements: [
      { type: 'relation', id: 5, center: { lat: 40, lon: -4 }, tags: { name: 'Senda', route: 'bicycle', roundtrip: 'yes' } },
      { type: 'node', id: 6 },
    ] })
    expect(r).toEqual([{ id: 5, name: 'Senda', ref: undefined, network: undefined, lat: 40, lon: -4, isMtb: false, shape: 'loop', description: undefined }])
  })

  it('une las vías de una relación', () => {
    const json = { elements: [
      { type: 'relation', id: 9, tags: { 'name:es': 'Camino' } },
      { type: 'way', id: 1, geometry: [{ lat: 40, lon: -4 }, { lat: 40.001, lon: -4 }] },
      { type: 'way', id: 2, geometry: [{ lat: 40.002, lon: -4 }, { lat: 40.001, lon: -4 }] },
    ] }
    const r = parseOverpassRoute(json, 9)
    expect(r.name).toBe('Camino')
    expect(r.segments).toHaveLength(1)
    expect(r.segments[0].map((q) => q.lat)).toEqual([40, 40.001, 40.002])
    expect(() => parseOverpassRoute({ elements: [] }, 9)).toThrow()
  })
})

describe('tramos y forma', () => {
  it('encadena vías desordenadas y al revés', () => {
    const out = stitch([line(p(1000, 0), p(2000, 0)), line(p(0, 0), p(1000, 0)).reverse(), line(p(5000, 0), p(6000, 0))])
    expect(out).toHaveLength(2)
    expect(out[0]).toHaveLength(21)
  })

  it('circular, lineal o sin saber', () => {
    const a = p(0, 0), b = p(1000, 0), c = p(1000, 1000), d = p(0, 1000)
    expect(routeShape([line(c, d), line(a, b), line(c, b), line(d, a)], area)).toBe('loop')
    expect(routeShape([line(a, b), line(b, c), line(c, d), line(d, a), line(p(-1000, -500), a)], area)).toBe('loop')
    expect(routeShape([line(p(0, 0), p(2000, 500))], area)).toBe('linear')
    expect(routeShape([line(p(0, 0), p(4950, 0))], area)).toBe('unknown')
    expect(routeShape([])).toBe('unknown')
  })
})

describe('lugares', () => {
  it('lee Nominatim y elige el zoom', () => {
    const [pl] = parsePlaces([{ lat: '40.78', lon: '-3.98', name: 'Bola del Mundo', display_name: 'Bola del Mundo, Navacerrada, Madrid, Comunidad de Madrid, España', boundingbox: ['40.77', '40.79', '-3.99', '-3.97'], type: 'peak' }])
    expect(pl).toMatchObject({ name: 'Bola del Mundo', detail: 'Navacerrada, Madrid, Comunidad de Madrid', kind: 'peak' })
    expect(zoomFor(pl)).toBe(12.5)
    expect(zoomFor({ ...pl, bbox: undefined })).toBe(13)
    expect(expand({ south: 0, west: 0, north: 1, east: 1 }, 0.1)).toEqual({ south: -0.1, west: -0.1, north: 1.1, east: 1.1 })
  })
})
