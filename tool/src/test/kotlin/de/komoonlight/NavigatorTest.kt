package de.komoonlight

import de.komoonlight.geo.Geo
import de.komoonlight.geo.Pt
import de.komoonlight.komoot.KomootClient
import de.komoonlight.nav.Kind
import de.komoonlight.nav.NavEvent
import de.komoonlight.nav.Navigator
import de.komoonlight.nav.Turn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavigatorTest {

    // ~1 km nach Norden, dann ~1 km nach Osten (Rechtskurve an Index 100)
    private val start = Pt(52.5, 13.4)
    private val route: List<Pt> = buildList {
        for (i in 0..100) add(Pt(start.lat + i * 0.00009, start.lon))          // ~10 m Schritte
        val corner = last()
        for (i in 1..100) add(Pt(corner.lat, corner.lon + i * 0.000148))       // ~10 m Schritte
    }
    private val turns = listOf(Turn(100, "TR", "Hauptstraße"), Turn(200, "D", null))

    /** Fährt die Route in 7-m-Schritten ab (≈ 25 km/h bei 1 Fix/s) und sammelt alle Ereignisse. */
    private fun ride(nav: Navigator, offsetLon: Double = 0.0): List<NavEvent> {
        val cum = Geo.cumulative(route)
        val events = mutableListOf<NavEvent>()
        var d = 0.0
        while (d <= cum.last()) {
            val i = cum.indexOfFirst { it >= d }.coerceAtLeast(1)
            val a = route[i - 1]; val b = route[i]
            val t = if (cum[i] == cum[i - 1]) 0.0 else (d - cum[i - 1]) / (cum[i] - cum[i - 1])
            val p = Pt(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t + offsetLon)
            events += nav.update(p).second
            d += 7.0
        }
        return events
    }

    @Test
    fun announcesTurnOnceInRightOrder() {
        val events = ride(Navigator(route, turns)).filter { (it as? NavEvent.Upcoming)?.turn?.kind == Kind.RIGHT ||
            (it as? NavEvent.Approaching)?.turn?.kind == Kind.RIGHT || (it as? NavEvent.Now)?.turn?.kind == Kind.RIGHT ||
            (it as? NavEvent.Passed)?.turn?.kind == Kind.RIGHT }
        assertEquals(
            listOf(NavEvent.Upcoming::class, NavEvent.Approaching::class, NavEvent.Now::class, NavEvent.Passed::class),
            events.map { it::class },
        )
        val up = events.first() as NavEvent.Upcoming
        assertTrue(up.meters in 100..200, "Vorankündigung bei ${up.meters} m")
    }

    @Test
    fun announcesArrival() {
        val events = ride(Navigator(route, turns))
        assertTrue(events.any { it is NavEvent.Now && it.turn.kind == Kind.ARRIVE })
    }

    @Test
    fun noTurnEventsWhileOffRoute() {
        // ~100 m östlich parallel zur ersten Geraden
        val events = ride(Navigator(route, turns), offsetLon = 0.0015)
        assertTrue(events.first() is NavEvent.OffRoute)
    }

    @Test
    fun remainingDistanceShrinks() {
        val nav = Navigator(route, turns)
        val a = nav.update(route[10]).first.remainingM
        val b = nav.update(route[150]).first.remainingM
        assertTrue(a > b)
        assertTrue(abs(nav.totalM - 1_000 - 1_000) < 50, "Länge ${nav.totalM}")
    }

    @Test
    fun straightHintsAreIgnored() {
        val nav = Navigator(route, listOf(Turn(50, "S", "Weg"), Turn(100, "TL", null)))
        assertEquals(1, nav.turns.size)
        assertEquals(Kind.LEFT, nav.turns.single().kind)
    }

    @Test
    fun parsesKomootTour() {
        val json = Json.parseToJsonElement(
            """
            {"_embedded":{
              "coordinates":{"items":[{"lat":52.5,"lng":13.4,"alt":34.0,"t":0},{"lat":52.501,"lng":13.4,"t":1000}]},
              "directions":{"items":[{"index":1,"type":"TSL","street_name":"Am See"},{"index":9,"type":"TR"}]}
            }}
            """
        ).jsonObject
        val r = KomootClient.parseRoute(json)
        assertEquals(2, r.points.size)
        assertEquals(34.0, r.points[0].alt)
        assertEquals(1, r.turns.size) // Index 9 liegt außerhalb
        assertEquals(Kind.SLIGHT_LEFT, r.turns.single().kind)
        assertNotNull(r.turns.single().street)
    }
}
