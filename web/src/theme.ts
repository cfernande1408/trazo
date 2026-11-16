// Tema de colores de Trazo web: el de la app (Material 3, lima y tierra)
// o Catppuccin (Latte de día, Mocha de noche). Catppuccin es de prueba;
// se cambia con el botón de la paleta, arriba, y se recuerda.

export type Theme = 'trazo' | 'catppuccin'
const KEY = 'trazo-theme'
export const DEFAULT_THEME: Theme = 'catppuccin'

export function savedTheme(): Theme {
  try {
    const t = localStorage.getItem(KEY)
    if (t === 'trazo' || t === 'catppuccin') return t
  } catch { /* sin almacenamiento */ }
  return DEFAULT_THEME
}

export function applyTheme(t: Theme, root: HTMLElement = document.documentElement) {
  root.dataset.theme = t
}

export function setTheme(t: Theme) {
  try { localStorage.setItem(KEY, t) } catch { /* sin almacenamiento */ }
  applyTheme(t)
}

export const nextTheme = (t: Theme): Theme => (t === 'trazo' ? 'catppuccin' : 'trazo')
