package de.komoonlight

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightFileShare
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.LightVibrator
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.asKotlinResult
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import de.komoonlight.geo.Geo
import de.komoonlight.geo.Gpx
import de.komoonlight.geo.Pt
import de.komoonlight.komoot.KomootClient
import de.komoonlight.location.lightOsLocations
import de.komoonlight.nav.NavEvent
import de.komoonlight.nav.NavStatus
import de.komoonlight.nav.Navigator
import de.komoonlight.nav.VoiceAnnouncer
import de.komoonlight.nav.VoicePrompts
import de.komoonlight.ui.RouteView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class NavViewModel(
    private val client: KomootClient,
    private val session: KomootClient.Session,
    val tour: KomootClient.TourInfo,
    private val fileShare: LightFileShare,
    private val vibrator: LightVibrator,
    private val voice: VoiceAnnouncer,
    private val store: Store,
) : LightViewModel<Unit>() {

    val route = MutableStateFlow<List<Pt>>(emptyList())
    val position = MutableStateFlow<Pt?>(null)
    val status = MutableStateFlow<NavStatus?>(null)
    val message = MutableStateFlow<String?>("Lade Route…")
    val zoomM = MutableStateFlow(300.0)

    val recording = MutableStateFlow(false)
    val track = MutableStateFlow<List<Pt>>(emptyList())
    val trackDistanceM = MutableStateFlow(0.0)
    val voiceOn = MutableStateFlow(true)

    private var navigator: Navigator? = null
    private var gpsJob: Job? = null
    private var announcedStart = false

    init {
        viewModelScope.launch {
            voiceOn.value = store.voiceEnabled()
            voice.enabled = voiceOn.value
        }
        viewModelScope.launch {
            try {
                val r = client.route(session, tour.id)
                route.value = r.points
                navigator = Navigator(r.points, r.turns)
                message.value = if (navigator!!.turns.isEmpty()) "Ohne Abbiegehinweise" else null
            } catch (e: KomootClient.AuthException) {
                message.value = "Bitte neu anmelden"
            } catch (e: Exception) {
                message.value = "Route nicht geladen"
            }
        }
    }

    /** Startet GPS, sobald die Berechtigung da ist. Läuft weiter, bis der Screen verlassen wird. */
    fun startGps() {
        if (gpsJob != null) return
        gpsJob = viewModelScope.launch {
            lightOsLocations().collect { onPosition(it) }
        }
    }

    private fun onPosition(p: Pt) {
        position.value = p
        if (recording.value) {
            track.value.lastOrNull()?.let { trackDistanceM.value += Geo.dist(it, p) }
            track.value = track.value + p
        }
        val nav = navigator ?: return
        if (!announcedStart) { announcedStart = true; voice.play(VoicePrompts.START) }
        val (s, events) = nav.update(p)
        status.value = s
        events.forEach(::react)
    }

    /** Sprachansage + kurze Vibration (falls der Ton aus ist oder nicht gehört wird). */
    private fun react(e: NavEvent) {
        voice.announce(e)
        when (e) {
            is NavEvent.Upcoming -> vibrator.vibrate(200.milliseconds)
            is NavEvent.Now -> viewModelScope.launch {
                vibrator.vibrate(150.milliseconds); delay(300); vibrator.vibrate(150.milliseconds)
            }
            NavEvent.OffRoute -> vibrator.vibrate(700.milliseconds)
            else -> Unit
        }
    }

    fun toggleVoice() {
        voiceOn.value = !voiceOn.value
        voice.enabled = voiceOn.value
        viewModelScope.launch { store.setVoiceEnabled(voiceOn.value) }
    }

    fun zoom(dir: Int) { zoomM.value = (zoomM.value * if (dir > 0) 0.5 else 2.0).coerceIn(75.0, 5000.0) }

    fun toggleRecording() {
        if (recording.value) {
            recording.value = false
            saveTrack()?.let { message.value = "Gespeichert: $it" }
        } else {
            track.value = emptyList(); trackDistanceM.value = 0.0
            recording.value = true
            message.value = null
        }
    }

    /** Schreibt den Track in den geteilten Ordner – im Tool Manager unter „Aufzeichnungen“. */
    private fun saveTrack(): String? {
        val t = track.value
        if (t.size < 2) return null
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
        val file = "$stamp.gpx"
        fileShare.write("$TRACKS_DIR/$file") { Gpx.write(it, "${tour.name} ($stamp)", t) }
        return file
    }

    override fun onCleared() {
        if (recording.value) saveTrack()
        voice.release()
    }
}

class NavScreen(
    private val sealedActivity: SealedLightActivity,
    private val client: KomootClient,
    private val session: KomootClient.Session,
    private val tour: KomootClient.TourInfo,
) : LightScreen<Unit, NavViewModel>(sealedActivity) {

    override val viewModelClass: Class<NavViewModel> get() = NavViewModel::class.java
    override fun createViewModel() =
        NavViewModel(
            client, session, tour, lightContext.fileShare, sealedActivity.vibrator,
            VoiceAnnouncer(DefaultLightAudio(sealedActivity)), Store(lightContext.dataStore),
        )

    private var resumeCount by mutableIntStateOf(0)
    override fun willShow() { resumeCount++ }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val route by viewModel.route.collectAsState()
        val track by viewModel.track.collectAsState()
        val pos by viewModel.position.collectAsState()
        val status by viewModel.status.collectAsState()
        val message by viewModel.message.collectAsState()
        val zoom by viewModel.zoomM.collectAsState()
        val rec by viewModel.recording.collectAsState()
        val recDist by viewModel.trackDistanceM.collectAsState()
        val voiceOn by viewModel.voiceOn.collectAsState()

        // GPS-Berechtigung über LightOS prüfen/anfragen, dann starten
        val permission = rememberPermissionRequestLauncher(Manifest.permission.ACCESS_FINE_LOCATION)
        LaunchedEffect(resumeCount) {
            val granted = checkPermission(Manifest.permission.ACCESS_FINE_LOCATION).asKotlinResult
                .map { it.permissionResult == LightServiceMethod.GetPermission.Result.Granted }
                .getOrDefault(false)
            if (granted) viewModel.startGps() else permission?.launch()
        }

        LightTheme(colors = themeColors) {
            val colors = LightThemeTokens.colors
            Column(Modifier.fillMaxSize().background(colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(tour.name),
                    rightButton = LightBarButton.Text(if (voiceOn) "Ton an" else "Stumm", onClick = { viewModel.toggleVoice() }),
                )

                Header(status, pos, message, rec, recDist)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RouteView(route, track, pos, status?.headingDeg, zoom, colors.content, colors.background)
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp(), vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText("−", LightTextVariant.Title, modifier = Modifier.lightClickable { viewModel.zoom(-1) }.padding(8.dp))
                    LightText(
                        if (rec) "Stopp" else "Aufnehmen", LightTextVariant.Button,
                        modifier = Modifier.lightClickable { viewModel.toggleRecording() }.padding(8.dp),
                    )
                    LightText("+", LightTextVariant.Title, modifier = Modifier.lightClickable { viewModel.zoom(+1) }.padding(8.dp))
                }
            }
        }
    }

    @Composable
    private fun Header(status: NavStatus?, pos: Pt?, message: String?, rec: Boolean, recDist: Double) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp(), vertical = 8.dp)) {
            val turn = status?.nextTurn
            val d = status?.nextTurnInM
            when {
                pos == null -> LightText(message ?: "Warte auf GPS…", LightTextVariant.Heading)
                status == null -> LightText(message ?: "…", LightTextVariant.Heading)
                status.offRouteM > 50 -> LightText("Abseits: %.0f m".format(status.offRouteM), LightTextVariant.Heading)
                turn != null && d != null && d <= 300 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LightText(turn.kind.arrow, LightTextVariant.Title, maxLines = 1)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        LightText(if (d < 20) "jetzt" else "${(d / 10).roundToInt() * 10} m", LightTextVariant.Heading)
                        LightText(turn.kind.say, LightTextVariant.Copy)
                        turn.street?.let { LightText(it, LightTextVariant.Detail, lighten = true, maxLines = 1) }
                    }
                }
                else -> LightText("Noch %.1f km".format(status.remainingM / 1000), LightTextVariant.Heading)
            }
            if (rec) LightText("● Aufnahme · %.2f km".format(recDist / 1000), LightTextVariant.Detail)
            if (pos != null && message != null) LightText(message, LightTextVariant.Fine, lighten = true)
        }
    }
}
