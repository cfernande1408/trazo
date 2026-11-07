/** Colores del mapa por estación, los mismos que en la app (Season.kt). */
export interface Season {
  label: string
  background: string
  grass: string
  wood: string
  park: string
  pineLight: string
  pineShade: string
  broadleaf: string
  broadleafShade: string
  blossom: string | null
  snow: boolean
  broadleafShare: number
  tip: string
}

const s = (
  label: string, background: string, grass: string, wood: string, park: string,
  pineLight: string, pineShade: string, broadleaf: string, broadleafShade: string,
  blossom: string | null, snow: boolean, broadleafShare: number, tip: string,
): Season => ({ label, background, grass, wood, park, pineLight, pineShade, broadleaf, broadleafShade, blossom, snow, broadleafShare, tip })

export const SEASONS = {
  spring: s('Primavera', '#EEF0DF', '#D2E7AE', '#B3D78B', '#C9E3A6', '#74B04A', '#4E8A2E', '#8CCB5E', '#5FA03A', '#F4A7C0', false, 0.35,
    'Primavera: barro tras las lluvias y arroyos crecidos. Ojo con los vados.'),
  summer: s('Verano', '#F1EDD8', '#E4E0B0', '#B2CA8A', '#D3DCA8', '#5E9340', '#3F6F2A', '#6FA048', '#4D7F31', null, false, 0.3,
    'Verano: sal temprano, lleva agua de sobra y respeta las restricciones por riesgo de incendio.'),
  autumn: s('Otoño', '#F0EADB', '#E5D7AA', '#C7CD93', '#DCD3A4', '#6E9F48', '#4F7F33', '#E08A2E', '#B5532A', null, false, 0.45,
    'Otoño: hojas y raíces mojadas resbalan y anochece pronto. Lleva luz.'),
  winter: s('Invierno', '#F2F4F2', '#E3E8DE', '#C5D3BD', '#D9E2D3', '#5D8A5A', '#3D6B44', '#9C7E62', '#7A5F48', null, true, 0.0,
    'Invierno: hielo en las umbrías y nieve en las cumbres. Mira el parte antes de salir.'),
}

/** Por fecha: equinoccios y solsticios redondeados al día. */
export function seasonOf(date: Date): Season {
  const m = date.getMonth() + 1
  const d = date.getDate()
  if ((m === 12 && d >= 21) || m <= 2 || (m === 3 && d < 20)) return SEASONS.winter
  if (m === 3 || m === 4 || m === 5 || (m === 6 && d < 21)) return SEASONS.spring
  if (m === 6 || m === 7 || m === 8 || (m === 9 && d < 23)) return SEASONS.summer
  return SEASONS.autumn
}
