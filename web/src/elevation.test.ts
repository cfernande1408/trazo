import { describe, expect, it } from 'vitest'
import { fillElevation } from './elevation'

describe('fillElevation', () => {
  it('pide altitudes y las interpola por distancia', async () => {
    const seg = Array.from({ length: 1000 }, (_, i) => ({ lat: 40 + i * 0.0001, lon: -3.7 }))
    const urls: string[] = []
    // Altitud de mentira: 1000 m + 1 m por cada milésima de grado al norte
    const fake = (async (url: string) => {
      urls.push(url)
      const lats = new URL(url).searchParams.get('latitude')!.split(',').map(Number)
      return new Response(JSON.stringify({ elevation: lats.map((l) => 1000 + (l - 40) * 1000) }))
    }) as typeof fetch
    const [out] = await fillElevation([seg], fake)
    expect(urls.length).toBe(4)
    expect(out[0].ele).toBeCloseTo(1000, 0)
    expect(out[500].ele).toBeCloseTo(1050, 0)
    expect(out[999].ele).toBeCloseTo(1099.9, 0)
  })

  it('no pide nada si ya hay altitudes', async () => {
    const segs = [[{ lat: 1, lon: 2, ele: 5 }]]
    const out = await fillElevation(segs, (() => { throw new Error('no') }) as typeof fetch)
    expect(out).toBe(segs)
  })
})
