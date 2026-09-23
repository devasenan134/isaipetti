package io.github.devasenan134.isaipetti.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Login details for the Navidrome server.
 *
 * The password itself is never stored. Subsonic accepts `token = md5(password + salt)`,
 * so we keep only the salt and token.
 */
data class Credentials(
    val server: String,
    val username: String,
    val salt: String,
    val token: String,
)

class SessionStore(private val context: Context) {
    private val server = stringPreferencesKey("server")
    private val username = stringPreferencesKey("username")
    private val salt = stringPreferencesKey("salt")
    private val token = stringPreferencesKey("token")

    private val _credentials = MutableStateFlow<Credentials?>(null)

    /** The logged-in account, or null when logged out. The UI watches this to pick login vs. main screen. */
    val credentials: StateFlow<Credentials?> = _credentials

    /** Reads the saved login once at app start. It's a tiny file, so blocking briefly is fine. */
    fun load() {
        val prefs = runBlocking { context.sessionDataStore.data.first() }
        _credentials.value = Credentials(
            server = prefs[server] ?: return,
            username = prefs[username] ?: return,
            salt = prefs[salt] ?: return,
            token = prefs[token] ?: return,
        )
    }

    suspend fun save(credentials: Credentials) {
        context.sessionDataStore.edit {
            it[server] = credentials.server
            it[username] = credentials.username
            it[salt] = credentials.salt
            it[token] = credentials.token
        }
        _credentials.value = credentials
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
        _credentials.value = null
    }
}
