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
- **El icono de la app cambia con la estación**: cielo azul y árboles en flor en primavera, verde en verano, ocres y naranjas en otoño y sierra nevada en invierno (cambia al salir de la app)
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
- **Descargar cualquier zona** desde *Explorar*: lo que ves en el mapa, con aviso si es demasiado grande (más de 300 MB)
- Lista de *Mapas sin conexión* en *Mis rutas*, con lo que ocupa cada uno y para borrarlos

## Pendiente y dificultad

- Tarjeta de **pendiente**: rampa más dura y en qué km, metros por encima del 10 % y del 15 % y bajada más fuerte
- Capa **Pendiente** en el mapa de la ruta: tramos de 100 m coloreados de verde (< 5 %) a rojo (> 20 %), con leyenda
- **Dificultad técnica de OpenStreetMap** junto al track: escala MTB (S0–S6) en bici y SAC (T1–T6) a pie
- Grado principal, el tramo más difícil y cuántos km van por cada grado, para cualquier GPX (no solo rutas de OSM)
- **Por dónde va**: km y porcentaje por asfalto, pista, sendero y escaleras, con una barra de colores (como Komoot)
- Sale de la superficie y el tipo de cada camino de OpenStreetMap; los tramos sin camino en OSM se dicen aparte

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
- Posición con el GPS de Android (sin Google Play Services); la de la red (wifi y antenas) solo se usa hasta que coge el GPS o si se pierde más de 10 s, y su altitud nunca
- Aviso con vibración larga y pitido al salirte de la ruta (se repite si sigues fuera) y vibración corta al volver
- **Recalcula solo**: tras 20 s fuera de la ruta, busca el punto de la ruta por delante que te queda más cerca y calcula con BRouter el camino más corto hasta él
- El camino de vuelta sale en azul discontinuo y se navega con sus giros; al llegar, sigues con el resto de la ruta sin hacer nada
- Lo dice por voz («Ruta recalculada. Vuelves a la ruta en 350 metros») y se puede quitar si prefieres ir a tu aire
- Sin cobertura lo avisa y lo vuelve a probar cada minuto; *Volver al inicio* sigue funcionando sin conexión
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
- **Altímetro barométrico**: si el móvil tiene barómetro, la altitud y el desnivel de lo que grabas salen de la presión, que no da saltos como el GPS
- El GPS solo fija el nivel: al empezar, con la media de sus primeras lecturas, y luego poco a poco, así que también corrige la deriva cuando cambia el tiempo
- Al grabar pone «desnivel con barómetro» cuando está en marcha; sin barómetro se usa el GPS como siempre

## Comunidad (en preparación)

El código está listo, pero aún no hay servidor: en la app sale como «Falta configurar la comunidad».

- **Avisos en el mapa** de otros usuarios: hielo, nieve, barro, árbol caído, camino cortado, arroyo crecido, ganado o perros, batida de caza
- Se avisa con un botón en el mapa, en Explorar o en la pestaña Comunidad, en tu posición y con un detalle opcional
- Cada aviso caduca solo según el tipo (hielo 2 días, árbol caído 3 semanas…) y otros pueden decir «sigue ahí» (dura más) o «ya no está» (se oculta)
- Los avisos salen en el mapa de la ruta y en Explorar; tocándolos se ven y se votan
- Pestaña **Comunidad** con los avisos a unos 15 km de ti, del más cercano al más lejano
- **Valoraciones de rutas**: estrellas y comentarios de los usuarios de Trazo en la pestaña Comentarios; se juntan por ruta de Wikiloc, de OSM o por trazado
- Sin registro: un usuario anónimo por móvil; solo se publica lo que escribes y dónde está el aviso

## Entrenamiento

- **Sensores Bluetooth** (en pruebas) estándar de cualquier marca: banda de pulso, sensor de cadencia y potenciómetro (con su cadencia)
- Se buscan y emparejan desde el botón de sensores del mapa o al grabar, y se conectan solos al empezar
- Pulso, cadencia y potencia se graban en el GPX como las extensiones de Garmin, así que Strava los lee
- En cada salida: pulso medio y máximo, cadencia media (sin los ratos sin pedalear) y potencia media
- **Pantalla de datos a tu gusto**: seis cifras en la tarjeta de navegación; toca una para elegir entre quedan, por subir, recorrido, velocidad, media, tiempo en movimiento, altitud, pendiente, hora de llegada, luz, pulso, cadencia, potencia y compañero virtual
- **Compañero virtual**: si ya hiciste esa ruta, «Vs. anterior» dice cuánto vas por delante (+1:20) o por detrás (−0:45) de tu salida más reciente en el mismo punto

## Seguridad

- **Volver al inicio** durante la navegación: *por donde he venido* (la ruta al revés hasta el inicio, sin conexión) o *por el camino más corto* por pistas y senderos (BRouter); se dibuja en azul discontinuo y se navega como una ruta más
- Botón **SOS** en el mapa y al grabar: tu posición en grande en **UTM** (huso, X, Y) y en grados (decimal y grados-minutos-segundos), con altitud y precisión
- Llamar al 112 con un toque, mandar la posición por SMS al contacto de emergencia o compartirla por cualquier app
- El SMS lleva coordenadas, UTM, altitud, hora y un enlace al mapa, y se entiende sin tener Trazo
- **Detección de caídas** mientras grabas o navegas: golpe fuerte seguido de 20 s sin moverte; suena una alarma con «¿Estás bien?» y, si no respondes en 60 s, manda tu posición por SMS al contacto
- No salta si te levantas, sigues pedaleando o el GPS dice que avanzas
- Contacto de emergencia y detección de caídas se configuran en la hoja de SOS

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
- Deshacer, cerrar en circular, **ida y vuelta** por el mismo camino y borrar todo
- Tocar un punto para **moverlo** (y luego tocar dónde va) o **borrarlo**
- Tocar la ruta calculada para **insertar un punto en medio** de ese tramo
- Guardar con nombre en *Tus rutas* y abrirla como cualquier otra
- Avisos claros si un punto está lejos de cualquier camino o la ruta es demasiado larga

## Grabar salidas

- Graba con o sin ruta, con la pantalla apagada (servicio en primer plano con notificación)
- Pausa y reanudación
- **Marcar puntos** mientras grabas o navegas, con el botón de la chincheta: tipo (fuente, mirador, peligro, desvío, refugio, foto u otro), una nota y una foto
- La posición se coge al pulsar, así que puedes seguir mientras escribes; salen en el mapa al momento
- Se guardan en el GPX como puntos (`wpt`), así que Wikiloc y las demás apps los leen; las fotos se quedan en el móvil
- En el detalle de la salida, en el mapa y en una lista; tocando la foto se abre en grande
- Distancia, tiempo total y en movimiento, velocidad media y máxima, desnivel
- Aviso en la barra de abajo mientras grabas, para volver a la grabación
- *Mis salidas* / *Mis excursiones* con el historial
- Detalle con el recorrido coloreado por velocidad y su leyenda
- Exportar a GPX para Strava, Wikiloc u otras apps
- Usar una salida grabada como ruta
- Borrar salidas

## Info

- Pestaña **Info** con el icono de la estación, la versión, el canal (estable, beta o debug) y el commit
- La versión sube sola con cada cambio: `0.3.0` la estable, `0.3.0.58-beta` la beta y `0.3.0.12-debug` la que compilas tú
- Qué es Trazo, enlaces al código en GitHub, a las novedades de cada versión, a la lista de funciones y para avisar de un fallo
- Espacio para donar (sale cuando haya enlace)
- Créditos de los datos y la nota de privacidad

## Diseño

- Material Design 3 con Jetpack Compose
- Tema oscuro y «modo sol» claro de alto contraste para leer a pleno sol
- Pantalla de borde a borde

## Privacidad

- Sin cuentas ni anuncios; la comunidad usa un usuario anónimo por móvil
- Rutas, salidas y ajustes se quedan en el móvil; solo sale lo que publicas en la comunidad
- Sin Google Play Services
