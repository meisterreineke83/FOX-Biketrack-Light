package de.komoonlight

import de.komoonlight.geo.Pt
import de.komoonlight.nav.AutoPause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPauseTest {

    private val start = Pt(47.55, 7.79)
    /** Punkt [m] Meter nördlich vom Start zur Zeit [s] Sekunden. */
    private fun at(m: Double, s: Int) = Pt(start.lat + m / 111_320.0, start.lon, time = s * 1000L)

    @Test
    fun ridingIsNeverPaused() {
        val a = AutoPause()
        for (s in 0..60 step 2) assertTrue(a.update(at(s * 5.0, s))) // 5 m/s
        assertFalse(a.paused)
        assertEquals(60_000, a.movingMs)
    }

    @Test
    fun pausesAfterFiveSecondsStandingAndResumes() {
        val a = AutoPause()
        a.update(at(0.0, 0)); a.update(at(20.0, 2))       // fährt
        assertTrue(a.update(at(22.0, 4)))                // hält an …
        assertTrue(a.update(at(23.0, 6)))                // 2 s – noch nicht
        assertFalse(a.update(at(21.0, 10)))              // 6 s am selben Fleck → Pause
        assertTrue(a.paused)
        assertFalse(a.update(at(24.0, 40)))              // GPS-Rauschen hält die Pause
        assertTrue(a.update(at(60.0, 42)))               // fährt weiter
        assertFalse(a.paused)
    }

    @Test
    fun pauseTimeIsNotCounted() {
        val a = AutoPause()
        a.update(at(0.0, 0)); a.update(at(20.0, 2))       // 2 s Fahrt
        a.update(at(21.0, 4)); a.update(at(21.0, 8))      // Stopp → Pause ab 8 s
        a.update(at(21.0, 300))                          // 5 Minuten Pause
        a.update(at(50.0, 302))                          // weiter
        assertTrue(a.movingMs < 15_000, "Bewegungszeit ${a.movingMs} ms")
    }

    @Test
    fun resetStartsOver() {
        val a = AutoPause()
        a.update(at(0.0, 0)); a.update(at(0.0, 10))
        assertTrue(a.paused)
        a.reset()
        assertFalse(a.paused)
        assertEquals(0, a.movingMs)
    }
}
