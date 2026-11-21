package space.neongram.app.core.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val serverUrl: String = "https://cloud.neongram.space",
    val onboardingCompleted: Boolean = false,
    val language: String = "ru",
    val themeId: String = "cyber_dark",
    val useSystemTheme: Boolean = false,
    val fontSize: String = "medium",
    val bubbleColorHex: String = "",
    val bubbleScope: String = "mine",
    val wallpaper: String = "gradient",
    val pinCode: String = "",
    val lockMode: String = "none",
    val autoLockMinutes: Int = 5
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
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_THEME_ID = stringPreferencesKey("theme_id")
        private val KEY_USE_SYSTEM_THEME = booleanPreferencesKey("use_system_theme")
        private val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        private val KEY_BUBBLE_COLOR = stringPreferencesKey("bubble_color_hex")
        private val KEY_BUBBLE_SCOPE = stringPreferencesKey("bubble_scope")
        private val KEY_WALLPAPER = stringPreferencesKey("wallpaper")
        private val KEY_PIN_CODE = stringPreferencesKey("pin_code")
        private val KEY_LOCK_MODE = stringPreferencesKey("lock_mode")
        private val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
    }

    val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
        val pin = prefs[KEY_PIN_CODE] ?: ""
        StoredSession(
            accessToken = prefs[KEY_ACCESS_TOKEN] ?: "",
            refreshToken = prefs[KEY_REFRESH_TOKEN] ?: "",
            userId = prefs[KEY_USER_ID] ?: "",
            username = prefs[KEY_USERNAME] ?: "",
            displayName = prefs[KEY_DISPLAY_NAME] ?: "",
            serverUrl = prefs[KEY_SERVER_URL] ?: "https://cloud.neongram.space",
            onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: false,
            language = prefs[KEY_LANGUAGE] ?: "ru",
            themeId = prefs[KEY_THEME_ID] ?: "cyber_dark",
            useSystemTheme = prefs[KEY_USE_SYSTEM_THEME] ?: false,
            fontSize = prefs[KEY_FONT_SIZE] ?: "medium",
            bubbleColorHex = prefs[KEY_BUBBLE_COLOR] ?: "",
            bubbleScope = prefs[KEY_BUBBLE_SCOPE] ?: "mine",
            wallpaper = prefs[KEY_WALLPAPER] ?: "gradient",
            pinCode = pin,
            lockMode = prefs[KEY_LOCK_MODE] ?: if (pin.isNotBlank()) "pin" else "none",
            autoLockMinutes = prefs[KEY_AUTO_LOCK_MINUTES] ?: 5
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
            prefs[KEY_ONBOARDING_DONE] = true
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE] = true
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = lang.lowercase()
        }
    }

    suspend fun updateAppearance(
        themeId: String? = null,
        useSystemTheme: Boolean? = null,
        fontSize: String? = null,
        bubbleColorHex: String? = null,
        bubbleScope: String? = null,
        wallpaper: String? = null
    ) {
        context.dataStore.edit { prefs ->
            themeId?.let { prefs[KEY_THEME_ID] = it }
            useSystemTheme?.let { prefs[KEY_USE_SYSTEM_THEME] = it }
            fontSize?.let { prefs[KEY_FONT_SIZE] = it }
            bubbleColorHex?.let { prefs[KEY_BUBBLE_COLOR] = it }
            bubbleScope?.let { prefs[KEY_BUBBLE_SCOPE] = it }
            wallpaper?.let { prefs[KEY_WALLPAPER] = it }
        }
    }

    suspend fun setPinCode(pin: String) {
        val clean = pin.trim()
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_CODE] = clean
            prefs[KEY_LOCK_MODE] = if (clean.isNotBlank()) "pin" else "none"
        }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_LOCK_MINUTES] = minutes.coerceAtLeast(1)
        }
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url.trimEnd('/')
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
