import maplibregl, { type GeoJSONSource, type Map as MlMap } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import './styles.css'
import logoUrl from '../../docs/logo.svg?url'
import { ProfileChart } from './chart'
import { fillElevation } from './elevation'
import {
  bounds, estimateMs, grades, isLoop, pointAt, profile, SLOPE_COLORS, SLOPE_LABELS, slopeLines, slopeSummary, stats,
  type Activity, type Grade, type ProfilePoint, type TrackStats,
} from './geo'
import { GpxError, parseGpx, writeGpx, type Gpx } from './gpx'
import { applyBase, BASE_MAPS, trazoStyle, treeImage, type BaseMap } from './mapstyle'
import { seasonOf } from './season'

// ---- Preferencias de este navegador (si se pueden guardar) ----
const prefs = {
  get(key: string): string | null {
    try { return localStorage.getItem('trazo-' + key) } catch { return null }
  },
  set(key: string, value: string) {
    try { localStorage.setItem('trazo-' + key, value) } catch { /* modo privado */ }
  },
}

const ES = 'es-ES'
const km = (m: number) => `${(m / 1000).toLocaleString(ES, { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`
const metros = (m: number) => `${Math.round(m).toLocaleString(ES)} m`
const pct = (p: number) => `${p.toLocaleString(ES, { maximumFractionDigits: 0 })} %`
const hours = (ms: number) => {
  const min = Math.round(ms / 60_000)
  return `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, '0')} min`
}
const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T
const escapeHtml = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!)

interface Route {
  file: string
  gpx: Gpx
  stats: TrackStats
  prof: ProfilePoint[]
  gs: Grade[]
  /** true si las altitudes vienen de Open-Meteo (el GPX no las traía). */
  filledEle: boolean
}

const season = seasonOf(new Date())
let activity: Activity = prefs.get('activity') === 'hike' ? 'hike' : 'bike'
let base: BaseMap = (['trazo', 'ign', 'pnoa'] as const).find((b) => b === prefs.get('base')) ?? 'trazo'
let relief = prefs.get('relief') !== 'false'
let slopeOn = prefs.get('slope') === 'true'
const routes: Route[] = []
let current = -1

// ---- Cabecera ----
const logo = $<HTMLImageElement>('logo')
logo.src = logoUrl
document.head.insertAdjacentHTML('beforeend', `<link rel="icon" href="${logoUrl}" type="image/svg+xml">`)

function setActivity(a: Activity) {
  activity = a
  prefs.set('activity', a)
  document.documentElement.dataset.activity = a
  document.querySelectorAll<HTMLButtonElement>('.segmented button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.activity === a)))
  renderSummary()
  chart.draw()
}
document.querySelectorAll<HTMLButtonElement>('.segmented button').forEach((b) => b.addEventListener('click', () => setActivity(b.dataset.activity as Activity)))

const fileInput = $<HTMLInputElement>('file')
for (const id of ['open', 'open2']) $(id).addEventListener('click', () => fileInput.click())
fileInput.addEventListener('change', () => {
  if (fileInput.files) openFiles(Array.from(fileInput.files))
  fileInput.value = ''
})

// Soltar archivos en cualquier parte de la página
const drop = $('drop')
let dragDepth = 0
window.addEventListener('dragenter', (e) => { if (e.dataTransfer?.types.includes('Files')) { dragDepth++; drop.hidden = false } })
window.addEventListener('dragleave', () => { if (--dragDepth <= 0) { dragDepth = 0; drop.hidden = true } })
window.addEventListener('dragover', (e) => e.preventDefault())
window.addEventListener('drop', (e) => {
  e.preventDefault()
  dragDepth = 0
  drop.hidden = true
  const files = Array.from(e.dataTransfer?.files ?? []).filter((f) => /\.(gpx|xml)$/i.test(f.name) || f.type.includes('xml'))
  if (files.length) openFiles(files)
  else toast('Eso no parece un GPX')
})

function toast(text: string) {
  const t = document.createElement('div')
  t.className = 'toast'
  t.textContent = text
  document.body.append(t)
  setTimeout(() => t.remove(), 3500)
}

// ---- Mapa ----
const map: MlMap = new maplibregl.Map({
  container: 'map',
  style: trazoStyle(season),
  center: [-3.7, 40.42],
  zoom: 5.4,
  attributionControl: { compact: true },
})
map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'bottom-right')
map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')

const empty = { type: 'FeatureCollection', features: [] } as GeoJSON.FeatureCollection
const src = (id: string) => map.getSource(id) as GeoJSONSource | undefined

/** true en cuanto el mapa ha cargado su estilo y tiene las capas de la ruta. */
let mapReady = false

map.on('load', () => {
  const dpr = window.devicePixelRatio || 1
  if (!map.hasImage('pino')) map.addImage('pino', treeImage(season, dpr), { pixelRatio: dpr })
  map.addSource('track', { type: 'geojson', data: empty })
  map.addSource('slope', { type: 'geojson', data: empty })
  map.addSource('wpts', { type: 'geojson', data: empty })
  map.addSource('ends', { type: 'geojson', data: empty })
  map.addSource('hover', { type: 'geojson', data: empty })
  map.addLayer({ id: 'track-casing', type: 'line', source: 'track', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#FFFFFF', 'line-width': 9 } })
  map.addLayer({ id: 'track-line', type: 'line', source: 'track', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#C2410C', 'line-width': 5 } })
  map.addLayer({
    id: 'slope-line', type: 'line', source: 'slope', layout: { 'line-cap': 'round', 'line-join': 'round', visibility: 'none' },
    paint: { 'line-color': ['match', ['get', 'b'], 0, SLOPE_COLORS[0], 1, SLOPE_COLORS[1], 2, SLOPE_COLORS[2], 3, SLOPE_COLORS[3], SLOPE_COLORS[4]], 'line-width': 5 },
  })
  map.addLayer({ id: 'wpts', type: 'circle', source: 'wpts', paint: { 'circle-radius': 6, 'circle-color': '#1F6FB2', 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 2 } })
  map.addLayer({
    id: 'ends', type: 'circle', source: 'ends',
    paint: {
      'circle-radius': ['match', ['get', 'k'], 'start', 9, 7],
      'circle-color': ['match', ['get', 'k'], 'start', '#C5F26B', '#1A1C16'],
      'circle-stroke-color': ['match', ['get', 'k'], 'start', '#1A1C16', '#FFFFFF'],
      'circle-stroke-width': 3,
    },
  })
  map.addLayer({ id: 'hover', type: 'circle', source: 'hover', paint: { 'circle-radius': 7, 'circle-color': '#FFFFFF', 'circle-stroke-color': '#C2410C', 'circle-stroke-width': 3 } })
  applyBase(map, base, relief)
  mapReady = true
  applySlope()
  if (current >= 0) showOnMap(routes[current], true)
})

map.on('click', 'wpts', (e) => {
  const f = e.features?.[0]
  if (!f) return
  const p = f.properties as { name?: string; desc?: string }
  new maplibregl.Popup({ maxWidth: '280px' })
    .setLngLat((f.geometry as GeoJSON.Point).coordinates as [number, number])
    .setHTML(`<b>${escapeHtml(p.name ?? 'Punto')}</b>${p.desc ? `<br>${escapeHtml(p.desc)}` : ''}`)
    .addTo(map)
})
map.on('mouseenter', 'wpts', () => (map.getCanvas().style.cursor = 'pointer'))
map.on('mouseleave', 'wpts', () => (map.getCanvas().style.cursor = ''))

// Capas: tipo de mapa, relieve y pendiente
const basesEl = $('bases')
for (const [id, b] of Object.entries(BASE_MAPS) as [BaseMap, (typeof BASE_MAPS)[BaseMap]][]) {
  const btn = document.createElement('button')
  btn.type = 'button'
  btn.textContent = b.label
  btn.title = b.note ?? ''
  btn.setAttribute('aria-pressed', String(id === base))
  btn.addEventListener('click', () => {
    base = id
    prefs.set('base', id)
    basesEl.querySelectorAll('button').forEach((x) => x.setAttribute('aria-pressed', String(x === btn)))
    if (mapReady) applyBase(map, base, relief)
  })
  basesEl.append(btn)
}
const reliefBox = $<HTMLInputElement>('relief')
reliefBox.checked = relief
reliefBox.addEventListener('change', () => {
  relief = reliefBox.checked
  prefs.set('relief', String(relief))
  if (mapReady) applyBase(map, base, relief)
})
const slopeBox = $<HTMLInputElement>('slopeLayer')
slopeBox.checked = slopeOn
slopeBox.addEventListener('change', () => {
  slopeOn = slopeBox.checked
  prefs.set('slope', String(slopeOn))
  applySlope()
})
function applySlope() {
  if (map.getLayer('slope-line')) {
    map.setLayoutProperty('slope-line', 'visibility', slopeOn ? 'visible' : 'none')
    map.setLayoutProperty('track-line', 'visibility', slopeOn ? 'none' : 'visible')
  }
  const legend = $('legend')
  legend.hidden = !slopeOn || current < 0
  legend.innerHTML = '<b>Pendiente</b>' + SLOPE_LABELS.map((l, i) => ({ l, i })).reverse()
    .map(({ l, i }) => `<span><i style="background:${SLOPE_COLORS[i]}"></i>${l}</span>`).join('')
}

// ---- Perfil ----
const chart = new ProfileChart($<HTMLCanvasElement>('chart'), (m) => {
  const r = routes[current]
  const readout = $('readout')
  if (!r || m == null) {
    src('hover')?.setData(empty)
    readout.textContent = ''
    return
  }
  const p = pointAt(r.gpx.segments, m)
  if (p) src('hover')?.setData({ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [p.lon, p.lat] } })
  const ele = r.prof.length ? r.prof.reduce((best, q) => (Math.abs(q.distanceMeters - m) < Math.abs(best.distanceMeters - m) ? q : best)).ele : undefined
  const g = r.gs.find((x) => x.startMeters <= m && m <= x.endMeters)
  readout.textContent = [km(m), ele != null ? metros(ele) : null, g ? pct(g.percent) : null].filter(Boolean).join(' · ')
})

// ---- Abrir rutas ----
async function openFiles(files: File[]) {
  let opened = 0
  for (const f of files) {
    try {
      const gpx = parseGpx(await f.text())
      let filledEle = false
      if (gpx.segments.flat().some((p) => p.ele == null)) {
        try {
          gpx.segments = await fillElevation(gpx.segments)
          filledEle = true
        } catch {
          toast('Sin conexión para pedir las altitudes: el perfil saldrá incompleto')
        }
      }
      const prof = profile(gpx.segments)
      routes.push({ file: f.name, gpx, stats: stats(gpx.segments), prof, gs: grades(prof), filledEle })
      opened++
    } catch (e) {
      toast(`${f.name}: ${e instanceof GpxError ? e.message : 'no se pudo leer'}`)
    }
  }
  if (opened) select(routes.length - 1)
}

function select(i: number) {
  current = i
  renderRoutes()
  renderSummary()
  const r = routes[i]
  $('profile').hidden = r.prof.length < 2
  chart.set(r.prof, r.gs)
  applySlope()
  // El perfil le quita alto al mapa: se encuadra después de que cambie de tamaño
  requestAnimationFrame(() => {
    map.resize()
    showOnMap(r, true)
  })
}

function showOnMap(r: Route, fit: boolean) {
  if (!mapReady) return
  const segs = r.gpx.segments.filter((s) => s.length >= 2)
  src('track')!.setData({ type: 'Feature', properties: {}, geometry: { type: 'MultiLineString', coordinates: segs.map((s) => s.map((p) => [p.lon, p.lat])) } })
  src('slope')!.setData(slopeLines(segs, r.gs))
  src('wpts')!.setData({
    type: 'FeatureCollection',
    features: r.gpx.waypoints.map((w) => ({ type: 'Feature', properties: { name: w.name ?? '', desc: w.desc ?? '' }, geometry: { type: 'Point', coordinates: [w.lon, w.lat] } })),
  })
  const first = segs[0]?.[0]
  const lastSeg = segs[segs.length - 1]
  const last = lastSeg?.[lastSeg.length - 1]
  src('ends')!.setData({
    type: 'FeatureCollection',
    features: [last && { k: 'end', p: last }, first && { k: 'start', p: first }].filter(Boolean).map((e) => ({
      type: 'Feature', properties: { k: e!.k }, geometry: { type: 'Point', coordinates: [e!.p.lon, e!.p.lat] },
    })),
  })
  const b = bounds(segs)
  if (fit && b) {
    const narrow = window.innerWidth < 860
    map.fitBounds([[b[0], b[1]], [b[2], b[3]]], { padding: narrow ? 40 : { top: 60, bottom: 60, left: 60, right: 200 }, duration: 600, maxZoom: 16 })
  }
}

function renderRoutes() {
  const el = $('routes')
  el.hidden = routes.length < 2
  el.innerHTML = ''
  routes.forEach((r, i) => {
    const b = document.createElement('button')
    b.type = 'button'
    if (i === current) b.setAttribute('aria-current', 'true')
    b.innerHTML = `<span>${escapeHtml(r.gpx.name ?? r.file)}</span><span>${km(r.stats.distanceMeters)}</span>`
    b.addEventListener('click', () => select(i))
    el.append(b)
  })
}

function renderSummary() {
  const r = routes[current]
  $('empty').hidden = !!r
  $('summary').hidden = !r
  if (!r) return
  const s = r.stats
  $('name').textContent = r.gpx.name ?? r.file
  const loop = isLoop(r.gpx.segments)
  $('tags').innerHTML = [loop ? 'Circular' : 'Lineal', activity === 'hike' ? 'A pie · método MIDE' : 'Bici de montaña', `${r.gpx.waypoints.length} puntos`]
    .map((t) => `<span class="tag">${t}</span>`).join('')
  const items: [string, string][] = [
    ['Distancia', km(s.distanceMeters)],
    ['Desnivel +', metros(s.gainMeters)],
    ['Desnivel −', metros(s.lossMeters)],
    ['Tiempo estimado', hours(estimateMs(activity, s.distanceMeters, s.gainMeters, s.lossMeters))],
    ['Altitud máx.', s.maxEle != null ? metros(s.maxEle) : '—'],
    ['Altitud mín.', s.minEle != null ? metros(s.minEle) : '—'],
  ]
  $('stats').innerHTML = items.map(([l, v]) => `<div class="stat"><b>${v}</b><span>${l}</span></div>`).join('')

  const sl = slopeSummary(r.gs)
  $('slope').innerHTML = sl
    ? `<h3>Pendiente</h3><div class="rows">
        <span>Rampa más dura</span><b>${pct(sl.maxUpPercent)} en el km ${(sl.maxUpAtMeters / 1000).toLocaleString(ES, { maximumFractionDigits: 1 })}</b>
        <span>Subiendo a más del 10 %</span><b>${metros(sl.upOver10Meters)}</b>
        <span>A más del 15 %</span><b>${metros(sl.upOver15Meters)}</b>
        <span>Bajada más fuerte</span><b>${pct(sl.maxDownPercent)}</b>
      </div>`
    : ''
  $('tip').textContent = season.tip
  $('points').innerHTML = r.gpx.waypoints.length
    ? `<h3>Puntos del autor</h3><ul class="points">${r.gpx.waypoints.map((w, i) => `<li data-i="${i}">${escapeHtml(w.name ?? 'Punto sin nombre')}</li>`).join('')}</ul>`
    : ''
  $('points').querySelectorAll<HTMLLIElement>('li').forEach((li) => li.addEventListener('click', () => {
    const w = r.gpx.waypoints[Number(li.dataset.i)]
    map.flyTo({ center: [w.lon, w.lat], zoom: Math.max(map.getZoom(), 15) })
  }))
  const wikiloc = r.gpx.links.find((l) => l.includes('wikiloc'))
  const source = $<HTMLAnchorElement>('source')
  source.hidden = !wikiloc
  if (wikiloc) source.href = wikiloc
  $('note').textContent = r.filledEle ? 'El GPX no traía altitudes: las del perfil son de Open-Meteo.' : ''
}

$('download').addEventListener('click', () => {
  const r = routes[current]
  if (!r) return
  const blob = new Blob([writeGpx(r.gpx)], { type: 'application/gpx+xml' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = (r.gpx.name ?? 'ruta').toLowerCase().replace(/[^a-z0-9áéíóúñü]+/gi, '-').replace(/^-|-$/g, '') + '.gpx'
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
})

setActivity(activity)
