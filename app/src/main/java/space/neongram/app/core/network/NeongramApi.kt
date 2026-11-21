package space.neongram.app.core.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import space.neongram.app.core.model.*
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class NeongramApi(
    var baseUrl: String = "https://cloud.neongram.space"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun apiUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$cleanBase/api$cleanPath"
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("username", username.trim())
                put("password", password)
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("clientPlatform", "android-kotlin")
                put("clientOsName", "Android")
                put("clientOsVersion", Build.VERSION.RELEASE ?: "14")
                put("clientDeviceType", "mobile")
                put("clientAppVersion", "1.0.0-kotlin")
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/auth/login"))
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { json.decodeFromString<AuthResponse>(bodyText).error }.getOrNull()
                    throw IllegalStateException(err ?: "HTTP ${response.code}: Ошибка входа")
                }
                json.decodeFromString<AuthResponse>(bodyText)
            }
        }
    }

    suspend fun register(username: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("username", username.trim())
                put("password", password)
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("clientPlatform", "android-kotlin")
                put("clientOsName", "Android")
                put("clientOsVersion", Build.VERSION.RELEASE ?: "14")
                put("clientDeviceType", "mobile")
                put("clientAppVersion", "1.0.0-kotlin")
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/auth/register"))
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { json.decodeFromString<AuthResponse>(bodyText).error }.getOrNull()
                    throw IllegalStateException(err ?: "HTTP ${response.code}: Ошибка регистрации")
                }
                json.decodeFromString<AuthResponse>(bodyText)
            }
        }
    }

    suspend fun getMe(accessToken: String): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/users/me"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                json.decodeFromString<MeResponse>(bodyText).user
                    ?: throw IllegalStateException("Empty user profile")
            }
        }
    }

    suspend fun getChats(accessToken: String): Result<List<ChatDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/chats"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: Не удалось загрузить чаты")
                }
                json.decodeFromString<ChatsListResponse>(bodyText).chats
            }
        }
    }

    suspend fun getChatMessages(accessToken: String, chatId: String, take: Int = 120): Result<List<MessageDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/chats/$chatId/messages?take=$take"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: Не удалось загрузить сообщения")
                    }
                    json.decodeFromString<MessagesListResponse>(bodyText).messages
                }
            }
        }

    suspend fun sendMessage(
        accessToken: String,
        chatId: String,
        content: String,
        replyToId: String? = null
    ): Result<MessageDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("content", content)
                put("type", "TEXT")
                put("clientMessageId", UUID.randomUUID().toString())
                if (!replyToId.isNullOrBlank()) {
                    put("replyToId", replyToId)
                }
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/messages/chat/$chatId"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: Ошибка отправки")
                }
                json.decodeFromString<SendMessageResponse>(bodyText).message
                    ?: throw IllegalStateException("Пустой ответ сервера")
            }
        }
    }

    suspend fun searchUsers(accessToken: String, query: String): Result<List<UserDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val request = Request.Builder()
                    .url(apiUrl("/users/search?username=$encoded&limit=25"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    json.decodeFromString<UsersSearchResponse>(bodyText).users
                }
            }
        }

    suspend fun createDirectChat(accessToken: String, userId: String): Result<ChatDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("userId", userId)
                }.toString()

                val request = Request.Builder()
                    .url(apiUrl("/chats/direct"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    json.decodeFromString<CreateChatResponse>(bodyText).chat
                        ?: throw IllegalStateException("Чат не создан")
                }
            }
        }

    suspend fun updateProfile(
        accessToken: String,
        displayName: String,
        bio: String
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("displayName", displayName.trim())
                put("bio", bio.trim())
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/users/me"))
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                json.decodeFromString<MeResponse>(bodyText).user
                    ?: throw IllegalStateException("Не удалось обновить профиль")
            }
        }
    }
}
