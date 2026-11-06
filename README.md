# Trazo

App Android para salir al monte en **bici de montaña o a pie**: importa
rutas GPX (por ejemplo, las que descargas de Wikiloc), explora las rutas
de OpenStreetMap de tu zona, crea las tuyas y navégalas con avisos de
desvío, perfil de desnivel y vista 3D.

Kotlin · Jetpack Compose · Material Design 3 · MapLibre

## Qué hace

- **Biker o senderista.** Se elige al abrir la app por primera vez y lo
  adapta todo: tema, rutas que se muestran, umbrales de navegación,
  estadísticas y tiempo estimado (MIDE en senderismo).
- **Mis rutas.** Importa GPX con *Abrir con…* o *Compartir → Trazo*.
  Resumen con mapa topo, distancia, desnivel, tiempo estimado, perfil
  arrastrable, puntos del autor y enlace a los comentarios en Wikiloc.
  Biblioteca local de rutas guardadas.
- **Crear ruta.** Tocas puntos en el mapa y [BRouter](https://brouter.de)
  traza la ruta por pistas (perfil `mtb`) o senderos de montaña
  (`hiking-mountain`). Deshacer, cerrar circular y guardar.
- **Explorar.** Mapa de tu zona con un icono por cada ruta MTB/ciclista o
  sendero de OSM (vía [Waymarked Trails](https://waymarkedtrails.org),
  con Overpass de respaldo). Al tocarlo se dibuja y se puede abrir.
- **Wikiloc.** Navegador integrado: lo que descargas en GPX se importa
  solo. Trazo no hace *scraping* de Wikiloc.
- **Navegar.** Posición en el mapa, aviso con vibración al salirte de la
  ruta, próxima subida, empezar desde el inicio o desde un punto
  (parking) y *Cómo llegar* con Google Maps.
- **Vista 3D.** Relieve con exageración automática, edificios, pinos y
  recorrido virtual de la ruta.
- **Grabar salidas.** Servicio en primer plano (sigue con la pantalla
  apagada), velocidad, tiempo en movimiento, mapa coloreado por
  velocidad y exportación a GPX.

## Estructura

```
core/gpx/   Kotlin puro (sin dependencias de Android), con tests JUnit
  gpx/        parser y escritor GPX, estadísticas, perfil, GeoJSON
  gpx/nav/    navegador: emparejado con la ruta, desvíos, subidas
  gpx/osm/    Overpass, Waymarked Trails, unión de vías, altitud
  gpx/ride/   grabación y estadísticas de salidas
  gpx/plan/   BRouter (crear rutas)
  Activity.kt biker / senderista: umbrales, perfiles y tiempo estimado
app/        Android: Compose + Material 3
  importer/   Mis rutas
  explore/    Explorar y navegador de Wikiloc
  map/        mapa topo, navegación y vista 3D
  plan/       crear ruta y biblioteca de rutas
  ride/       grabar salidas y su historial
  ui/         tema, modo biker/senderista, gráficos
```

Toda la lógica que se puede probar sin un móvil vive en `core/gpx`.

## Requisitos

- Android Studio o JDK 17+ con el SDK de Android (compileSdk 35)
- Móvil con Android 8.0 (API 26) o superior

Crea `local.properties` con la ruta al SDK si no lo hace Android Studio:

```properties
sdk.dir=/ruta/a/Android/Sdk
```

## Comandos

```bash
./gradlew :core:gpx:test        # tests del núcleo
./gradlew :app:assembleDebug    # APK de debug
./gradlew :app:installDebug     # instalar en el móvil conectado
```

Para probar la importación sin Wikiloc:

```bash
adb push core/gpx/src/test/resources/wikiloc_sample.gpx /sdcard/Download/
```

y abre el archivo desde el gestor de archivos con Trazo.

## Servicios y datos

Trazo no usa claves de API ni cuentas. Todo son servicios abiertos:

| Uso | Servicio | Datos |
| --- | --- | --- |
| Mapa base | [OpenFreeMap](https://openfreemap.org) | © OpenStreetMap |
| Relieve y 3D | [Terrain Tiles en AWS](https://registry.opendata.aws/terrain-tiles/) | Mapzen / varias fuentes |
| Rutas de la zona | [Waymarked Trails](https://waymarkedtrails.org), [Overpass API](https://overpass-api.de) | © OpenStreetMap |
| Crear rutas | [BRouter](https://brouter.de) | © OpenStreetMap |
| Altitud | [Open-Meteo Elevation](https://open-meteo.com) | Copernicus DEM |
| Vista 3D | [MapLibre GL JS](https://maplibre.org) | — |

Datos de OpenStreetMap © colaboradores de OpenStreetMap, bajo licencia
[ODbL](https://www.openstreetmap.org/copyright).

Wikiloc es una marca de sus propietarios. Trazo no está afiliada a
Wikiloc: solo abre su web y lee los GPX que el usuario descarga.

## Estado

Proyecto personal en desarrollo (v0.1). La pestaña Comunidad está en
construcción.
