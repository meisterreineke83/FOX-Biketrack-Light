package de.komoonlight

import de.komoonlight.geo.Geo
import de.komoonlight.geo.MapView
import de.komoonlight.geo.Pt
import de.komoonlight.geo.WebMercator
import de.komoonlight.geo.fitRoute
import de.komoonlight.geo.tilesAlong
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapViewTest {

    private val berlin = Pt(52.5163, 13.3777) // Brandenburger Tor

    @Test
    fun tileNumbersMatchOsm() {
        // Referenz: Slippy-Map-Formel aus dem OSM-Wiki, Zoom 16
        assertEquals(Pair(35203, 21493), WebMercator.tileOf(berlin, 16))
    }

    @Test
    fun centerIsInTheMiddle() {
        val v = MapView(berlin, 300.0, 1080f, 900f)
        assertTrue(abs(v.x(berlin) - 540f) < 0.01f)
        assertTrue(abs(v.y(berlin) - 450f) < 0.01f)
    }

    @Test
    fun scaleMatchesRadius() {
        val v = MapView(berlin, 300.0, 1000f, 1000f)
        // Punkt 300 m nördlich liegt am oberen Rand (≈ 500 px über der Mitte)
        val north = Pt(berlin.lat + 300 / 111_320.0, berlin.lon)
        assertTrue(abs((v.y(berlin) - v.y(north)) - 500f) < 10f, "Abstand ${v.y(berlin) - v.y(north)}")
        val east = Pt(berlin.lat, berlin.lon + 0.002)
        val meters = Geo.dist(berlin, east)
        assertTrue(abs((v.x(east) - v.x(berlin)) - meters / 300 * 500) < 10f)
    }

    @Test
    fun tilesCoverWholeScreen() {
        val w = 1080f; val h = 900f
        val tiles = MapView(berlin, 300.0, w, h).tiles()
        assertTrue(tiles.minOf { it.left } <= 0f && tiles.minOf { it.top } <= 0f)
        assertTrue(tiles.maxOf { it.left + it.size } >= w && tiles.maxOf { it.top + it.size } >= h)
        assertTrue(tiles.size in 2..16, "${tiles.size} Kacheln")
        // Kacheln stoßen lückenlos aneinander
        val xs = tiles.map { it.left }.distinct().sorted()
        xs.zipWithNext().forEach { (a, b) -> assertTrue(abs(b - a - tiles[0].size) < 0.5f) }
    }

    @Test
    fun zoomFollowsRadius() {
        val near = MapView(berlin, 100.0, 1000f, 1000f).zoom
        val far = MapView(berlin, 3000.0, 1000f, 1000f).zoom
        assertTrue(near > far)
        assertTrue(far >= 12 && near <= 19)
    }

    @Test
    fun prefetchIsLimitedAndDeduplicated() {
        val route = List(1000) { Pt(berlin.lat + it * 0.0001, berlin.lon) }
        val t = tilesAlong(route, listOf(15, 16), limit = 50)
        assertTrue(t.size <= 50)
        assertEquals(t.size, t.toSet().size)
    }

    @Test
    fun wholeRouteFitsOnScreen() {
        val route = listOf(berlin, Pt(berlin.lat + 0.05, berlin.lon + 0.08), Pt(berlin.lat - 0.02, berlin.lon + 0.03))
        val (c, radius) = fitRoute(route)!!
        val v = MapView(c, radius, 1080f, 900f)
        for (p in route) {
            assertTrue(v.x(p) in 0f..1080f && v.y(p) in 0f..900f, "${v.x(p)}, ${v.y(p)}")
        }
        assertEquals(null, fitRoute(emptyList()))
    }

    @Test
    fun panMovesCenterOppositeToFinger() {
        val v = MapView(berlin, 300.0, 1000f, 1000f)
        assertEquals(berlin.lat, v.panned(0f, 0f).lat, 1e-9)
        assertEquals(berlin.lon, v.panned(0f, 0f).lon, 1e-9)
        // Finger nach links → Inhalt nach links → Mitte rückt nach Osten
        val c = v.panned(-500f, 0f)
        assertTrue(c.lon > berlin.lon)
        // 500 px = Radius → neue Mitte ≈ 300 m weiter
        assertTrue(abs(Geo.dist(berlin, c) - 300) < 10, "${Geo.dist(berlin, c)}")
        // Finger nach unten → Mitte rückt nach Norden; der alte Mittelpunkt liegt dann 200 px tiefer
        val n = v.panned(0f, 200f)
        assertTrue(n.lat > berlin.lat)
        val moved = MapView(n, 300.0, 1000f, 1000f)
        assertTrue(abs(moved.y(berlin) - 700f) < 1f, "${moved.y(berlin)}")
    }
}
