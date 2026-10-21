package de.komoonlight

import de.komoonlight.geo.Pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {

    @Test
    fun formatsKomootDate() {
        assertEquals("12.10.2026", formatDate("2026-10-12T09:31:00.000+02:00"))
        assertNull(formatDate(null))
        assertNull(formatDate("kaputt"))
    }

    @Test
    fun formatsDuration() {
        assertEquals("0:00 h", formatDuration(0))
        assertEquals("0:42 h", formatDuration(42 * 60_000L))
        assertEquals("2:05 h", formatDuration((2 * 60 + 5) * 60_000L))
    }

    @Test
    fun rideTimeFromTrack() {
        val t = listOf(Pt(0.0, 0.0, time = 1_000), Pt(0.0, 0.0, time = 61_000))
        assertEquals(60_000, rideMillis(t))
        assertEquals(0, rideMillis(emptyList()))
        assertEquals(0, rideMillis(listOf(Pt(0.0, 0.0))))
    }
}
