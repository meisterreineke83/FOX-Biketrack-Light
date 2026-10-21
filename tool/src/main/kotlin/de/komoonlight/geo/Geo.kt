package de.komoonlight.geo

import kotlin.math.*

/** Ein GPS-Punkt. time = Unix-Millis (für aufgezeichnete Tracks) oder null. */
data class Pt(val lat: Double, val lon: Double, val alt: Double? = null, val time: Long? = null)

/** Ergebnis vom "Einrasten" der eigenen Position auf die Route. */
data class Snap(
    val segmentIndex: Int,     // Index des nächstgelegenen Routensegments
    val offRouteM: Double,     // Abstand zur Route in Metern
    val alongM: Double         // zurückgelegte Strecke entlang der Route
)

object Geo {
    private const val R = 6_371_000.0

    fun dist(a: Pt, b: Pt): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    /** Kurs von a nach b in Grad (0 = Nord, im Uhrzeigersinn). */
    fun bearing(a: Pt, b: Pt): Double {
        val la1 = Math.toRadians(a.lat); val la2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Kumulierte Distanz entlang der Route, cum[i] = Meter bis Punkt i. */
    fun cumulative(route: List<Pt>): DoubleArray {
        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) cum[i] = cum[i - 1] + dist(route[i - 1], route[i])
        return cum
    }

    /**
     * Sucht das nächstgelegene Routensegment. Zuerst in einem Fenster um den letzten Treffer
     * (schnell, verhindert Springen bei Schleifen/Hin-und-zurück), sonst über die ganze Route.
     */
    fun snap(route: List<Pt>, cum: DoubleArray, p: Pt, hint: Int): Snap {
        if (route.size < 2) return Snap(0, if (route.isEmpty()) 0.0 else dist(route[0], p), 0.0)
        val local = search(route, cum, p, (hint - 20).coerceAtLeast(0), (hint + 300).coerceAtMost(route.size - 2))
        return if (local.offRouteM < 80) local else search(route, cum, p, 0, route.size - 2)
    }

    private fun search(route: List<Pt>, cum: DoubleArray, p: Pt, from: Int, to: Int): Snap {
        var best = Snap(from, Double.MAX_VALUE, 0.0)
        val kx = cos(Math.toRadians(p.lat)) * R * PI / 180  // Meter pro Grad Länge
        val ky = R * PI / 180                               // Meter pro Grad Breite
        for (i in from..to) {
            val a = route[i]; val b = route[i + 1]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
            val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * ky
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            val d = sqrt(cx * cx + cy * cy)
            if (d < best.offRouteM) best = Snap(i, d, cum[i] + t * (cum[i + 1] - cum[i]))
        }
        return best
    }

    /** Punkt auf der Route, der `meters` Meter nach `alongM` liegt (für den Richtungspfeil). */
    fun pointAhead(route: List<Pt>, cum: DoubleArray, alongM: Double, meters: Double): Pt {
        val target = alongM + meters
        val i = cum.indexOfFirst { it >= target }
        return if (i == -1) route.last() else route[i]
    }
}
