package de.komoonlight

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

/** Ordner im geteilten Bereich, den der Tool Manager im Browser anzeigt. */
const val TRACKS_DIR = "tracks"

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    /**
     * Macht die aufgezeichneten GPX-Dateien im LightOS Tool Manager (Browser am Rechner)
     * herunterladbar – von dort direkt in Komoot hochladen.
     */
    override fun getToolManagerManifest() = ClientToolManifest(
        title = "Komoot",
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(
                    label = "Aufzeichnungen",
                    path = TRACKS_DIR,
                    headerText = "GPX-Dateien deiner Fahrten. Herunterladen und in Komoot unter „Aktivität hochladen“ importieren.",
                )
            )
        ),
    )
}
