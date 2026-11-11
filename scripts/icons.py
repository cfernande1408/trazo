# Genera los iconos de Trazo por estación (vectores de Android) y una vista previa en SVG.
# Uso: python3 icons.py <raíz del repo> <carpeta de la vista previa>
import os, sys
root, out = sys.argv[1], sys.argv[2]
R = root + '/app/src/main/res'

# Debug, beta y estable llevan el mismo icono: se distinguen por el nombre
# (Trazo debug, Trazo beta, Trazo).
# Cielo arriba/abajo, sierra, nieve, monte de delante, árboles
SEASONS = {
    'spring': dict(top='#BFE6FF', bottom='#DDF3C4', ridge='#5FA03A', snow='#E6FFFFFF', hill='#3E7A23', tree='#9BD36A', shade='#5E9E36', bloom='#F4A7C0'),
    'summer': dict(top='#C5F26B', bottom='#8CCB5E', ridge='#4F7F33', snow='#E6FFFFFF', hill='#2E4F00', tree='#7FB24F', shade='#4E8A2E', bloom=None),
    'autumn': dict(top='#FFE0A8', bottom='#F2A65A', ridge='#7C6A2B', snow='#E6FFFFFF', hill='#5E3410', tree='#E08A2E', shade='#B5532A', bloom=None),
    'winter': dict(top='#DCEBFA', bottom='#AFC9E6', ridge='#7E97AE', snow='#FFFFFFFF', hill='#E8F0F6', tree='#3D6B44', shade='#264A2D', bloom=None),
}
RIDGE = 'M0,70L26,46L38,56L58,34L80,58L90,50L108,62L108,108L0,108Z'
SNOW = 'M58,34L66,43L62,42L58,47L54,41L51,42Z'
WINTER_SNOW = 'M58,34L70,47L64,45L58,51L52,44L46,48Z'  # en invierno, la nieve baja más
HILL = 'M0,82L28,66L54,77L80,64L108,74L108,108L0,108Z'
PINES = ['M30,84L34.5,75L32.5,75L36.5,67L40.5,75L38.5,75L43,84Z', 'M70,80L74,72L72,72L75.5,65L79,72L77,72L81,80Z']
# La ruta termina en la ladera, por debajo de la cresta
ROUTE = 'M30,92C46,86 44,74 54,72S62,59 64,52'
END = (64, 52)
BLOOMS = [(36, 72), (40, 79), (74, 70), (78, 76), (33, 80), (49, 75), (53, 79)]

def pine(cx, base, h):
    """Pino de dos pisos (mitad iluminada y mitad en sombra) y su tronco."""
    k = h / 17
    pts = [(-6.5, 0), (-2, -9), (-4, -9), (0, -17), (4, -9), (2, -9), (6.5, 0)]
    full = 'M' + 'L'.join(f'{cx + x * k:.2f},{base + y * k:.2f}' for x, y in pts) + 'Z'
    half = 'M' + 'L'.join(f'{cx + x * k:.2f},{base + y * k:.2f}' for x, y in pts[3:]) + f'L{cx:.2f},{base:.2f}Z'
    trunk = f'M{cx - 1.1 * k:.2f},{base:.2f}h{2.2 * k:.2f}v{3 * k:.2f}h{-2.2 * k:.2f}z'
    return full, half, trunk

# Delante del trazo, tapando un trozo: así la ruta pasa por detrás y parece 3D
FRONT = [pine(50.5, 82.5, 16)]

def circle(cx, cy, r):
    return f'M{cx},{cy}m-{r},0a{r},{r} 0,1 1,{2*r},0a{r},{r} 0,1 1,-{2*r},0'

def bg(s):
    c = SEASONS[s]
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Fondo del icono ({s}): cielo, sierra con nieve y monte delante. Generado por scripts/icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="0" android:endY="108"
                android:startColor="#FF{c['top'][1:]}" android:endColor="#FF{c['bottom'][1:]}" />
        </aapt:attr>
    </path>
    <path android:fillColor="{c['ridge']}" android:pathData="{RIDGE}" />
    <path android:fillColor="{c['snow']}" android:pathData="{WINTER_SNOW if s == 'winter' else SNOW}" />
    <path android:fillColor="{c['hill']}" android:pathData="{HILL}" />
</vector>
'''

def fg(s):
    c = SEASONS[s]
    trees = '\n'.join(f'    <path android:fillColor="{c["tree"]}" android:pathData="{p}" />' for p in PINES)
    blooms = '\n'.join(f'    <path android:fillColor="{c["bloom"]}" android:pathData="{circle(x, y, 1.6)}" />' for x, y in BLOOMS) if c['bloom'] else ''
    front = '\n'.join(
        f'    <path android:fillColor="#6B4F35" android:pathData="{t}" />\n'
        f'    <path android:fillColor="{c["tree"]}" android:pathData="{f}" />\n'
        f'    <path android:fillColor="{c["shade"]}" android:pathData="{h}" />'
        for f, h, t in FRONT)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Primer plano del icono ({s}): árboles y el trazo de la ruta. Generado por scripts/icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{trees}
    <path android:strokeColor="#FFFFFF" android:strokeWidth="7.5" android:strokeLineCap="round" android:pathData="{ROUTE}" />
    <path android:strokeColor="#C2410C" android:strokeWidth="4.2" android:strokeLineCap="round" android:pathData="{ROUTE}" />
    <path android:fillColor="#C2410C" android:strokeColor="#FFFFFF" android:strokeWidth="2.2" android:pathData="{circle(*END, 5.5)}" />
{front}
{blooms}
</vector>
'''

def adaptive(s):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_bg_{s}" />
    <foreground android:drawable="@drawable/ic_launcher_fg_{s}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''

MONO = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Icono temático (Android 13+): silueta de la sierra y el trazo, en un solo color -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:strokeColor="#FFFFFFFF" android:strokeWidth="3" android:strokeLineJoin="round"
        android:pathData="M24,70L36,58L44,64L58,44L72,62L84,56" />
    <path android:strokeColor="#FFFFFFFF" android:strokeWidth="5" android:strokeLineCap="round"
        android:pathData="M30,90C46,84 44,74 54,72S61,62 62,58" />
    <path android:fillColor="#FFFFFFFF" android:pathData="{circle(62, 58, 5)}" />
</vector>
'''

def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'w').write(text)

for s in SEASONS:
    w(f'{R}/drawable/ic_launcher_bg_{s}.xml', bg(s))
    w(f'{R}/drawable/ic_launcher_fg_{s}.xml', fg(s))
    w(f'{R}/mipmap-anydpi-v26/ic_launcher_{s}.xml', adaptive(s))
w(f'{R}/drawable/ic_launcher_monochrome.xml', MONO)
# El icono "de la app" (ajustes, notificaciones) es el de verano
w(f'{R}/mipmap-anydpi-v26/ic_launcher.xml', adaptive('summer'))
w(f'{R}/mipmap-anydpi-v26/ic_launcher_round.xml', adaptive('summer'))

# Vista previa
def svg(s, i):
    c = SEASONS[s]
    blooms = ''.join(f'<circle cx="{x}" cy="{y}" r="1.6" fill="{c["bloom"]}"/>' for x, y in BLOOMS) if c['bloom'] else ''
    snow = WINTER_SNOW if s == 'winter' else SNOW
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 108 108">
<defs><linearGradient id="g{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{c['top']}"/><stop offset="1" stop-color="{c['bottom']}"/></linearGradient>
<clipPath id="m{i}"><circle cx="54" cy="54" r="36"/></clipPath></defs>
<g clip-path="url(#m{i})"><rect width="108" height="108" fill="url(#g{i})"/>
<path fill="{c['ridge']}" d="{RIDGE}"/><path fill="#fff" fill-opacity=".92" d="{snow}"/><path fill="{c['hill']}" d="{HILL}"/>
{''.join(f'<path fill="{c["tree"]}" d="{p}"/>' for p in PINES)}
<path fill="none" stroke="#fff" stroke-width="7.5" stroke-linecap="round" d="{ROUTE}"/>
<path fill="none" stroke="#C2410C" stroke-width="4.2" stroke-linecap="round" d="{ROUTE}"/>
<circle cx="{END[0]}" cy="{END[1]}" r="5.5" fill="#C2410C" stroke="#fff" stroke-width="2.2"/>
{''.join(f'<path fill="#6B4F35" d="{t}"/><path fill="{c["tree"]}" d="{f}"/><path fill="{c["shade"]}" d="{h}"/>' for f, h, t in FRONT)}{blooms}</g></svg>'''
# Logo del README y de la web: el icono de verano en baldosa redondeada, recortado a la
# parte que enseña el escritorio (el círculo central de 72 de los 108 del icono adaptativo)
def logo(s='summer'):
    c = SEASONS[s]
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="18 18 72 72">
<!-- Logo de Trazo. Generado por scripts/icons.py con el icono de la app -->
<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{c['top']}"/><stop offset="1" stop-color="{c['bottom']}"/></linearGradient>
<clipPath id="tile"><rect x="18" y="18" width="72" height="72" rx="18"/></clipPath></defs>
<g clip-path="url(#tile)"><rect x="18" y="18" width="72" height="72" fill="url(#sky)"/>
<path fill="{c['ridge']}" d="{RIDGE}"/><path fill="#fff" fill-opacity=".92" d="{SNOW}"/><path fill="{c['hill']}" d="{HILL}"/>
{''.join(f'<path fill="{c["tree"]}" d="{p}"/>' for p in PINES)}
<path fill="none" stroke="#fff" stroke-width="7.5" stroke-linecap="round" d="{ROUTE}"/>
<path fill="none" stroke="#C2410C" stroke-width="4.2" stroke-linecap="round" d="{ROUTE}"/>
<circle cx="{END[0]}" cy="{END[1]}" r="5.5" fill="#C2410C" stroke="#fff" stroke-width="2.2"/>
{''.join(f'<path fill="#6B4F35" d="{t}"/><path fill="{c["tree"]}" d="{f}"/><path fill="{c["shade"]}" d="{h}"/>' for f, h, t in FRONT)}</g></svg>
'''

w(root + '/docs/logo.svg', logo())

html = '<body style="margin:0;background:#1d1b16;display:flex;gap:16px;padding:12px;color:#eee;font:14px sans-serif">' + \
    ''.join(f'<div style="text-align:center">{svg(s, i)}<br>{s}</div>' for i, s in enumerate(SEASONS)) + '</body>'
open(out + '/icons.html', 'w').write(html)
