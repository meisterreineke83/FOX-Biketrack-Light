package de.komoonlight.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Web-Mercator wie bei OpenStreetMap, mit 512-px-Kacheln (@2x, scharf auf dem Light-Display). */
object WebMercator {
    const val TILE = 512
    private const val EARTH_MPP_Z0 = 156_543.03392 // Meter pro Pixel bei Zoom 0, 256er-Kachel, Äquator

    fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * 2.0.pow(z) * TILE

    fun worldY(lat: Double, z: Int): Double {
        val r = lat.coerceIn(-85.0511, 85.0511) * PI / 180
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * 2.0.pow(z) * TILE
    }

    /** Meter pro Kachel-Pixel bei Zoom [z] an Breite [lat]. */
    fun mpp(lat: Double, z: Int) = EARTH_MPP_Z0 * cos(lat * PI / 180) / 2.0.pow(z) / (TILE / 256)

    /** Kachel (x, y), in der ein Punkt liegt. */
    fun tileOf(p: Pt, z: Int) = Pair(
        floor(worldX(p.lon, z) / TILE).toInt(),
        floor(worldY(p.lat, z) / TILE).toInt(),
    )

    /** Ganzzahliger Zoom, dessen Kacheln bei [mppScreen] Meter/Bildschirmpixel am schärfsten sind. */
    fun zoomFor(lat: Double, mppScreen: Double, min: Int = 2, max: Int = 19): Int =
        log2(EARTH_MPP_Z0 * cos(lat * PI / 180) / (TILE / 256) / mppScreen).roundToInt().coerceIn(min, max)
}

/** Eine Kachel und wo sie auf dem Bildschirm hingehört. */
data class TileDraw(val z: Int, val x: Int, val y: Int, val left: Float, val top: Float, val size: Float)

/**
 * Bildschirmausschnitt um [center]: [radiusM] Meter vom Zentrum bis zum kürzeren Rand.
 * Route, Track und Kacheln nutzen dieselbe Projektion, damit alles deckungsgleich liegt.
 */
class MapView(center: Pt, radiusM: Double, private val width: Float, private val height: Float) {
    private val mppScreen = radiusM / (minOf(width, height) / 2.0)
    val zoom = WebMercator.zoomFor(center.lat, mppScreen)
    /** Bildschirmpixel pro Kachelpixel. */
    private val scale = WebMercator.mpp(center.lat, zoom) / mppScreen
    private val cx = WebMercator.worldX(center.lon, zoom)
    private val cy = WebMercator.worldY(center.lat, zoom)

    fun x(p: Pt) = ((WebMercator.worldX(p.lon, zoom) - cx) * scale + width / 2).toFloat()
    fun y(p: Pt) = ((WebMercator.worldY(p.lat, zoom) - cy) * scale + height / 2).toFloat()

    /** Alle Kacheln, die den Bildschirm berühren. */
    fun tiles(): List<TileDraw> {
        val t = WebMercator.TILE
        val n = 1 shl zoom
        val x0 = floor((cx - width / 2 / scale) / t).toInt()
        val x1 = floor((cx + width / 2 / scale) / t).toInt()
        val y0 = floor((cy - height / 2 / scale) / t).toInt().coerceAtLeast(0)
        val y1 = floor((cy + height / 2 / scale) / t).toInt().coerceAtMost(n - 1)
        val size = (t * scale).toFloat()
        return buildList {
            for (ty in y0..y1) for (tx in x0..x1) {
                add(TileDraw(
                    z = zoom, x = Math.floorMod(tx, n), y = ty,
                    left = ((tx * t - cx) * scale + width / 2).toFloat(),
                    top = ((ty * t - cy) * scale + height / 2).toFloat(),
                    size = size,
                ))
            }
        }
    }
}

/** Kacheln entlang einer Route (für Offline-Nutzung), höchstens [limit] Stück. */
fun tilesAlong(route: List<Pt>, zooms: List<Int>, limit: Int): List<Triple<Int, Int, Int>> {
    val out = LinkedHashSet<Triple<Int, Int, Int>>()
    for (z in zooms) for (p in route) {
        val (x, y) = WebMercator.tileOf(p, z)
        out += Triple(z, x, y)
        if (out.size >= limit) return out.toList()
    }
    return out.toList()
}

/** Mittelpunkt und Radius (Meter), damit die ganze Route auf den Bildschirm passt. */
fun fitRoute(route: List<Pt>): Pair<Pt, Double>? {
    if (route.isEmpty()) return null
    val minLat = route.minOf { it.lat }; val maxLat = route.maxOf { it.lat }
    val minLon = route.minOf { it.lon }; val maxLon = route.maxOf { it.lon }
    val center = Pt((minLat + maxLat) / 2, (minLon + maxLon) / 2)
    val halfH = Geo.dist(Pt(minLat, center.lon), Pt(maxLat, center.lon)) / 2
    val halfW = Geo.dist(Pt(center.lat, minLon), Pt(center.lat, maxLon)) / 2
    return center to (maxOf(halfH, halfW) * 1.15).coerceIn(150.0, 50_000.0)
}
