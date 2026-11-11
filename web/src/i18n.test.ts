import { afterEach, describe, expect, it } from 'vitest'
import { L, locale, percent, setLang } from './i18n'

describe('i18n', () => {
  afterEach(() => setLang('es'))
  it('cada texto en su idioma', () => {
    expect(L('Hola', 'Hello')).toBe('Hola')
    expect(percent(15)).toBe('15 %')
    expect(locale()).toBe('es-ES')
    setLang('en')
    expect(L('Hola', 'Hello')).toBe('Hello')
    expect(percent(15)).toBe('15%')
    expect(locale()).toBe('en-GB')
  })
})
