import type { Activity, Segments } from './geo'
import { friendly, getJson } from './net'
import { defaultStart, describe, detailRows, forecastUrl, parse, samples, warnings, whatToWear, FORECAST_DAYS, type DetailRow, type WeatherAt } from './weather'

const ES = 'es-ES'
const hhmm = (ms: number) => new Date(ms).toLocaleTimeString(ES, { hour: '2-digit', minute: '2-digit' })
const DAY = 86_400_000

/**
 * La tarjeta del tiempo de una ruta, como la pestaña Tiempo de la app: eliges
 * día y hora de salida y ves avisos, el tiempo en cada tramo cuando pases
 * (o cada 15 minutos en el detalle) y qué llevarte puesto.
 */
export class WeatherCard {
  private dayOffset: number
  private hour: number
  private detail = false
  private load?: AbortController
  /** Lo último pedido, para no volver a pedirlo si solo cambias de vista. */
  private key = ''
  private result: { points: WeatherAt[]; rows: DetailRow[]; warn: string[] } | { error: string } | null = null
  private segments: Segments = []
  private activity: Activity = 'bike'

  constructor(private el: HTMLElement) {
    const d = defaultStart(new Date())
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    this.dayOffset = Math.round((new Date(d).setHours(0, 0, 0, 0) - today.getTime()) / DAY)
    this.hour = d.getHours()
  }

  /** Una ruta nueva (o la misma con más datos): se pide la previsión si hace falta. */
  set(segments: Segments, activity: Activity, ready: boolean) {
    this.segments = segments
    this.activity = activity
    if (!segments.length || !ready) {
      this.el.innerHTML = segments.length ? '<h3>Tiempo por el camino</h3><p class="muted small">Esperando el recorrido completo…</p>' : ''
      return
    }
    this.fetch()
  }

  clear() {
    this.load?.abort()
    this.segments = []
    this.key = ''
    this.result = null
    this.el.innerHTML = ''
  }

  private startMs(): number {
    const d = new Date()
    d.setHours(this.hour, 0, 0, 0)
    return d.getTime() + this.dayOffset * DAY
  }

  private async fetch() {
    const ss = samples(this.segments, this.activity)
    if (!ss.length) return
    const url = forecastUrl(ss)
    const start = this.startMs()
    const key = `${url}|${start}|${this.activity}`
    if (key === this.key) {
      // Ya viene de camino, o ya está (si falló, se vuelve a intentar)
      if (!this.result) return
      if (!('error' in this.result)) return this.render()
    }
    this.key = key
    this.load?.abort()
    const ctl = new AbortController()
    this.load = ctl
    this.result = null
    this.render(true)
    try {
      const json = await getJson(url, ctl.signal)
      if (ctl.signal.aborted) return
      const points = parse(json, ss, start)
      this.result = { points, rows: detailRows(json, ss, start), warn: warnings(points) }
    } catch (e) {
      if (ctl.signal.aborted) return
      this.result = { error: `No se pudo mirar el tiempo: ${friendly(e)}` }
    }
    this.render()
  }

  private dayLabel(d: number): string {
    if (d === 0) return 'Hoy'
    if (d === 1) return 'Mañana'
    const s = new Date(Date.now() + d * DAY).toLocaleDateString(ES, { weekday: 'short', day: 'numeric' })
    return s.charAt(0).toUpperCase() + s.slice(1)
  }

  private render(loading = false) {
    const days = Array.from({ length: FORECAST_DAYS - 1 }, (_, d) =>
      `<button type="button" class="chip" data-day="${d}" aria-pressed="${d === this.dayOffset}">${this.dayLabel(d)}</button>`).join('')
    const hours = Array.from({ length: 17 }, (_, i) => i + 5)
      .map((h) => `<option value="${h}"${h === this.hour ? ' selected' : ''}>${String(h).padStart(2, '0')}:00</option>`).join('')
    let body = ''
    const r = this.result
    if (loading || !r) body = '<p class="muted small">Mirando el tiempo a lo largo de la ruta…</p>'
    else if ('error' in r) body = `<p class="error small">${r.error}</p>`
    else if (!r.points.length) body = '<p class="muted small">Esa hora queda fuera de la previsión (7 días).</p>'
    else {
      const warn = r.warn.length
        ? `<div class="warn">${r.warn.map((w) => `<div>${w}</div>`).join('')}</div>`
        : '<div class="ok">Sin avisos: buen día para salir.</div>'
      const toggle = `<div class="filters"><button type="button" class="chip" data-detail="0" aria-pressed="${!this.detail}">Resumen</button>` +
        `<button type="button" class="chip" data-detail="1" aria-pressed="${this.detail}">Detalle cada 15 min</button></div>`
      const list = this.detail ? this.table(r.rows) : this.summary(r.points)
      const wear = whatToWear(r.rows, this.activity)
      body = warn + toggle + list +
        (wear.length ? `<h4>Qué llevarte puesto</h4><ul class="wear">${wear.map((w) => `<li>${w}</li>`).join('')}</ul>` : '')
    }
    this.el.innerHTML = `<h3>Tiempo por el camino</h3>
      <div class="filters days">${days}</div>
      <label class="hour">Salida a las <select data-hour>${hours}</select></label>
      ${body}
      <p class="muted small">Previsión de Open-Meteo a la altitud de cada punto. Las horas de paso son estimadas ${this.activity === 'hike' ? 'con el método MIDE' : 'para bici de montaña'}.</p>`
    this.el.querySelectorAll<HTMLButtonElement>('[data-day]').forEach((b) => b.addEventListener('click', () => {
      this.dayOffset = Number(b.dataset.day)
      this.fetch()
    }))
    this.el.querySelector<HTMLSelectElement>('[data-hour]')!.addEventListener('change', (e) => {
      this.hour = Number((e.target as HTMLSelectElement).value)
      this.fetch()
    })
    this.el.querySelectorAll<HTMLButtonElement>('[data-detail]').forEach((b) => b.addEventListener('click', () => {
      this.detail = b.dataset.detail === '1'
      this.render()
    }))
  }

  private summary(points: WeatherAt[]): string {
    return `<div class="wx-list">${points.map((w) => {
      const [text, icon] = describe(w.code)
      const rain = w.precipProbability != null ? ` · lluvia ${w.precipProbability} %` : ''
      return `<div class="wx-row"><span class="wx-icon">${icon}</span>
        <span><small>${hhmm(w.timeMs)} · km ${Math.round(w.sample.distanceMeters / 1000)}${w.sample.ele != null ? ` · ${Math.round(w.sample.ele)} m` : ''}</small>
        <b>${text}</b><small>Viento ${Math.round(w.windKmh)} km/h, rachas ${Math.round(w.gustKmh)}${rain}</small></span>
        <b class="wx-temp">${Math.round(w.tempC)}°</b></div>`
    }).join('')}</div>`
  }

  private table(rows: DetailRow[]): string {
    const head = '<tr><th>Hora</th><th>Km</th><th></th><th>Temp.</th><th>Sensación</th><th>Lluvia</th><th>Viento</th><th>UV</th></tr>'
    const body = rows.map((r) => {
      const [, icon] = describe(r.code)
      const rain = r.precipMm >= 0.1 ? `${r.precipMm.toFixed(1)} mm` : `${r.precipProbability ?? 0} %`
      return `<tr${r.isDay ? '' : ' class="night"'}><td>${hhmm(r.timeMs)}</td><td>${(r.distanceMeters / 1000).toLocaleString(ES, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}</td><td>${r.isDay ? icon : '🌙'}</td>` +
        `<td>${Math.round(r.tempC)}°</td><td>${Math.round(r.feelsC)}°</td><td>${rain}</td>` +
        `<td>${Math.round(r.windKmh)}–${Math.round(r.gustKmh)}</td><td>${r.uv != null ? Math.round(r.uv) : '—'}</td></tr>`
    }).join('')
    return `<div class="wx-table"><table>${head}${body}</table></div>`
  }
}
