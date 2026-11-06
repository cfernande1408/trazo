package app.trazo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.trazo.gpx.GeoPoint
import app.trazo.gpx.ProfilePoint
import app.trazo.gpx.RouteGeometry

/** Silueta de la ruta, con el norte arriba, sin mapa de fondo. */
@Composable
fun RouteShape(segments: List<List<GeoPoint>>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val casing = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val dark = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        val step = 24.dp.toPx()
        var gx = step
        while (gx < size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1.dp.toPx()); gx += step }
        var gy = step
        while (gy < size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()); gy += step }

        val projected = RouteGeometry.project(segments, size.width, size.height, padding = 24.dp.toPx())
        projected.filter { it.size >= 2 }.forEach { seg ->
            val path = Path().apply {
                moveTo(seg[0].x, seg[0].y)
                for (p in seg.drop(1)) lineTo(p.x, p.y)
            }
            drawPath(path, casing, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, line, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        projected.firstOrNull()?.firstOrNull()?.let {
            drawCircle(casing, 9.dp.toPx(), Offset(it.x, it.y))
            drawCircle(line, 6.dp.toPx(), Offset(it.x, it.y))
        }
        projected.lastOrNull()?.lastOrNull()?.let {
            drawCircle(casing, 9.dp.toPx(), Offset(it.x, it.y))
            drawCircle(dark, 6.dp.toPx(), Offset(it.x, it.y))
        }
    }
}

/** Perfil de desnivel: área rellena con degradado y línea encima. */
@Composable
fun ElevationChart(profile: List<ProfilePoint>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val stroke = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        if (profile.size < 2) return@Canvas
        val total = profile.last().distanceMeters.takeIf { it > 0 } ?: return@Canvas
        val minE = profile.minOf { it.ele }
        val range = (profile.maxOf { it.ele } - minE).coerceAtLeast(10.0)
        val top = 4.dp.toPx()
        fun x(p: ProfilePoint) = (p.distanceMeters / total * size.width).toFloat()
        fun y(p: ProfilePoint) = (top + (1 - (p.ele - minE) / range) * (size.height - top)).toFloat()

        val line = Path().apply {
            moveTo(x(profile[0]), y(profile[0]))
            for (p in profile.drop(1)) lineTo(x(p), y(p))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(fill.copy(alpha = 0.45f), fill.copy(alpha = 0.05f))))
        drawPath(line, stroke, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}
