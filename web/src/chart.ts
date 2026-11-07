import { bucket, gradeAt, SLOPE_COLORS, type Grade, type ProfilePoint } from './geo'

/**
 * Perfil de altitud en un canvas, coloreado por pendiente como en el mapa.
 * Al pasar el ratón (o el dedo) avisa de la distancia para marcarla en el mapa.
 */
export class ProfileChart {
  private prof: ProfilePoint[] = []
  private gs: Grade[] = []
  private hover: number | null = null
  private readonly pad = { l: 54, r: 8, t: 8, b: 20 }

  constructor(private canvas: HTMLCanvasElement, onHover: (meters: number | null) => void) {
    const move = (e: PointerEvent) => {
      const r = canvas.getBoundingClientRect()
      const m = this.metersAt(e.clientX - r.left, r.width)
      this.hover = m
      this.draw()
      onHover(m)
    }
    canvas.addEventListener('pointermove', move)
    canvas.addEventListener('pointerdown', move)
    canvas.addEventListener('pointerleave', () => {
      this.hover = null
      this.draw()
      onHover(null)
    })
    new ResizeObserver(() => this.draw()).observe(canvas)
  }

  set(prof: ProfilePoint[], gs: Grade[]) {
    this.prof = prof
    this.gs = gs
    this.hover = null
    this.draw()
  }

  private get total() {
    return this.prof.length ? this.prof[this.prof.length - 1].distanceMeters : 0
  }

  private metersAt(x: number, width: number): number | null {
    if (!this.prof.length) return null
    const w = width - this.pad.l - this.pad.r
    return Math.min(this.total, Math.max(0, ((x - this.pad.l) / w) * this.total))
  }

  draw() {
    const c = this.canvas
    const dpr = window.devicePixelRatio || 1
    const W = c.clientWidth, H = c.clientHeight
    if (!W || !H) return
    c.width = Math.round(W * dpr)
    c.height = Math.round(H * dpr)
    const g = c.getContext('2d')!
    g.setTransform(dpr, 0, 0, dpr, 0, 0)
    g.clearRect(0, 0, W, H)
    if (this.prof.length < 2) return

    const css = getComputedStyle(document.documentElement)
    const ink = css.getPropertyValue('--muted').trim() || '#666'
    const grid = css.getPropertyValue('--outline').trim() || '#999'
    const { l, r, t, b } = this.pad
    const w = W - l - r, h = H - t - b
    const eles = this.prof.map((p) => p.ele)
    let lo = Math.min(...eles), hi = Math.max(...eles)
    // Margen y escala mínima: un perfil casi llano no parece una montaña
    const span = Math.max(hi - lo, 60)
    lo = Math.floor((lo - span * 0.08) / 10) * 10
    hi = lo + span * 1.16
    const X = (m: number) => l + (m / this.total) * w
    const Y = (e: number) => t + h - ((e - lo) / (hi - lo)) * h

    // Rejilla: cuatro altitudes y los km
    g.font = '11px Inter, system-ui, sans-serif'
    g.fillStyle = ink
    g.strokeStyle = grid
    g.globalAlpha = 0.35
    g.lineWidth = 1
    g.textAlign = 'right'
    g.textBaseline = 'middle'
    // Altitudes redondas (cada 10, 20, 50, 100… m), unas cuatro líneas
    const eStep = [10, 20, 25, 50, 100, 200, 250, 500, 1000].find((s) => (hi - lo) / s <= 4) ?? 2000
    for (let e = Math.ceil(lo / eStep) * eStep; e <= hi; e += eStep) {
      g.beginPath(); g.moveTo(l, Y(e)); g.lineTo(W - r, Y(e)); g.stroke()
      g.globalAlpha = 1
      g.fillText(`${e} m`, l - 6, Y(e))
      g.globalAlpha = 0.35
    }
    g.globalAlpha = 1
    g.textAlign = 'center'
    g.textBaseline = 'top'
    const km = this.total / 1000
    const step = [1, 2, 5, 10, 20, 50].find((s) => km / s <= 8) ?? 100
    for (let k = 0; k <= km + 1e-6; k += step) g.fillText(`${k} km`, X(k * 1000), H - b + 4)

    // Relleno por tramos de pendiente
    for (let i = 1; i < this.prof.length; i++) {
      const a = this.prof[i - 1], z = this.prof[i]
      const pct = gradeAt(this.gs, (a.distanceMeters + z.distanceMeters) / 2) ?? 0
      g.fillStyle = SLOPE_COLORS[bucket(pct)]
      g.globalAlpha = 0.55
      g.beginPath()
      g.moveTo(X(a.distanceMeters), Y(lo))
      g.lineTo(X(a.distanceMeters), Y(a.ele))
      g.lineTo(X(z.distanceMeters), Y(z.ele))
      g.lineTo(X(z.distanceMeters), Y(lo))
      g.closePath()
      g.fill()
    }
    g.globalAlpha = 1
    g.strokeStyle = css.getPropertyValue('--on-bg').trim() || '#222'
    g.lineWidth = 1.5
    g.beginPath()
    this.prof.forEach((p, i) => (i ? g.lineTo(X(p.distanceMeters), Y(p.ele)) : g.moveTo(X(p.distanceMeters), Y(p.ele))))
    g.stroke()

    if (this.hover != null) {
      const x = X(this.hover)
      g.strokeStyle = css.getPropertyValue('--primary').trim()
      g.lineWidth = 2
      g.beginPath(); g.moveTo(x, t); g.lineTo(x, t + h); g.stroke()
    }
  }
}
