package space.neongram.app.core.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "neongram_session")

data class StoredSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val serverUrl: String = "https://cloud.neongram.space"
) {
    val isAuthenticated: Boolean
        get() = accessToken.isNotBlank() && userId.isNotBlank()
}

class SessionStore(private val context: Context) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }

    val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
        StoredSession(
            accessToken = prefs[KEY_ACCESS_TOKEN] ?: "",
            refreshToken = prefs[KEY_REFRESH_TOKEN] ?: "",
            userId = prefs[KEY_USER_ID] ?: "",
            username = prefs[KEY_USERNAME] ?: "",
            displayName = prefs[KEY_DISPLAY_NAME] ?: "",
            serverUrl = prefs[KEY_SERVER_URL] ?: "https://cloud.neongram.space"
        )
    }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        username: String,
        displayName: String,
        serverUrl: String = "https://cloud.neongram.space"
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USERNAME] = username
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_SERVER_URL] = serverUrl.trimEnd('/')
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_DISPLAY_NAME)
        }
    }
}
