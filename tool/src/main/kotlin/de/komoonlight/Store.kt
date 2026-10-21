package de.komoonlight

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.komoonlight.komoot.KomootClient
import kotlinx.coroutines.flow.first

/** Speichert User-ID + API-Token (nie das Passwort) und Einstellungen. */
class Store(private val ds: DataStore<Preferences>) {
    private val uid = stringPreferencesKey("komoot_uid")
    private val tok = stringPreferencesKey("komoot_token")
    private val name = stringPreferencesKey("komoot_name")
    private val voice = booleanPreferencesKey("voice_enabled")
    private val uploaded = stringSetPreferencesKey("uploaded_tracks")
    private val map = booleanPreferencesKey("map_enabled")

    suspend fun mapEnabled(): Boolean = ds.data.first()[map] ?: true
    suspend fun setMapEnabled(on: Boolean) { ds.edit { it[map] = on } }

    /** Dateinamen der Aufzeichnungen, die schon bei Komoot sind. */
    suspend fun uploadedTracks(): Set<String> = ds.data.first()[uploaded].orEmpty()
    suspend fun markUploaded(file: String) { ds.edit { it[uploaded] = it[uploaded].orEmpty() + file } }

    suspend fun voiceEnabled(): Boolean = ds.data.first()[voice] ?: true
    suspend fun setVoiceEnabled(on: Boolean) { ds.edit { it[voice] = on } }

    suspend fun session(): KomootClient.Session? {
        val p = ds.data.first()
        val id = p[uid] ?: return null
        val t = p[tok] ?: return null
        return KomootClient.Session(id, t, p[name] ?: "")
    }

    suspend fun save(s: KomootClient.Session?) {
        ds.edit {
            if (s == null) { it.remove(uid); it.remove(tok); it.remove(name) }
            else { it[uid] = s.userId; it[tok] = s.token; it[name] = s.displayName }
        }
    }
}
