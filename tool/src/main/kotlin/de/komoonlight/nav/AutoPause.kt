package de.komoonlight.nav

import de.komoonlight.geo.Geo
import de.komoonlight.geo.Pt

/**
 * Erkennt Stillstand: Bewegt man sich [stopAfterMs] lang nicht weiter als [radiusM]
 * (GPS-Rauschen), gilt die Fahrt als pausiert – bis man den Radius wieder verlässt.
 * Zählt dabei die reine Bewegungszeit.
 */
class AutoPause(
    private val radiusM: Double = 10.0,
    private val stopAfterMs: Long = 5_000,
) {
    var paused = false
        private set

    /** Bewegungszeit in ms (Pausen zählen nicht). */
    var movingMs = 0L
        private set

    private var anchor: Pt? = null
    private var last: Pt? = null

    /**
     * Neuer GPS-Punkt (mit Zeitstempel). Liefert true, wenn der Punkt zur Aufzeichnung gehört,
     * false während einer Pause.
     */
    fun update(p: Pt): Boolean {
        val t = p.time ?: return !paused
        val a = anchor
        if (a == null || a.time == null) {
            anchor = p; last = p
            return true
        }
        if (Geo.dist(a, p) <= radiusM) {
            // steht (noch) – nach stopAfterMs pausieren
            if (!paused && t - a.time >= stopAfterMs) paused = true
        } else {
            // bewegt sich: neuer Bezugspunkt, Pause vorbei
            anchor = p
            paused = false
        }
        if (!paused) last?.time?.let { lt -> if (t > lt) movingMs += t - lt }
        last = p
        return !paused
    }

    fun reset() {
        paused = false; movingMs = 0; anchor = null; last = null
    }
}
