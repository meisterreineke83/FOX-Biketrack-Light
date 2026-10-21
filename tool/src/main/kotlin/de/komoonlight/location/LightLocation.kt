package de.komoonlight.location

import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.getOrNull
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

/**
 * GPS über LightOS: LightOS misst selbst, das Tool fragt den letzten Wert ab.
 * Solange der Flow gesammelt wird, hält er die Messung aktiv (LightOS will alle < 1 min ein "weiter so").
 * Liefert nur neue Fixes mit brauchbarer Genauigkeit.
 */
fun lightOsLocations(
    poll: Duration = 2.seconds,
    maxAccuracyM: Double = 30.0,
): Flow<Pt> = flow {
    coroutineScope {
        val keepAlive = launch {
            while (isActive) {
                callRemoteServiceMethod(LightServiceMethod.RequestLocationUpdates, Unit)
                delay(30.seconds)
            }
        }
        var lastTs = 0L
        try {
            while (isActive) {
                val l = callRemoteServiceMethod(LightServiceMethod.GetCurrentLocation, Unit).getOrNull()
                val lat = l?.latitude; val lon = l?.longitude; val ts = l?.timestampMs
                if (lat != null && lon != null && ts != null && ts > lastTs &&
                    (l.accuracyMeters ?: Double.MAX_VALUE) <= maxAccuracyM
                ) {
                    lastTs = ts
                    emit(Pt(lat, lon, null, ts))
                }
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
