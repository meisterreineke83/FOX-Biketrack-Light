package de.komoonlight.nav

/**
 * Ordnet Navigationsereignisse den mitgelieferten Sprachclips zu
 * (tool/src/main/assets/voice, erzeugt mit tools/voice/generate.py).
 */
object VoicePrompts {
    const val DIR = "voice"
    val DISTANCES = listOf(50, 100, 150, 200)
    const val START = "$DIR/start.wav"

    /** Asset-Pfad für [e] oder null, wenn dazu nichts gesagt wird. */
    fun clipFor(e: NavEvent): String? = when (e) {
        is NavEvent.Upcoming -> speakable(e.turn)?.let { "$DIR/up_${nearest(e.meters)}_$it.wav" }
        is NavEvent.Now -> speakable(e.turn)?.let { "$DIR/now_$it.wav" }
        NavEvent.OffRoute -> "$DIR/off_route.wav"
        NavEvent.BackOnRoute -> "$DIR/back_on_route.wav"
        is NavEvent.Approaching, is NavEvent.Passed -> null
    }

    /** Alle Clips, die [clipFor] liefern kann – für den Vollständigkeitstest. */
    fun all(): List<String> =
        Kind.entries.filter { it != Kind.STRAIGHT }.flatMap { k ->
            val n = k.name.lowercase()
            DISTANCES.map { "$DIR/up_${it}_$n.wav" } + "$DIR/now_$n.wav"
        } + listOf("$DIR/off_route.wav", "$DIR/back_on_route.wav", START)

    private fun speakable(t: Turn) = t.kind.takeIf { it != Kind.STRAIGHT }?.name?.lowercase()

    private fun nearest(m: Int) = DISTANCES.minBy { kotlin.math.abs(it - m) }
}
