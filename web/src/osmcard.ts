import { stats, type Activity, type Segments } from './geo'
import { friendly, overpass } from './net'
import { gradeLabel, osmWaysQuery, parseWays, summarizeDifficulty, summarizeSurface, SURFACE, type OsmWay, type SurfaceKind } from './surface'

const ES = 'es-ES'
const km = (m: number) => `${(m / 1000).toLocaleString(ES, { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`
/** A partir de aquí se avisa de que Overpass va lento, como en la app. */
const SLOW_MS = 12_000

/**
 * Dificultad (MTB o SAC) y por dónde va (asfalto, pista, sendero), con una
 * sola consulta a Overpass por ruta, como RouteOsm en la app.
 */
export class OsmCard {
  private load?: AbortController
  private key = ''
  private ways: OsmWay[] | null = null
  private error: string | null = null
  private slow = false
  private segments: Segments = []
  private activity: Activity = 'bike'

  constructor(private diffEl: HTMLElement, private surfEl: HTMLElement) {}

  set(segments: Segments, activity: Activity, ready: boolean) {
    this.segments = segments
    this.activity = activity
    if (!segments.length || !ready) {
      this.diffEl.innerHTML = ''
      this.surfEl.innerHTML = ''
      return
    }
    const s = stats(segments)
    const first = segments[0][0], lastSeg = segments[segments.length - 1], last = lastSeg[lastSeg.length - 1]
    const key = [first.lat, first.lon, last.lat, last.lon, Math.round(s.distanceMeters)].join(',')
    if (key === this.key && (this.ways || this.load)) return this.render()
    this.key = key
    this.fetch()
  }

  clear() {
    this.load?.abort()
    this.load = undefined
    this.key = ''
    this.ways = null
    this.diffEl.innerHTML = ''
    this.surfEl.innerHTML = ''
  }

  private async fetch() {
    this.load?.abort()
    const ctl = new AbortController()
    this.load = ctl
    this.ways = null
    this.error = null
    this.slow = false
    this.render()
    const slowTimer = setTimeout(() => { if (this.load === ctl) { this.slow = true; this.render() } }, SLOW_MS)
    try {
      const json = await overpass(osmWaysQuery(this.segments), ctl.signal)
      if (ctl.signal.aborted) return
      this.ways = parseWays(json)
    } catch (e) {
      if (ctl.signal.aborted) return
      this.error = friendly(e)
    } finally {
      clearTimeout(slowTimer)
      if (this.load === ctl) this.load = undefined
    }
    this.render()
  }

  private render() {
    const mtb = this.activity === 'bike'
    const title = mtb ? 'Dificultad técnica (escala MTB)' : 'Dificultad (escala SAC)'
    const waiting = `<p class="muted small"><span class="spinner"></span>Mirando los caminos en OpenStreetMap…${this.slow ? ' Overpass va lento ahora; sigue intentándolo.' : ''}</p>`
    if (this.error) {
      const err = `<p class="error small">No se pudieron mirar los caminos: ${this.error}</p><button type="button" class="btn small" data-retry>Reintentar</button>`
      this.diffEl.innerHTML = `<h3>${title}</h3>${err}`
      this.surfEl.innerHTML = ''
      this.diffEl.querySelector('[data-retry]')?.addEventListener('click', () => this.fetch())
      return
    }
    if (!this.ways) {
      this.diffEl.innerHTML = `<h3>${title}</h3>${waiting}`
      this.surfEl.innerHTML = `<h3>Por dónde va</h3>${waiting}`
      return
    }
    this.diffEl.innerHTML = `<h3>${title}</h3>${this.difficulty(mtb)}`
    this.surfEl.innerHTML = `<h3>Por dónde va</h3>${this.surface()}`
  }

  private difficulty(mtb: boolean): string {
    const s = summarizeDifficulty(this.segments, this.ways!, mtb)
    if (s.mainGrade == null) return '<p class="small">Los caminos de esta ruta no tienen dificultad marcada en OpenStreetMap.</p>'
    const [mainCode, mainText] = gradeLabel(s.mainGrade, mtb)
    const [hardCode, hardText] = gradeLabel(s.maxGrade!, mtb)
    const top = Math.max(...s.byGrade.map(([, m]) => m))
    const rows = s.byGrade.map(([g, m]) =>
      `<div class="bar-row"><b>${gradeLabel(g, mtb)[0]}</b><span class="bar"><i style="width:${(m / top) * 100}%;background:${gradeColor(g, mtb)}"></i></span><span>${km(m)}</span></div>`).join('')
    return `<p class="small">Sobre todo ${mainCode} (${mainText})${s.maxGrade !== s.mainGrade ? `. Lo más difícil: ${hardCode}, ${hardText}.` : '.'}</p>
      <div class="bars">${rows}</div>
      <p class="muted small">Marcada en ${km(s.ratedMeters)} de ${km(s.routeMeters)}. El resto de caminos no tiene dato.</p>`
  }

  private surface(): string {
    const s = summarizeSurface(this.segments, this.ways!)
    if (s.knownMeters <= 0) return '<p class="small">La ruta no coincide con caminos de OpenStreetMap.</p>'
    const kinds = (Object.keys(SURFACE) as SurfaceKind[]).filter((k) => s.meters[k])
    const share = (k: SurfaceKind) => (s.meters[k]! / s.knownMeters) * 100
    const strip = `<div class="strip">${kinds.map((k) => `<i style="width:${share(k)}%;background:${SURFACE[k].color}" title="${SURFACE[k].label}"></i>`).join('')}</div>`
    const rows = kinds.map((k) =>
      `<span><i class="dot" style="background:${SURFACE[k].color}"></i>${SURFACE[k].label}</span><b>${km(s.meters[k]!)} · ${Math.round(share(k))} %</b>`).join('')
    const unknown = s.routeMeters - s.knownMeters
    return `${strip}<div class="rows">${rows}</div>` +
      (unknown > 200 ? `<p class="muted small">Sin dato en ${km(unknown)}: por ahí no hay camino en OpenStreetMap.</p>` : '')
  }
}

/** De verde (fácil) a rojo (muy difícil), igual para las dos escalas. */
function gradeColor(g: number, mtb: boolean): string {
  const colors = ['#2E7D32', '#7CB342', '#F9A825', '#EF6C00', '#C62828', '#6A1B9A', '#37474F']
  return colors[Math.min(6, mtb ? g : g - 1)]
}
