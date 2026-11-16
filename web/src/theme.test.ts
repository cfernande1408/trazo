import { beforeEach, describe, expect, it } from 'vitest'
import { DEFAULT_THEME, applyTheme, nextTheme, savedTheme, setTheme } from './theme'

describe('tema', () => {
  beforeEach(() => localStorage.clear())

  it('sin nada guardado usa el tema por defecto', () => {
    expect(savedTheme()).toBe(DEFAULT_THEME)
  })

  it('ignora valores guardados que no son un tema', () => {
    localStorage.setItem('trazo-theme', 'dracula')
    expect(savedTheme()).toBe(DEFAULT_THEME)
  })

  it('recuerda el tema elegido y lo pone en la raiz', () => {
    setTheme('trazo')
    expect(savedTheme()).toBe('trazo')
    expect(document.documentElement.dataset.theme).toBe('trazo')
    setTheme('catppuccin')
    expect(savedTheme()).toBe('catppuccin')
  })

  it('alterna entre los dos temas', () => {
    expect(nextTheme('trazo')).toBe('catppuccin')
    expect(nextTheme('catppuccin')).toBe('trazo')
    const el = document.createElement('div')
    applyTheme('trazo', el)
    expect(el.dataset.theme).toBe('trazo')
  })
})
