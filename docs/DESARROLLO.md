# Desarrollo de Trazo

Notas para compilar la app, publicar versiones y activar la comunidad. Para usarla basta con
descargar el APK de [Releases](https://github.com/cfernande1408/trazo/releases/latest).

## Compilar

Necesitas JDK 17+ y el SDK de Android (compileSdk 35):

```bash
git clone https://github.com/cfernande1408/trazo.git
cd trazo
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # si no lo crea Android Studio
./gradlew :app:installDebug                            # con el móvil conectado por USB
```

Comandos útiles:

```bash
./gradlew :core:gpx:test        # tests del núcleo
./gradlew :app:assembleDebug    # APK de debug
./scripts/screenshot.sh ruta    # captura del móvil para el README
python3 scripts/icons.py . /tmp # regenera los iconos de las cuatro estaciones
```

## Trazo web

Está en `web/`: TypeScript, Vite y MapLibre GL JS. Usa el mismo estilo de mapa que la app
(`app/src/main/assets/trazo_topo.json`) y los cálculos del núcleo pasados a `web/src/geo.ts`: si
cambian en `core/gpx`, hay que cambiarlos también ahí. Necesitas Node 20 o superior.

```bash
cd web
npm install
npm run dev      # en http://localhost:5173, se recarga sola al guardar
npm test         # tests (lectura de GPX, desnivel, pendiente, altitudes)
npm run build    # la web lista en web/dist
```

Cada push a `main` que toque `web/` la publica en GitHub Pages con `.github/workflows/pages.yml`.
La primera vez hay que activarlo: **Settings → Pages → Build and deployment → Source: GitHub Actions**.

## Publicar una versión estable

Cada push a `main` pasa los tests y compila en GitHub Actions, y actualiza la beta. Las versiones
estables se publican con una etiqueta:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

La primera vez hay que crear la clave de firma y guardarla en los secretos del repo
(*Settings → Secrets and variables → Actions*):

```bash
keytool -genkeypair -v -keystore trazo.jks -alias trazo -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 trazo.jks          # → secreto TRAZO_KEYSTORE_BASE64
```

y los secretos `TRAZO_KEYSTORE_PASSWORD`, `TRAZO_KEY_ALIAS` (`trazo`) y `TRAZO_KEY_PASSWORD`.
Guarda `trazo.jks` fuera del repo: sin ella no se pueden publicar actualizaciones.

## Activar la comunidad

Las valoraciones y los avisos van a un proyecto de [Supabase](https://supabase.com) (el plan
gratuito sobra). Sin configurarlo, la app funciona igual y la comunidad sale desactivada.

1. Crea un proyecto en Supabase.
2. En *Authentication → Sign In / Providers*, activa *Allow anonymous sign-ins*.
3. En *SQL Editor*, pega y ejecuta [`supabase/schema.sql`](supabase/schema.sql): crea las tablas
   y las políticas de seguridad (cada uno solo puede tocar lo suyo).
4. En *Project Settings → API* copia la URL y la clave `anon` a `local.properties`:

```properties
trazo.supabase.url=https://xxxx.supabase.co
trazo.supabase.anonKey=eyJ...
```

Para las releases, pon lo mismo en los secretos del repo `TRAZO_SUPABASE_URL` y
`TRAZO_SUPABASE_ANON_KEY`. La clave `anon` es pública por diseño: lo que protege los datos son
las políticas de la base.
