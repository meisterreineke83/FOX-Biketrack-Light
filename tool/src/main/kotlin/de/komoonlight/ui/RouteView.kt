package de.komoonlight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import de.komoonlight.geo.Pt
import kotlin.math.PI
import kotlin.math.cos

/**
 * Kartenlose Routenansicht fürs S/W-Display:
 * Route = durchgezogene Linie, eigener Track = gepunktet, Position = Pfeil in der Mitte.
 * Norden ist oben. [radiusM] = Meter vom Zentrum bis zum Rand.
 */
@Composable
fun RouteView(
    route: List<Pt>,
    track: List<Pt>,
    position: Pt?,
    headingDeg: Double?,
    radiusM: Double,
    content: Color,
    background: Color,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Canvas(modifier) {
        val center = position ?: route.firstOrNull() ?: return@Canvas
        val pxPerM = (minOf(size.width, size.height) / 2f) / radiusM.toFloat()
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(center.lat * PI / 180)

        fun toScreen(p: Pt) = Offset(
            x = size.width / 2f + ((p.lon - center.lon) * mPerDegLon).toFloat() * pxPerM,
            y = size.height / 2f - ((p.lat - center.lat) * mPerDegLat).toFloat() * pxPerM,
        )

        fun pathOf(pts: List<Pt>) = Path().apply {
            pts.forEachIndexed { i, p ->
                val o = toScreen(p)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }

        if (route.size > 1) drawPath(pathOf(route), content, style = Stroke(width = 10f, cap = StrokeCap.Round))

        if (track.size > 1) drawPath(
            pathOf(track), content,
            style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 14f)), cap = StrokeCap.Round),
        )

        if (position != null) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val arrow = Path().apply {
                moveTo(c.x, c.y - 34f); lineTo(c.x + 22f, c.y + 24f)
                lineTo(c.x, c.y + 12f); lineTo(c.x - 22f, c.y + 24f); close()
            }
            rotate((headingDeg ?: 0.0).toFloat(), c) {
                drawPath(arrow, background, style = Stroke(width = 12f))
                drawPath(arrow, content)
            }
        }
    }
}
