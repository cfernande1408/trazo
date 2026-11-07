import type { GeoPoint, Segments, Waypoint } from './geo'

export interface Gpx {
  name?: string
  description?: string
  segments: Segments
  waypoints: Waypoint[]
  /** Enlaces http de la ruta (p. ej. la página de Wikiloc). */
  links: string[]
}

export class GpxError extends Error {}

/** Los hijos directos con ese nombre, sin namespace (GPX 1.0 y 1.1, con o sin prefijo). */
function children(el: Element, name: string): Element[] {
  return Array.from(el.children).filter((c) => c.localName === name)
}

function text(el: Element, name: string): string | undefined {
  const t = children(el, name)[0]?.textContent?.trim()
  return t ? t : undefined
}

function point(el: Element): GeoPoint | undefined {
  const lat = parseFloat(el.getAttribute('lat') ?? '')
  const lon = parseFloat(el.getAttribute('lon') ?? '')
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return undefined
  const e = parseFloat(text(el, 'ele') ?? '')
  return Number.isFinite(e) ? { lat, lon, ele: e } : { lat, lon }
}

/**
 * Lee un GPX como la app: tracks (`trk`), rutas (`rte`) y puntos (`wpt`), y
 * quita el «Wikiloc - » del nombre.
 */
export function parseGpx(xml: string): Gpx {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  const root = doc.documentElement
  if (!root || root.localName !== 'gpx' || doc.getElementsByTagName('parsererror').length > 0) {
    throw new GpxError('No es un archivo GPX válido')
  }
  const segments: Segments = []
  for (const trk of children(root, 'trk')) {
    for (const seg of children(trk, 'trkseg')) {
      const pts = children(seg, 'trkpt').map(point).filter((p): p is GeoPoint => !!p)
      if (pts.length) segments.push(pts)
    }
  }
  for (const rte of children(root, 'rte')) {
    const pts = children(rte, 'rtept').map(point).filter((p): p is GeoPoint => !!p)
    if (pts.length) segments.push(pts)
  }
  const waypoints = children(root, 'wpt').flatMap((w) => {
    const p = point(w)
    return p ? [{ ...p, name: text(w, 'name'), desc: text(w, 'desc') ?? text(w, 'cmt') }] : []
  })
  const trk = children(root, 'trk')[0] ?? children(root, 'rte')[0]
  const meta = children(root, 'metadata')[0]
  const name = (trk && text(trk, 'name')) ?? (meta && text(meta, 'name'))
  const description = (trk && text(trk, 'desc')) ?? (meta && text(meta, 'desc'))
  const links = [trk, meta]
    .filter((e): e is Element => !!e)
    .flatMap((e) => children(e, 'link').map((l) => l.getAttribute('href') ?? ''))
    .filter((h) => h.startsWith('http'))
  if (segments.every((s) => s.length < 2) && waypoints.length === 0) throw new GpxError('El GPX no tiene recorrido')
  return { name: name?.replace(/^Wikiloc - /, ''), description, segments, waypoints, links }
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** GPX para pasar la ruta al móvil (con altitudes, si se completaron). */
export function writeGpx(gpx: Gpx): string {
  const name = gpx.name ?? 'Ruta'
  const wpts = gpx.waypoints
    .map((w) => `  <wpt lat="${w.lat.toFixed(7)}" lon="${w.lon.toFixed(7)}">${w.ele != null ? `<ele>${w.ele.toFixed(1)}</ele>` : ''}${w.name ? `<name>${esc(w.name)}</name>` : ''}${w.desc ? `<desc>${esc(w.desc)}</desc>` : ''}</wpt>\n`)
    .join('')
  const segs = gpx.segments
    .map((seg) =>
      '    <trkseg>\n' +
      seg.map((p) => `      <trkpt lat="${p.lat.toFixed(7)}" lon="${p.lon.toFixed(7)}">${p.ele != null ? `<ele>${p.ele.toFixed(1)}</ele>` : ''}</trkpt>\n`).join('') +
      '    </trkseg>\n')
    .join('')
  return `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Trazo web" xmlns="http://www.topografix.com/GPX/1/1">
${wpts}  <trk>
    <name>${esc(name)}</name>
${gpx.description ? `    <desc>${esc(gpx.description)}</desc>\n` : ''}${segs}  </trk>
</gpx>
`
}
