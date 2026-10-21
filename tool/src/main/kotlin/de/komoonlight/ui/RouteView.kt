package de.komoonlight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.komoonlight.geo.MapView
import de.komoonlight.geo.Pt
import de.komoonlight.map.MapStyle
import de.komoonlight.map.TileStore
import kotlin.math.roundToInt

/**
 * Routenansicht fürs Light-Display, optional mit OpenStreetMap-Karte darunter:
 * Route = breite Linie (mit Rand, damit sie auf der Karte heraussticht), eigener Track = gepunktet,
 * Position = Pfeil in der Mitte. Norden ist oben. [radiusM] = Meter vom Zentrum bis zum Rand.
 */
@Composable
fun RouteView(
    route: List<Pt>,
    track: List<Pt>,
    position: Pt?,
    /** Bildmitte; null → eigene Position bzw. Routenanfang. */
    center: Pt?,
    headingDeg: Double?,
    radiusM: Double,
    content: Color,
    background: Color,
    tiles: TileStore?,
    mapStyle: MapStyle,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    // Neu zeichnen, sobald eine Kachel nachgeladen wurde
    val tileVersion = tiles?.version?.collectAsState()?.value ?: 0

    Canvas(modifier) {
        tileVersion // im Zeichenblock gelesen → Invalidierung
        val mid = center ?: position ?: route.firstOrNull() ?: return@Canvas
        val map = MapView(mid, radiusM, size.width, size.height)
        val filter = contrastFor(mapStyle)
        fun o(p: Pt) = Offset(map.x(p), map.y(p))

        // 1. Karte
        if (tiles != null) {
            for (t in map.tiles()) {
                val img = tiles.get(mapStyle, t.z, t.x, t.y) ?: continue
                val s = t.size.roundToInt() + 1 // +1 gegen Haarlinien zwischen Kacheln
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(t.left.roundToInt(), t.top.roundToInt()),
                    dstSize = IntSize(s, s),
                    colorFilter = filter,
                )
            }
        }

        fun pathOf(pts: List<Pt>) = Path().apply {
            pts.forEachIndexed { i, p ->
                val q = o(p)
                if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
            }
        }

        // 2. Route – mit Rand in Hintergrundfarbe, damit sie sich von Straßen abhebt
        if (route.size > 1) {
            val path = pathOf(route)
            if (tiles != null) drawPath(path, background, style = Stroke(width = 18f, cap = StrokeCap.Round))
            drawPath(path, content, style = Stroke(width = 10f, cap = StrokeCap.Round))
        }

        // 3. Eigener Weg
        if (track.size > 1) drawPath(
            pathOf(track), content,
            style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 14f)), cap = StrokeCap.Round),
        )

        // 4. Position (nur wenn sie im Bild liegt)
        if (position != null) {
            val c = o(position)
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

/**
 * Kontrast für das Graustufen-Display: Dark Matter ist sehr dunkel (Straßen ~#1a1a1a auf ~#0e0e0e),
 * Positron sehr hell. Ohne Anhebung ist auf dem Light-Display praktisch nichts zu sehen.
 */
private fun contrastFor(style: MapStyle): ColorFilter {
    val (gain, offset) = when (style) {
        MapStyle.DARK -> 5f to -45f    // #0e → ~#19 (bleibt dunkel), #1a → ~#55, #26 → ~#91, Hauptstraßen → weiß
        MapStyle.LIGHT -> 3f to -510f  // #fa → ~#f0, Flächen #f0 → ~#d2, Straßenränder #e0 → ~#a2, Schrift → schwarz
    }
    return ColorFilter.colorMatrix(
        ColorMatrix(floatArrayOf(
            gain, 0f, 0f, 0f, offset,
            0f, gain, 0f, 0f, offset,
            0f, 0f, gain, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ))
    )
}
