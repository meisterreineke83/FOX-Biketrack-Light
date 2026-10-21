package de.komoonlight.komoot

import de.komoonlight.geo.Pt
import de.komoonlight.nav.Turn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Inoffizieller Komoot-Client – dieselben Endpunkte wie KomootGPX.
 * Nur für den privaten Gebrauch mit dem eigenen Account.
 */
class KomootClient {

    data class Session(val userId: String, val token: String, val displayName: String)
    data class TourInfo(
        val id: String,
        val name: String,
        val planned: Boolean,
        val sport: String,
        val distanceM: Double,
        /** ISO-Datum, z. B. 2026-10-12T09:31:00.000+02:00 */
        val date: String? = null,
    )

    enum class TourType(val apiValue: String) { PLANNED("tour_planned"), RECORDED("tour_recorded") }
    data class Route(val points: List<Pt>, val turns: List<Turn>)

    class AuthException(msg: String) : Exception(msg)
    class KomootException(msg: String) : Exception(msg)

    private val http = HttpClient(OkHttp)
    private val json = Json { ignoreUnknownKeys = true }

    /** Login mit E-Mail + Passwort → User-ID + API-Token. Gespeichert wird nur der Token. */
    suspend fun login(email: String, password: String): Session {
        val o = get("https://api.komoot.de/v006/account/email/${email.trim().encodeURLPathPart()}/", email.trim(), password)
        return Session(
            userId = o.str("username") ?: throw KomootException("Unerwartete Antwort"),
            token = o.str("password") ?: throw KomootException("Unerwartete Antwort"),
            displayName = o["user"]?.jsonObject?.str("displayname") ?: "",
        )
    }

    /** Touren eines Typs (geplant/gefahren), neueste zuerst. */
    suspend fun tours(s: Session, type: TourType, page: Int = 0): List<TourInfo> {
        val o = get(
            "https://api.komoot.de/v007/users/${s.userId}/tours/" +
                "?type=${type.apiValue}&page=$page&limit=50&sort_field=date&sort_direction=desc",
            s.userId, s.token,
        )
        val arr = o["_embedded"]?.jsonObject?.get("tours")?.jsonArray ?: return emptyList()
        return arr.map { it.jsonObject }.map { t ->
            TourInfo(
                id = t["id"]!!.jsonPrimitive.content,
                name = t.str("name") ?: "Tour",
                planned = t.str("type") == "tour_planned",
                sport = t.str("sport") ?: "",
                distanceM = t["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                date = t.str("date"),
            )
        }.filter { it.planned == (type == TourType.PLANNED) } // falls die API den Filter ignoriert
    }

    /** Geometrie + Abbiegehinweise. Gefahrene Touren haben meist keine Hinweise. */
    suspend fun route(s: Session, tourId: String): Route {
        val o = get("https://api.komoot.de/v007/tours/$tourId?_embedded=coordinates,directions", s.userId, s.token)
        return parseRoute(o)
    }

    /**
     * Lädt eine aufgezeichnete Fahrt als „gefahrene“ Tour hoch (wie der GPX-Import auf komoot.com)
     * und setzt danach Name + Sportart. Liefert die neue Tour-ID.
     */
    suspend fun uploadRecorded(s: Session, gpx: String, name: String, sport: String = "touringbicycle"): String =
        withContext(Dispatchers.IO) {
            val res = http.post("https://api.komoot.de/v007/tours/?data_type=gpx") {
                auth(s.userId, s.token)
                header(HttpHeaders.Accept, "application/hal+json")
                setBody(TextContent(gpx, ContentType("application", "gpx+xml")))
            }
            check(res, "Hochladen")
            val id = json.parseToJsonElement(res.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content
                ?: throw KomootException("Upload ohne Tour-ID")

            // Name/Sport sind kosmetisch – scheitert das, ist die Fahrt trotzdem gespeichert
            runCatching {
                http.patch("https://api.komoot.de/v007/tours/$id") {
                    auth(s.userId, s.token)
                    setBody(TextContent(
                        buildJsonObject { put("name", name); put("sport", sport) }.toString(),
                        ContentType.Application.Json,
                    ))
                }
            }
            id
        }

    fun close() = http.close()

    private fun HttpRequestBuilder.auth(user: String, pass: String) {
        val b64 = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
        header(HttpHeaders.Authorization, "Basic $b64")
    }

    private suspend fun check(res: HttpResponse, what: String) {
        when (res.status.value) {
            in 200..299 -> Unit
            401, 403 -> throw AuthException("Anmeldung abgelehnt")
            else -> throw KomootException("$what fehlgeschlagen (${res.status.value}): ${res.bodyAsText().take(200)}")
        }
    }

    private suspend fun get(url: String, user: String, pass: String): JsonObject = withContext(Dispatchers.IO) {
        val res: HttpResponse = http.get(url) {
            auth(user, pass)
            header(HttpHeaders.Accept, "application/hal+json")
        }
        when (res.status.value) {
            200 -> json.parseToJsonElement(res.bodyAsText()).jsonObject
            401, 403 -> throw AuthException("Anmeldung abgelehnt")
            else -> throw KomootException("Komoot-Fehler ${res.status.value}")
        }
    }

    companion object {
        /** Getrennt, damit es ohne Netzwerk testbar ist. */
        fun parseRoute(o: JsonObject): Route {
            val emb = o["_embedded"]?.jsonObject ?: throw KomootException("Keine Routendaten")
            val points = emb["coordinates"]!!.jsonObject["items"]!!.jsonArray.map {
                val c = it.jsonObject
                Pt(
                    lat = c["lat"]!!.jsonPrimitive.doubleOrNull!!,
                    lon = c["lng"]!!.jsonPrimitive.doubleOrNull!!,
                    alt = c["alt"]?.jsonPrimitive?.doubleOrNull,
                )
            }
            val turns = emb["directions"]?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull {
                val d = it.jsonObject
                val idx = d["index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                if (idx !in points.indices) null
                else Turn(idx, d.str("type") ?: "", d.str("street_name")?.takeIf { s -> s.isNotBlank() })
            }
            return Route(points, turns)
        }

        private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    }
}
