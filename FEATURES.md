# Funciones de Trazo

Todo lo que hace la app, una línea por cosa. Lo que aún falta está en la
[hoja de ruta del README](README.md#hoja-de-ruta).

## Biker o senderista

- La primera vez que abres la app eliges **Soy biker** o **Soy senderista**
- El chip de arriba en *Mis rutas* cambia de una a otra cuando quieras
- Tema propio de cada actividad: lima y verde para la bici, tierra y ámbar a pie
- *Explorar* enseña rutas MTB y ciclistas o senderos GR/PR según la actividad
- Iconos distintos en el mapa: bici verde (MTB), bici verde azulada (ciclista) y excursionista ámbar
- Aviso de desvío a 40 m en bici y a 30 m a pie, con más margen de tiempo a pie
- Al grabar, a pie cuenta como movimiento aunque subas muy despacio
- Filtro de saltos del GPS según la actividad (25 m/s en bici, 5 m/s a pie)
- Tiempo estimado en bici y por el método MIDE a pie
- *Crear ruta* va por pistas ciclables en bici y por senderos de montaña a pie
- Colores de velocidad del recorrido grabado adaptados a cada actividad
- Los textos dicen «salida» o «excursión» según el caso

## Según la estación

- La estación va con el calendario y se puede fijar a mano (Automática, Primavera, Verano, Otoño, Invierno)
- Colores del mapa por estación: fondo, prados, bosques y parques
- Árboles del mapa por estación: pinos con frondosas verdes, en flor en primavera, naranjas en otoño y pinos nevados en invierno
- Los mismos árboles en la vista 3D
- Color de acento del tema por estación: flor, cielo, hojas de otoño o hielo
- Consejo de temporada en el resumen de cada ruta (barro y arroyos, calor e incendios, hojas mojadas y anochecer temprano, hielo y nieve)

## Mis rutas

- Importa GPX con *Abrir con…*, *Compartir → Trazo* o desde el selector de archivos
- Lee `trk`, `rte` y `wpt`, con CDATA y entidades, y quita el «Wikiloc - » del nombre
- Vista previa con el mapa topo y la ruta dibujada encima
- Distancia, desnivel positivo y negativo (con filtro de ruido del GPS) y tiempo estimado
- Pestaña **Perfil**: perfil de altitud que se recorre con el dedo, con altitud mínima y máxima
- Pestaña **Puntos**: los puntos que marcó el autor, con su descripción
- Pestaña **Tiempo**: previsión a lo largo de la ruta (ver más abajo)
- Pestaña **Comentarios**: la descripción del autor y un botón a los comentarios en Wikiloc, o para buscar la ruta allí
- **Guardar en tus rutas**: la ruta se queda en el móvil y funciona sin conexión
- Lista de *Tus rutas guardadas* para abrirlas o borrarlas

## Sin conexión

- **Descargar mapa para usar sin conexión** en el resumen de cada ruta: la zona de la ruta y 2 km alrededor
- Incluye mapa, relieve y, si quieres, el topo IGN; dice cuánto ocupará antes de empezar
- Barra de progreso; si se corta la red, la descarga sigue sola al volver
- Sin cobertura, el mapa y la navegación usan lo descargado sin hacer nada
- Lista de *Mapas sin conexión* en *Mis rutas*, con lo que ocupa cada uno y para borrarlos

## Pendiente y dificultad

- Tarjeta de **pendiente**: rampa más dura y en qué km, metros por encima del 10 % y del 15 % y bajada más fuerte
- Capa **Pendiente** en el mapa de la ruta: tramos de 100 m coloreados de verde (< 5 %) a rojo (> 20 %), con leyenda
- **Dificultad técnica de OpenStreetMap** junto al track: escala MTB (S0–S6) en bici y SAC (T1–T6) a pie
- Grado principal, el tramo más difícil y cuántos km van por cada grado, para cualquier GPX (no solo rutas de OSM)

## El tiempo en la ruta

- Resumen en una línea arriba del todo en cada ruta (peor tiempo, temperaturas y primer aviso); al tocarlo se abre la pestaña Tiempo
- Eliges el día (hasta 7 días vista) y la hora de salida
- La previsión sale en varios puntos de la ruta, uno cada ~5 km
- En cada punto, el tiempo a la hora estimada a la que pasarás por él (según bici o a pie y el desnivel)
- Temperatura a la altitud real de cada punto, no la del pueblo de abajo
- Cielo, probabilidad de lluvia, viento y rachas
- Avisos con el km donde empiezan: tormenta, nieve, bajo cero (hielo), rachas fuertes, lluvia probable y calor

## Buscador

- Barra de búsqueda en *Explorar*
- Busca lugares (picos, pueblos, puertos, parkings) y te lleva allí con un marcador
- Busca rutas y senderos por nombre o referencia (p. ej. «PR-M 13») y los dibuja al tocarlos
- Acerca el mapa lo justo para que carguen las rutas de alrededor
- Botón **Lista** con las rutas que se ven, de la más cercana al centro a la más lejana

## Explorar

- Mapa de tu zona, centrado en tu posición (o en Madrid si no hay ubicación)
- Un icono por cada ruta de OpenStreetMap; se van cargando al mover el mapa
- Filtro *Solo MTB* en bici
- Rutas desde Waymarked Trails (casi instantáneo), con Overpass de respaldo en varios servidores
- Reintenta solo si se corta la conexión, y tocando el aviso si falla
- Al tocar una ruta se dibuja primero el trazado rápido y luego el exacto de OSM
- Tarjeta con distancia, desnivel y perfil (altitud de Open-Meteo), *Abrir en Trazo* y enlace a OSM

## Wikiloc

- Navegador de Wikiloc dentro de la app
- El GPX que descargas ahí se importa solo
- Trazo no hace *scraping*: solo lee lo que tú descargas

## Capas del mapa

- Botón de capas a la derecha en todos los mapas, como en Google Maps
- **Tipo de mapa**: Trazo (ilustrado), **Topo IGN** (el mapa topográfico oficial de España, con curvas de nivel) y **Satélite** (ortofoto PNOA del IGN, con los caminos y nombres de Trazo encima)
- **Detalles**: sombreado del relieve, árboles y una capa de rutas MTB o senderos señalizados de Waymarked Trails
- Lo que eliges vale para todos los mapas, incluida la vista 3D (satélite en 3D), y se recuerda

## Mapa y navegación

- Mapa topográfico propio: teselas vectoriales de OpenFreeMap, sombreado de relieve y árboles dibujados en los bosques
- Caminos, pistas, carreteras, ríos, picos con altitud y pueblos
- Ruta con borde blanco, puntos del autor, inicio y final
- Al recorrer el perfil con el dedo, un punto marca en el mapa dónde estás
- *¿Desde dónde empiezas?*: desde el inicio, desde el punto de la ruta más cercano o desde un punto del autor (el parking)
- *Cómo llegar* al inicio con Google Maps
- Posición con el GPS de Android (sin Google Play Services), usando varios proveedores y su precisión
- Aviso con vibración larga y pitido al salirte de la ruta (se repite si sigues fuera) y vibración corta al volver
- Tarjeta con la subida en curso, la próxima subida y lo que queda
- La navegación sigue con la pantalla apagada y se graba como salida
- La pantalla no se apaga sola mientras navegas

## Como un GPS profesional

- **Subidas en directo** (como el ClimbPro de Garmin): «Subida 2 de 4 · 9 %», lo que queda de distancia y desnivel y el perfil de esa subida con tu posición
- **Avisos por voz** en español: «En 150 metros, gira a la derecha», «Empieza una subida de 1,2 kilómetros al 8 por ciento», «Fuente en 200 metros», «Te has salido de la ruta»
- Los giros salen del propio track (cambios de rumbo de más de 45°); las zetas de sendero se avisan una sola vez como «zona de curvas cerradas»
- A pie se avisa más cerca (60 m y 15 m) que en bici (150 m y 40 m)
- Botón para silenciar la voz, que se recuerda
- **Luz restante**: «Te sobran 45 min de luz» o «Anochece 20 min antes de terminar» (en rojo), con la puesta de sol de donde estás y lo que te queda a tu ritmo real; aviso por voz si vas justo
- En el resumen de la ruta: a qué hora anochece y a qué hora terminarías si sales ya
- **Agua y refugios por delante**: fuentes, manantiales y refugios de OpenStreetMap a menos de 150 m de la ruta, en el mapa, en la pestaña *Puntos* con su km y en navegación («Fuente en 1,2 km»)
- Las fuentes marcadas como no potables no salen

## Vista 3D

- Relieve en 3D con la exageración ajustada a cada ruta (más en rutas suaves, menos en sierra)
- Botones + y − para exagerar más o menos el relieve; se recuerda para la próxima vez
- Cielo y niebla en el horizonte
- Casas en volumen con su altura de OpenStreetMap
- Bosques con árboles de pie en el borde y por dentro, sin tapar el relieve
- Recorrido virtual: la cámara vuela la ruta siguiendo al ciclista
- Botón para parar y ver la ruta entera
- Créditos del mapa recogidos en un botón (i)

## Crear ruta

- Tocas puntos en el mapa y BRouter traza la ruta por caminos
- Perfil `mtb` en bici y `hiking-mountain` a pie
- Puntos numerados y una guía recta mientras se calcula
- Distancia, desnivel, tiempo estimado y perfil mientras la dibujas
- Deshacer, cerrar en circular y borrar todo
- Guardar con nombre en *Tus rutas* y abrirla como cualquier otra
- Avisos claros si un punto está lejos de cualquier camino o la ruta es demasiado larga

## Grabar salidas

- Graba con o sin ruta, con la pantalla apagada (servicio en primer plano con notificación)
- Pausa y reanudación
- Distancia, tiempo total y en movimiento, velocidad media y máxima, desnivel
- Aviso en la barra de abajo mientras grabas, para volver a la grabación
- *Mis salidas* / *Mis excursiones* con el historial
- Detalle con el recorrido coloreado por velocidad y su leyenda
- Exportar a GPX para Strava, Wikiloc u otras apps
- Usar una salida grabada como ruta
- Borrar salidas

## Diseño

- Material Design 3 con Jetpack Compose
- Tema oscuro y «modo sol» claro de alto contraste para leer a pleno sol
- Pantalla de borde a borde

## Privacidad

- Sin cuentas, sin anuncios y sin claves de API
- Sin servidor propio: rutas, salidas y ajustes se quedan en el móvil
- Sin Google Play Services
