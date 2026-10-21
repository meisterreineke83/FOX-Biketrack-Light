package de.komoonlight.nav

import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.LightAudioItem
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.audio.LightAudioSource
import com.thelightphone.sdk.audio.LightAudioUsage
import com.thelightphone.sdk.audio.LightMediaMetadata
import com.thelightphone.sdk.audio.LightPlayerSpec

/**
 * Spielt die Sprachclips über den Audio-Player des Light-SDK ab.
 * Eine neue Ansage unterbricht eine laufende – die aktuellere ist wichtiger.
 */
class VoiceAnnouncer(audio: LightAudio) {

    private val player: LightAudioPlayer =
        audio.newPlayer(LightPlayerSpec(usage = LightAudioUsage.Speech))

    var enabled = true

    fun announce(e: NavEvent) {
        VoicePrompts.clipFor(e)?.let(::play)
    }

    fun play(assetPath: String) {
        if (!enabled) return
        player.setMediaQueue(
            listOf(
                LightAudioItem(
                    source = LightAudioSource.AssetSource(assetPath),
                    metadata = LightMediaMetadata(title = "Navigation"),
                    id = assetPath,
                )
            )
        )
        player.play()
    }

    fun release() = player.release()
}
