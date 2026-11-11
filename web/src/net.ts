import { OVERPASS } from './osm'
import { L } from './i18n'

/** Mensajes de error entendibles, como Http.friendly en la app. */
export function friendly(e: unknown): string {
  if (e instanceof DOMException && e.name === 'AbortError') return L('Cancelado', 'Cancelled')
  if (e instanceof TypeError) return L('Sin conexión o el servidor no contesta.', 'No connection or the server is not answering.')
  if (e instanceof Error && e.message) return e.message
  return L('Algo ha fallado.', 'Something went wrong.')
}

export class BusyError extends Error {}

/** GET de JSON con tiempo máximo. */
export async function getJson<T = unknown>(url: string, signal?: AbortSignal, timeoutMs = 20_000): Promise<T> {
  const res = await fetch(url, { signal: anySignal(signal, AbortSignal.timeout(timeoutMs)) })
  if (res.status === 429 || res.status === 503 || res.status === 504) throw new BusyError(L('El servidor está saturado. Prueba en un momento.', 'The server is busy. Try again in a moment.'))
  if (!res.ok) throw new Error(L(`El servidor contestó ${res.status}`, `The server answered ${res.status}`))
  return (await res.json()) as T
}

/** Tiempo que se da a un servidor de Overpass antes de probar también el siguiente. */
const HEDGE_MS = 4_000

/**
 * Overpass con espejos, como en la app: se lanza el primero y, si tarda o
 * falla, también el siguiente; gana la primera respuesta buena y las demás
 * se cancelan.
 */
export function overpass<T = unknown>(query: string, signal?: AbortSignal): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const controllers: AbortController[] = []
    const failures: unknown[] = []
    let done = false
    let next = 0
    let timer: ReturnType<typeof setTimeout> | undefined
    const finish = () => {
      done = true
      clearTimeout(timer)
      controllers.forEach((c) => c.abort())
    }
    signal?.addEventListener('abort', () => { if (!done) { finish(); reject(new DOMException(L('Cancelado', 'Cancelled'), 'AbortError')) } })
    const launch = () => {
      if (done || next >= OVERPASS.length) return
      const endpoint = OVERPASS[next++]
      const c = new AbortController()
      controllers.push(c)
      clearTimeout(timer)
      timer = setTimeout(launch, HEDGE_MS)
      fetch(endpoint, {
        method: 'POST',
        body: new URLSearchParams({ data: query }),
        signal: anySignal(c.signal, AbortSignal.timeout(60_000)),
      })
        .then(async (res) => {
          if (res.status === 429 || res.status === 504) throw new BusyError('busy')
          if (!res.ok) throw new Error(L(`Overpass contestó ${res.status}`, `Overpass answered ${res.status}`))
          return (await res.json()) as T
        })
        .then((json) => { if (!done) { finish(); resolve(json) } })
        .catch((e) => {
          if (done) return
          failures.push(e)
          if (failures.length === OVERPASS.length) {
            finish()
            reject(failures.some((f) => f instanceof BusyError)
              ? new BusyError(L('Los servidores de rutas de OpenStreetMap están saturados. Prueba en un par de minutos.', 'The OpenStreetMap route servers are busy. Try again in a couple of minutes.'))
              : new Error(friendly(e)))
          } else launch()
        })
    }
    launch()
  })
}

function anySignal(...signals: (AbortSignal | undefined)[]): AbortSignal {
  const list = signals.filter((s): s is AbortSignal => !!s)
  if ('any' in AbortSignal) return AbortSignal.any(list)
  const c = new AbortController()
  for (const s of list) s.addEventListener('abort', () => c.abort(s.reason))
  return c.signal
}
