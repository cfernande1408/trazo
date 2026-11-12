package app.trazo.ui

import app.trazo.gpx.i18n.L
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Abre un enlace en la app que lo maneje (la de Wikiloc si está instalada, o el navegador). */
fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, L("No hay ninguna app para abrir el enlace", "There is no app to open the link"), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Indicaciones en coche hasta un punto. Primero Google Maps en modo
 * navegación; si no está instalado, cualquier app de mapas (OsmAnd,
 * Organic Maps…) mediante un enlace geo:.
 */
fun Context.openDirections(lat: Double, lon: Double, label: String) {
    val googleMaps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon&mode=d"))
        .setPackage("com.google.android.apps.maps")
    val anyMaps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})"))
    try {
        startActivity(googleMaps)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(anyMaps)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, L("Instala una app de mapas para ver cómo llegar", "Install a maps app to get directions"), Toast.LENGTH_SHORT).show()
        }
    }
}
