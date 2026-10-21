package de.komoonlight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import de.komoonlight.komoot.KomootClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {
    private val store = Store(dataStore)
    val client = KomootClient()

    val session = MutableStateFlow<KomootClient.Session?>(null)
    val tab = MutableStateFlow(KomootClient.TourType.PLANNED)
    val tours = MutableStateFlow<Map<KomootClient.TourType, List<KomootClient.TourInfo>>>(emptyMap())
    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val message = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            session.value = store.session()
            loading.value = false
            if (session.value != null) loadTours()
        }
    }

    fun login() = viewModelScope.launch {
        if (email.value.isBlank() || password.value.isEmpty()) { message.value = "E-Mail und Passwort eingeben"; return@launch }
        loading.value = true; message.value = null
        try {
            val s = client.login(email.value, password.value)
            store.save(s)
            password.value = ""
            session.value = s
            loadTours()
        } catch (e: KomootClient.AuthException) {
            message.value = "Anmeldung abgelehnt"
        } catch (e: Exception) {
            message.value = "Keine Verbindung"
        } finally { loading.value = false }
    }

    fun selectTab(t: KomootClient.TourType) {
        tab.value = t
        message.value = null
        if (tours.value[t] == null) loadTours()
    }

    fun loadTours() = viewModelScope.launch {
        val s = session.value ?: return@launch
        val type = tab.value
        loading.value = true; message.value = null
        try {
            val list = client.tours(s, type)
            tours.value = tours.value + (type to list)
            if (list.isEmpty()) message.value = "Keine Touren gefunden"
        } catch (e: KomootClient.AuthException) {
            store.save(null); session.value = null; message.value = "Bitte neu anmelden"
        } catch (e: Exception) {
            message.value = "Keine Verbindung"
        } finally { loading.value = false }
    }

    fun logout() = viewModelScope.launch {
        store.save(null); session.value = null; tours.value = emptyMap()
    }

    override fun onCleared() { client.close() }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel> get() = HomeViewModel::class.java
    override fun createViewModel() = HomeViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val session by viewModel.session.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val message by viewModel.message.collectAsState()

        LightTheme(colors = themeColors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Komoot"),
                    rightButton = if (session != null) LightBarButton.Text("Abmelden", onClick = { viewModel.logout() }) else null,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
                Column(Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
                    // Freie Fahrt geht immer – auch ohne Komoot-Anmeldung
                    LightText(
                        "▶ Einfach losfahren", LightTextVariant.Heading,
                        modifier = Modifier.fillMaxWidth()
                            .lightClickable { navigateTo<Unit>({ NavScreen(it, null) }) }
                            .padding(vertical = 12.dp),
                    )
                    LightText("Ohne Route, nur aufzeichnen", LightTextVariant.Detail, lighten = true,
                        modifier = Modifier.padding(bottom = 24.dp))
                    message?.let {
                        LightText(it, LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    when {
                        loading && session == null -> LightText("…", LightTextVariant.Copy)
                        session == null -> LoginForm()
                        else -> TourList(loading)
                    }
                }
            }
        }
    }

    @Composable
    private fun LoginForm() {
        val email by viewModel.email.collectAsState()
        val password by viewModel.password.collectAsState()
        val loading by viewModel.loading.collectAsState()

        LightTextField(
            label = "E-Mail:", value = email, placeholder = "deine@mail.de",
            onClick = { edit("E-Mail", email) { viewModel.email.value = it.trim() } },
        )
        Spacer(Modifier.height(16.dp))
        LightTextField(
            label = "Passwort:", value = "•".repeat(password.length), placeholder = "Passwort",
            onClick = { edit("Passwort", "") { viewModel.password.value = it } },
        )
        Spacer(Modifier.height(32.dp))
        LightText(
            if (loading) "Anmelden…" else "Anmelden", LightTextVariant.Button,
            modifier = Modifier.lightClickable(enabled = !loading) { viewModel.login() }.padding(vertical = 12.dp),
        )
        LightText(
            "Das Passwort wird nicht gespeichert, nur ein Komoot-Token.",
            LightTextVariant.Fine, lighten = true, modifier = Modifier.padding(top = 16.dp),
        )
    }

    @Composable
    private fun TourList(loading: Boolean) {
        val all by viewModel.tours.collectAsState()
        val tab by viewModel.tab.collectAsState()
        val tours = all[tab].orEmpty()

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            for (t in KomootClient.TourType.entries) {
                LightText(
                    if (t == KomootClient.TourType.PLANNED) "Geplant" else "Gefahren",
                    LightTextVariant.Subheading,
                    lighten = t != tab,
                    underline = t == tab,
                    modifier = Modifier.lightClickable { viewModel.selectTab(t) }.padding(end = 24.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
        if (loading && tours.isEmpty()) LightText("Lade Touren…", LightTextVariant.Copy)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(tours, key = { it.id }) { t ->
                Column(
                    Modifier.fillMaxWidth()
                        .lightClickable {
                            val s = viewModel.session.value ?: return@lightClickable
                            navigateTo<Unit>({ NavScreen(it, TourRef(viewModel.client, s, t)) })
                        }
                        .padding(vertical = 12.dp)
                ) {
                    LightText(t.name, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LightText(
                        listOfNotNull(formatDate(t.date), "%.1f km".format(t.distanceM / 1000)).joinToString(" · "),
                        LightTextVariant.Detail, lighten = true,
                    )
                }
            }
        }
    }

    private fun edit(title: String, initial: String, onResult: (String) -> Unit) {
        navigateTo<String>({ TextEditorScreen(it, title, initial) }) { result -> onResult(result) }
    }
}
