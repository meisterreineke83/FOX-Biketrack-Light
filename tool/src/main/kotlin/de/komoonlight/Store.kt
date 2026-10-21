package de.komoonlight

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.komoonlight.komoot.KomootClient
import kotlinx.coroutines.flow.first

/** Speichert User-ID + API-Token (nie das Passwort). */
class Store(private val ds: DataStore<Preferences>) {
    private val uid = stringPreferencesKey("komoot_uid")
    private val tok = stringPreferencesKey("komoot_token")
    private val name = stringPreferencesKey("komoot_name")

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
