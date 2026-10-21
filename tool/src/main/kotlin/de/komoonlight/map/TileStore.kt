package de.komoonlight.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Standard-Kacheln von openstreetmap.org – ohne API-Schlüssel.
 * Nutzungsregeln: eindeutiger User-Agent, Kacheln zwischenspeichern, kein Massen-Download
 * (vorab höchstens < 250 Kacheln ab Zoom 13) → https://operations.osmfoundation.org/policies/tiles/
 */
private fun osmUrl(z: Int, x: Int, y: Int) = "https://tile.openstreetmap.org/$z/$x/$y.png"

const val MAP_ATTRIBUTION = "© OpenStreetMap-Mitwirkende"

/**
 * Lädt Kartenkacheln, hält sie auf der Platte (für unterwegs ohne Netz) und im Speicher.
 * [version] zählt hoch, sobald eine neue Kachel bereitliegt – die Ansicht zeichnet dann neu.
 */
class TileStore(filesDir: File, private val userAgent: String) {

    private val root = File(filesDir, "tiles")
    private val dir = File(root, "osm")
    private val http = HttpClient(OkHttp)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val net = Semaphore(2)        // sichtbare Kacheln – höflich zum Kachelserver
    private val background = Semaphore(1) // Vorab-Laden, damit es sichtbare Kacheln nicht ausbremst
    private val inflight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    // 256er-Kachel in RGB_565 ≈ 128 KB
    private val mem = LruCache<String, ImageBitmap>(96)

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** null = alles gut, sonst kurzer Hinweis, warum keine Karte kommt. */
    private val _status = MutableStateFlow<String?>("Karte lädt…")
    val status: StateFlow<String?> = _status

    init {
        scope.launch {
            // Alte CARTO-Kacheln (v0.5/0.6) enthalten nur „API KEY REQUIRED“ → weg damit
            listOf("dark_all", "light_all").forEach { File(root, it).deleteRecursively() }
            cleanup(maxAgeDays = 60)
        }
    }

    /** Kachel aus dem Speicher – oder null und im Hintergrund laden. */
    fun get(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
        mem.get(key)?.let { return it }
        if (key !in failed && inflight.add(key)) {
            scope.launch {
                try {
                    val bytes = load(z, x, y) ?: return@launch
                    val img = decode(bytes)
                    if (img == null) _status.value = "Karte: Kachel unlesbar"
                    else { mem.put(key, img); _status.value = null; _version.value++ }
                } finally {
                    inflight.remove(key)
                }
            }
        }
        return null
    }

    /** Kacheln vorab auf die Platte laden (z. B. entlang der Route). */
    fun prefetch(tiles: List<Triple<Int, Int, Int>>) {
        scope.launch {
            for ((z, x, y) in tiles) {
                if (!file(z, x, y).isFile) load(z, x, y, background)
            }
        }
    }

    fun close() {
        scope.cancel()
        http.close()
    }

    private fun file(z: Int, x: Int, y: Int) = File(dir, "$z/$x/$y.png")

    private suspend fun load(z: Int, x: Int, y: Int, sem: Semaphore = net): ByteArray? {
        val file = file(z, x, y)
        if (file.isFile && file.length() > 0) return file.readBytes()
        return sem.withPermit {
            runCatching {
                val res = http.get(osmUrl(z, x, y)) { header(HttpHeaders.UserAgent, userAgent) }
                if (!res.status.isSuccess()) {
                    if (res.status.value == 404) failed += "$z/$x/$y"
                    if (sem === net) _status.value = "Karte: Server-Fehler ${res.status.value}"
                    return@runCatching null
                }
                res.readRawBytes().also { bytes ->
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
            }.onFailure {
                if (sem === net) _status.value = "Karte offline – keine Verbindung"
            }.getOrNull()
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }.getOrNull()

    private fun cleanup(maxAgeDays: Int) {
        val cutoff = System.currentTimeMillis() - maxAgeDays * 86_400_000L
        dir.walkBottomUp().forEach { f -> if (f.isFile && f.lastModified() < cutoff) f.delete() }
    }
}
