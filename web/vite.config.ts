import { defineConfig } from 'vitest/config'

// Se publica en GitHub Pages: https://cfernande1408.github.io/trazo/
export default defineConfig({
  base: './',
  server: {
    // El estilo del mapa se importa directamente de la app Android (app/src/main/assets)
    fs: { allow: ['..'] },
  },
  build: { chunkSizeWarningLimit: 1500 },
  test: {
    environment: 'jsdom',
  },
})
