import { describe, expect, it } from 'vitest'
import { GpxError, parseGpx, writeGpx } from './gpx'

const WIKILOC = `<?xml version="1.0" encoding="UTF-8"?>
<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="Wikiloc">
  <metadata><name>Wikiloc - Bola del Mundo</name><link href="https://es.wikiloc.com/rutas/123"><text>Wikiloc</text></link></metadata>
  <wpt lat="40.785" lon="-3.98"><ele>1850</ele><name><![CDATA[Fuente & mirador]]></name><desc>Agua fresca</desc></wpt>
  <trk>
    <name>Wikiloc - Bola del Mundo</name>
    <desc><![CDATA[Subida <b>dura</b>]]></desc>
    <trkseg>
      <trkpt lat="40.78" lon="-3.97"><ele>1500</ele></trkpt>
      <trkpt lat="40.781" lon="-3.971"><ele>1510.5</ele></trkpt>
      <trkpt lat="40.782" lon="-3.972"></trkpt>
    </trkseg>
  </trk>
</gpx>`

describe('parseGpx', () => {
  it('lee un GPX de Wikiloc', () => {
    const g = parseGpx(WIKILOC)
    expect(g.name).toBe('Bola del Mundo')
    expect(g.description).toBe('Subida <b>dura</b>')
    expect(g.segments).toHaveLength(1)
    expect(g.segments[0]).toHaveLength(3)
    expect(g.segments[0][1].ele).toBe(1510.5)
    expect(g.segments[0][2].ele).toBeUndefined()
    expect(g.waypoints[0]).toMatchObject({ name: 'Fuente & mirador', desc: 'Agua fresca', ele: 1850 })
    expect(g.links).toEqual(['https://es.wikiloc.com/rutas/123'])
  })

  it('lee rutas rte sin namespace', () => {
    const g = parseGpx('<gpx><rte><name>Ruta</name><rtept lat="1" lon="2"/><rtept lat="1.1" lon="2.1"/></rte></gpx>')
    expect(g.name).toBe('Ruta')
    expect(g.segments[0]).toHaveLength(2)
  })

  it('avisa si no es un GPX', () => {
    expect(() => parseGpx('<kml></kml>')).toThrow(GpxError)
    expect(() => parseGpx('esto no es xml')).toThrow(GpxError)
    expect(() => parseGpx('<gpx><trk><trkseg><trkpt lat="1" lon="2"/></trkseg></trk></gpx>')).toThrow('no tiene recorrido')
  })

  it('escribe un GPX que se vuelve a leer igual', () => {
    const g = parseGpx(WIKILOC)
    const again = parseGpx(writeGpx(g))
    expect(again.name).toBe(g.name)
    expect(again.description).toBe(g.description)
    expect(again.segments).toEqual(g.segments)
    expect(again.waypoints.map((w) => w.name)).toEqual(['Fuente & mirador'])
  })
})
