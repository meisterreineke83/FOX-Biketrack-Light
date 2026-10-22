package de.komoonlight.location

import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightResult
import com.thelightphone.sdk.shared.LightServiceMethod
import de.komoonlight.geo.Pt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Was der GPS-Flow liefert: eine neue Position oder (solange keine kommt) den Grund dafür. */
sealed interface GpsEvent {
    /** Neue Position von LightOS mit ihrer Genauigkeit in Metern. */
    data class Fix(val pt: Pt, val accuracyM: Double) : GpsEvent
    /** Kein neuer brauchbarer Fix – [reason] erklärt kurz, warum (für die Anzeige). */
    data class Waiting(val reason: String) : GpsEvent
}

/**
 * GPS über LightOS: LightOS misst selbst, das Tool fragt den letzten Wert ab.
 * Solange der Flow gesammelt wird, hält er die Messung aktiv (LightOS will alle < 1 min ein "weiter so").
 *
 * Liefert jeden neuen Fix bis [maxAccuracyM] (die Aufzeichnung filtert selbst strenger) und
 * sonst regelmäßig [GpsEvent.Waiting] mit dem Grund – früher wurde still gewartet, man sah nur
 * „Warte auf GPS…“, ohne zu wissen, ob LightOS gar nichts liefert, nur ungenau misst oder einen Fehler meldet.
 */
fun lightOsLocations(
    poll: Duration = 2.seconds,
    maxAccuracyM: Double = 100.0,
): Flow<GpsEvent> = flow {
    coroutineScope {
        var requestError: String? = null
        val keepAlive = launch {
            while (isActive) {
                val r = callRemoteServiceMethod(LightServiceMethod.RequestLocationUpdates, Unit)
                requestError = (r as? LightResult.Error)?.let { describe(it) }
                // Klappt das Anmelden nicht, schnell erneut versuchen statt 30 s zu warten
                delay(if (requestError == null) 30.seconds else 5.seconds)
            }
        }
        var lastTs = 0L
        var lastFixAt = 0L
        try {
            while (isActive) {
                val now = System.currentTimeMillis()
                val waiting: String? = when (val r = callRemoteServiceMethod(LightServiceMethod.GetCurrentLocation, Unit)) {
                    is LightResult.Error -> "LightOS: ${describe(r)}"
                    is LightResult.Success -> {
                        val l = r.data
                        val lat = l.latitude; val lon = l.longitude; val ts = l.timestampMs
                        val acc = l.accuracyMeters
                        when {
                            lat == null || lon == null || ts == null ->
                                requestError?.let { "LightOS: $it" } ?: "Noch keine Position von LightOS"
                            ts <= lastTs -> {
                                // Gleicher Fix wie zuletzt: erst nach einer Weile als Problem melden
                                if (now - lastFixAt > 15_000) "Kein neuer Fix seit ${ageText(now - ts)}" else null
                            }
                            acc != null && acc > maxAccuracyM -> {
                                lastTs = ts
                                "Zu ungenau (±${acc.toInt()} m) – freie Sicht zum Himmel hilft"
                            }
                            else -> {
                                lastTs = ts; lastFixAt = now
                                emit(GpsEvent.Fix(Pt(lat, lon, null, ts), acc ?: maxAccuracyM))
                                null
                            }
                        }
                    }
                }
                if (waiting != null) emit(GpsEvent.Waiting(waiting))
                delay(poll)
            }
        } finally {
            keepAlive.cancel()
            withContext(NonCancellable) {
                callRemoteServiceMethod(LightServiceMethod.ReleaseLocationUpdates, Unit)
            }
        }
    }
}

private fun describe(e: LightResult.Error): String =
    listOfNotNull(e.code.name, e.extra?.takeIf { it.isNotBlank() }).joinToString(": ")

private fun ageText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 120) "$s s" else "${s / 60} min"
}
