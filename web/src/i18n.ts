// Idioma de Trazo web: español o inglés. Se elige el del navegador (español
// si alguno de sus idiomas lo es; si no, inglés) y se puede cambiar arriba.
// Los textos van en pares L('español', 'English') junto a donde se usan.

export type Lang = 'es' | 'en'
const KEY = 'trazo-lang'

function detect(): Lang {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'es' || saved === 'en') return saved
  } catch { /* sin almacenamiento */ }
  const langs = typeof navigator !== 'undefined' ? (navigator.languages?.length ? navigator.languages : [navigator.language]) : []
  return langs.some((l) => l?.toLowerCase().startsWith('es')) ? 'es' : 'en'
}

let current: Lang = detect()

export const lang = (): Lang => current

/** Para los tests y para el selector (que además recarga la página). */
export function setLang(l: Lang) {
  current = l
}

/** Cambia el idioma, lo recuerda y recarga: así todo sale ya en el nuevo. */
export function switchLang(l: Lang) {
  try { localStorage.setItem(KEY, l) } catch { /* sin almacenamiento */ }
  location.reload()
}

/** El texto en el idioma actual. */
export const L = (es: string, en: string): string => (current === 'es' ? es : en)

/** Locale para números y fechas. */
export const locale = (): string => (current === 'es' ? 'es-ES' : 'en-GB')

/**
 * Textos fijos de index.html: los elementos llevan el inglés en data-en (y
 * data-en-placeholder, data-en-aria o data-en-title para atributos).
 */
export function translatePage(root: ParentNode = document) {
  document.documentElement.lang = current
  if (current === 'es') return
  root.querySelectorAll<HTMLElement>('[data-en]').forEach((el) => { el.textContent = el.dataset.en! })
  root.querySelectorAll<HTMLElement>('[data-en-placeholder]').forEach((el) => el.setAttribute('placeholder', el.dataset.enPlaceholder!))
  root.querySelectorAll<HTMLElement>('[data-en-aria]').forEach((el) => el.setAttribute('aria-label', el.dataset.enAria!))
  root.querySelectorAll<HTMLElement>('[data-en-title]').forEach((el) => el.setAttribute('title', el.dataset.enTitle!))
}

/** "15 %" en español, "15%" en inglés. */
export const percent = (n: number | string): string => (current === 'es' ? `${n} %` : `${n}%`)
