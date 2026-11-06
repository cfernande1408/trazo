#!/bin/sh
# Guarda lo que se ve ahora en el móvil como captura del README.
# Uso: ./scripts/screenshot.sh explorar   →  docs/screenshots/explorar.png
set -e
name="${1:?Pon un nombre: explorar, ruta, mapa, 3d, crear, salida…}"
dir="$(dirname "$0")/../docs/screenshots"
mkdir -p "$dir"
# Barra de estado limpia (hora fija, batería llena, sin notificaciones)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
sleep 1
adb exec-out screencap -p > "$dir/$name.png"
adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
echo "Guardada $dir/$name.png"
