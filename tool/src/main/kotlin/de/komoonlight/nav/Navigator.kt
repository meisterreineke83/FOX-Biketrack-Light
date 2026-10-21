package de.komoonlight.nav

import de.komoonlight.geo.Geo
import de.komoonlight.geo.Pt
import kotlin.math.roundToInt

/** Momentaufnahme für die Anzeige. */
data class NavStatus(
    val remainingM: Double,
    val offRouteM: Double,
    val headingDeg: Double,
    val nextTurn: Turn?,
    val nextTurnInM: Double?,
)

/** Ereignisse, auf die Ansage/Vibration/Display reagieren können. */
sealed interface NavEvent {
    /** ~200 m vorher, [meters] auf 50 m gerundet. */
    data class Upcoming(val turn: Turn, val meters: Int) : NavEvent
    /** 50 m vorher – hier soll das Display angehen. */
    data class Approaching(val turn: Turn) : NavEvent
    /** ~25 m vorher. */
    data class Now(val turn: Turn) : NavEvent
    /** 15 m danach – Display darf wieder aus. */
    data class Passed(val turn: Turn) : NavEvent
    data object OffRoute : NavEvent
    data object BackOnRoute : NavEvent
}

/**
 * Reine Navigationslogik ohne Android-Abhängigkeiten (dadurch per Unit-Test prüfbar).
 * Pro Positions-Update [update] aufrufen; liefert Status + neue Ereignisse.
 */
class Navigator(
    private val route: List<Pt>,
    rawTurns: List<Turn>,
    private val farM: Double = 200.0,
    private val approachM: Double = 50.0,
    private val nowM: Double = 25.0,
    private val passedM: Double = 15.0,
    private val offRouteM: Double = 50.0,
    private val backOnRouteM: Double = 20.0,
) {
    private val cum = Geo.cumulative(route)
    val totalM: Double = cum.lastOrNull() ?: 0.0

    /** Nur echte Richtungswechsel, sortiert entlang der Route. */
    val turns: List<Turn> = rawTurns
        .filter { it.routeIndex in route.indices && it.kind != Kind.STRAIGHT }
        .map { it.copy(alongM = cum[it.routeIndex]) }
        .sortedBy { it.alongM }

    private var hint = 0
    private var off = false
    private val fired = mutableMapOf<Int, MutableSet<String>>()

    fun update(p: Pt): Pair<NavStatus, List<NavEvent>> {
        require(route.size >= 2) { "Route braucht mindestens zwei Punkte" }
        val s = Geo.snap(route, cum, p, hint).also { hint = it.segmentIndex }
        val events = mutableListOf<NavEvent>()

        if (!off && s.offRouteM > offRouteM) { off = true; events += NavEvent.OffRoute }
        else if (off && s.offRouteM < backOnRouteM) { off = false; events += NavEvent.BackOnRoute }

        // Abbiegungen, die gerade passiert wurden
        turns.forEachIndexed { i, t ->
            if (s.alongM > t.alongM + passedM && fire(i, "passed") && wasAnnounced(i)) events += NavEvent.Passed(t)
        }

        val i = turns.indexOfFirst { it.alongM > s.alongM - passedM }
        val next = turns.getOrNull(i)
        val dist = next?.let { it.alongM - s.alongM }

        if (next != null && dist != null && !off && dist > -5) {
            if (dist in (approachM + 30)..farM && fire(i, "far"))
                events += NavEvent.Upcoming(next, ((dist / 50).roundToInt() * 50).coerceAtLeast(50))
            if (dist <= approachM && fire(i, "approach")) events += NavEvent.Approaching(next)
            if (dist <= nowM && fire(i, "now")) events += NavEvent.Now(next)
        }

        val status = NavStatus(
            remainingM = (totalM - s.alongM).coerceAtLeast(0.0),
            offRouteM = s.offRouteM,
            headingDeg = Geo.bearing(p, Geo.pointAhead(route, cum, s.alongM, 40.0)),
            nextTurn = next,
            nextTurnInM = dist?.coerceAtLeast(0.0),
        )
        return status to events
    }

    /** true beim ersten Aufruf für (Abbiegung, Stufe). */
    private fun fire(i: Int, stage: String) = fired.getOrPut(i) { mutableSetOf() }.add(stage)

    private fun wasAnnounced(i: Int) = fired[i]?.contains("approach") == true
}
