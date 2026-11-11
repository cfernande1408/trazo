import { downsample, haversine, type Segments } from './geo'
import { L } from './i18n'

/**
 * Altitudes de Open-Meteo para un GPX que no las trae (como Http.withElevation
 * en la app): se piden unos cientos de puntos repartidos por la ruta y el
 * resto se interpola por distancia.
 */
export async function fillElevation(segments: Segments, fetcher: typeof fetch = fetch): Promise<Segments> {
  const all = segments.flat()
  if (all.length === 0 || all.every((p) => p.ele != null)) return segments
  // Distancia acumulada de cada punto
  const along: number[] = []
  let acc = 0
  for (const seg of segments) seg.forEach((p, i) => { if (i > 0) acc += haversine(seg[i - 1], p); along.push(acc) })
  const idx = downsample(all.map((_, i) => i), 400)
  const eles: number[] = []
  for (let i = 0; i < idx.length; i += 100) {
    const chunk = idx.slice(i, i + 100)
    const url = 'https://api.open-meteo.com/v1/elevation?latitude=' +
      chunk.map((k) => all[k].lat.toFixed(5)).join(',') + '&longitude=' + chunk.map((k) => all[k].lon.toFixed(5)).join(',')
    const res = await fetcher(url)
    if (!res.ok) throw new Error(L('No se pudieron pedir las altitudes', 'Could not get the altitudes'))
    const json = (await res.json()) as { elevation: number[] }
    eles.push(...json.elevation)
  }
  const sampleAlong = idx.map((k) => along[k])
  let j = 0
  let n = 0
  return segments.map((seg) =>
    seg.map((p) => {
      const d = along[n++]
      while (j < sampleAlong.length - 2 && sampleAlong[j + 1] < d) j++
      const a = sampleAlong[j], b = sampleAlong[j + 1] ?? a
      const t = b > a ? Math.min(1, Math.max(0, (d - a) / (b - a))) : 0
      const ele = eles[j] + ((eles[j + 1] ?? eles[j]) - eles[j]) * t
      return { ...p, ele: Math.round(ele * 10) / 10 }
    }),
  )
}
