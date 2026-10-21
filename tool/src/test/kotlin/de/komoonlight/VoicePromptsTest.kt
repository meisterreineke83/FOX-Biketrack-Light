package de.komoonlight

import de.komoonlight.nav.NavEvent
import de.komoonlight.nav.Turn
import de.komoonlight.nav.VoicePrompts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoicePromptsTest {

    // Gradle startet Unit-Tests im Modulordner (tool/)
    private val assets = File("src/main/assets")

    @Test
    fun everyClipExists() {
        val missing = VoicePrompts.all().filterNot { File(assets, it).isFile }
        assertTrue(missing.isEmpty(), "Fehlende Sprachclips (tools/voice/generate.py ausführen): $missing")
    }

    @Test
    fun noUnusedClips() {
        val used = VoicePrompts.all().toSet()
        val present = File(assets, VoicePrompts.DIR).list().orEmpty().map { "${VoicePrompts.DIR}/$it" }
        assertEquals(emptyList(), present.filterNot { it in used })
    }

    @Test
    fun mapsEventsToClips() {
        val right = Turn(1, "TR", null)
        assertEquals("voice/up_200_right.wav", VoicePrompts.clipFor(NavEvent.Upcoming(right, 200)))
        assertEquals("voice/up_150_right.wav", VoicePrompts.clipFor(NavEvent.Upcoming(right, 140)))
        assertEquals("voice/now_right.wav", VoicePrompts.clipFor(NavEvent.Now(right)))
        assertEquals("voice/now_arrive.wav", VoicePrompts.clipFor(NavEvent.Now(Turn(1, "D", null))))
        assertEquals("voice/off_route.wav", VoicePrompts.clipFor(NavEvent.OffRoute))
        assertNull(VoicePrompts.clipFor(NavEvent.Approaching(right)))
        assertNull(VoicePrompts.clipFor(NavEvent.Now(Turn(1, "S", null))))
    }
}
