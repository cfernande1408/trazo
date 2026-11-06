# Trazo

Navegación para bici de montaña a partir de rutas GPX (por ejemplo, las que
descargas de Wikiloc).

## Módulos

- `core/gpx` — Kotlin puro, sin dependencias: parser GPX (`trk`, `rte`, `wpt`)
  y estadísticas (distancia, desnivel con histéresis anti-ruido).
- `app` — Android, Jetpack Compose + Material 3 (tema oscuro y "modo sol").
  Importa GPX con "Abrir con…" o "Compartir → Trazo".

## Comandos

```bash
./gradlew :core:gpx:test        # tests del parser
./gradlew :app:assembleDebug    # APK de debug
./gradlew :app:installDebug     # instalar en el móvil conectado
```

## Probar la importación

```bash
adb push core/gpx/src/test/resources/wikiloc_sample.gpx /sdcard/Download/
```

Luego abre el archivo desde el gestor de archivos y elige Trazo.
