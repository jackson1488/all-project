package space.neongram.app.core.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

    suspend fun checkHealth(targetUrl: String = baseUrl): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = targetUrl.trimEnd('/')
            val request = Request.Builder()
                .url("$clean/api/health")
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
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
                    throw IllegalStateException(err ?: "HTTP ${response.code}: Неверный логин или пароль")
                }
                json.decodeFromString<AuthResponse>(bodyText)
            }
        }
    }

    suspend fun completeTwoFactorLogin(challengeId: String, code: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("challengeId", challengeId)
                put("code", code.trim())
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/auth/login/2fa"))
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { json.decodeFromString<AuthResponse>(bodyText).error }.getOrNull()
                    throw IllegalStateException(err ?: "HTTP ${response.code}: Неверный 2FA код")
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

    suspend fun startAuthQr(): Result<QrStartResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            }.toString()
            val request = Request.Builder()
                .url(apiUrl("/auth/qr/start"))
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<QrStartResponse>(bodyText)
            }
        }
    }

    suspend fun approveAuthQr(accessToken: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("token", token.trim()) }.toString()
            val request = Request.Builder()
                .url(apiUrl("/auth/qr/approve"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
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
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<MeResponse>(bodyText).user
                    ?: throw IllegalStateException("Empty user profile")
            }
        }
    }

    suspend fun getUserById(accessToken: String, userId: String): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/users/$userId"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<MeResponse>(bodyText).user
                    ?: throw IllegalStateException("User not found")
            }
        }
    }

    suspend fun findUserByUsername(accessToken: String, username: String): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8")
            val request = Request.Builder()
                .url(apiUrl("/users/find?username=$encoded"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("Пользователь не найден")
                json.decodeFromString<MeResponse>(bodyText).user
                    ?: throw IllegalStateException("Пользователь не найден")
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
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<ChatsListResponse>(bodyText).chats
            }
        }
    }

    suspend fun updateChatPreferences(
        accessToken: String,
        chatId: String,
        pinned: Boolean? = null,
        muteMinutes: Int? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                pinned?.let { put("pinned", it) }
                muteMinutes?.let { put("muteMinutes", it) }
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/chats/$chatId/preferences"))
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun clearChatForEveryone(accessToken: String, chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/chats/$chatId/clear-for-everyone"))
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun deleteChatForEveryone(accessToken: String, chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/chats/$chatId/delete-for-everyone"))
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun getChatMessages(accessToken: String, chatId: String, take: Int = 150): Result<List<MessageDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/chats/$chatId/messages?take=$take"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    json.decodeFromString<MessagesListResponse>(bodyText).messages
                }
            }
        }

    suspend fun getPinnedMessages(accessToken: String, chatId: String): Result<List<MessageDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/chats/$chatId/pins"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use emptyList()
                    json.decodeFromString<PinsListResponse>(bodyText).pins.mapNotNull { it.message }
                }
            }
        }

    suspend fun pinMessage(accessToken: String, chatId: String, messageId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/chats/$chatId/pins/$messageId"))
                    .header("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun unpinMessage(accessToken: String, chatId: String, messageId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/chats/$chatId/pins/$messageId"))
                    .header("Authorization", "Bearer $accessToken")
                    .delete()
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
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
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: Ошибка отправки")
                json.decodeFromString<SendMessageResponse>(bodyText).message
                    ?: throw IllegalStateException("Пустой ответ сервера")
            }
        }
    }

    suspend fun editMessage(accessToken: String, messageId: String, content: String): Result<MessageDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("content", content) }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/messages/$messageId"))
                    .header("Authorization", "Bearer $accessToken")
                    .patch(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    json.decodeFromString<SendMessageResponse>(bodyText).message
                        ?: throw IllegalStateException("Empty edited message")
                }
            }
        }

    suspend fun deleteMessageForEveryone(accessToken: String, messageId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/messages/$messageId"))
                    .header("Authorization", "Bearer $accessToken")
                    .delete()
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun forwardMessage(accessToken: String, messageId: String, targetChatId: String): Result<MessageDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("chatId", targetChatId) }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/messages/$messageId/forward"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    json.decodeFromString<SendMessageResponse>(bodyText).message
                        ?: throw IllegalStateException("Empty forwarded message")
                }
            }
        }

    suspend fun toggleReaction(accessToken: String, messageId: String, emoji: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("emoji", emoji) }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/messages/$messageId/reactions"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun searchUsers(accessToken: String, query: String): Result<List<UserDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim().removePrefix("@"), "UTF-8")
                val request = Request.Builder()
                    .url(apiUrl("/users/search?username=$encoded&limit=25"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    json.decodeFromString<UsersSearchResponse>(bodyText).users
                }
            }
        }

    suspend fun createDirectChat(accessToken: String, userId: String): Result<ChatDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("userId", userId) }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/chats/direct"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    json.decodeFromString<CreateChatResponse>(bodyText).chat
                        ?: throw IllegalStateException("Чат не создан")
                }
            }
        }

    suspend fun createGroupChat(
        accessToken: String,
        title: String,
        memberIds: List<String>,
        isPrivateGroup: Boolean = false
    ): Result<ChatDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("title", title.trim())
                putJsonArray("memberIds") {
                    memberIds.forEach { add(it) }
                }
                put("isPrivateGroup", isPrivateGroup)
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/chats/group"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<CreateChatResponse>(bodyText).chat
                    ?: throw IllegalStateException("Группа не создана")
            }
        }
    }

    suspend fun updateProfile(
        accessToken: String,
        displayName: String,
        username: String,
        bio: String,
        birthday: String? = null
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("displayName", displayName.trim())
                if (username.trim().length >= 3) put("username", username.trim())
                put("bio", bio.trim())
                if (!birthday.isNullOrBlank()) put("birthday", birthday.trim())
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/users/me"))
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<MeResponse>(bodyText).user
                    ?: throw IllegalStateException("Не удалось обновить профиль")
            }
        }
    }

    suspend fun updatePrivacy(
        accessToken: String,
        showOnline: Boolean,
        showLastSeen: Boolean,
        allowCalls: Boolean,
        callsContactsOnly: Boolean,
        readReceipts: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("showOnline", showOnline)
                put("showLastSeen", showLastSeen)
                put("allowCalls", allowCalls)
                put("callsContactsOnly", callsContactsOnly)
                put("readReceipts", readReceipts)
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/users/me/privacy"))
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun changePassword(accessToken: String, currentPassword: String, newPassword: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("currentPassword", currentPassword)
                    put("newPassword", newPassword)
                }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/auth/change-password"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: Неверный текущий пароль")
                    true
                }
            }
        }

    suspend fun getSessions(accessToken: String): Result<SessionsListResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/sessions"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<SessionsListResponse>(bodyText)
            }
        }
    }

    suspend fun terminateSession(accessToken: String, sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/sessions/$sessionId"))
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun terminateOtherSessions(accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/sessions/others"))
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun blockUser(accessToken: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/users/block/$userId"))
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun unblockUser(accessToken: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/users/block/$userId"))
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun getUserStories(accessToken: String, userId: String): Result<List<StoryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/stories?userId=$userId"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use emptyList()
                json.decodeFromString<StoriesListResponse>(bodyText).stories
            }
        }
    }

    // External Open-Meteo Weather API (1:1 with frontend/src/services/weatherService.js)
    suspend fun fetchWeatherForecast(
        cityName: String = "Bishkek",
        country: String = "Kyrgyzstan",
        lat: Double = 42.8746,
        lon: Double = 74.5698
    ): Result<WeatherForecastData> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,wind_speed_10m,relative_humidity_2m,weather_code" +
                "&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=7&timezone=auto"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("Weather HTTP ${response.code}")
                val root = json.parseToJsonElement(bodyText).jsonObject
                val current = root["current"]?.jsonObject
                val temp = current?.get("temperature_2m")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val feels = current?.get("apparent_temperature")?.jsonPrimitive?.doubleOrNull ?: temp
                val wind = current?.get("wind_speed_10m")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val hum = current?.get("relative_humidity_2m")?.jsonPrimitive?.intOrNull ?: 0
                val code = current?.get("weather_code")?.jsonPrimitive?.intOrNull ?: 0
                val timeStr = current?.get("time")?.jsonPrimitive?.contentOrNull.orEmpty()

                val hourlyObj = root["hourly"]?.jsonObject
                val hTimes = hourlyObj?.get("time")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                val hTemps = hourlyObj?.get("temperature_2m")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
                val hourlyList = hTimes.zip(hTemps).take(24).map { (t, v) ->
                    t.substringAfter("T", t) to v
                }

                val dailyObj = root["daily"]?.jsonObject
                val dTimes = dailyObj?.get("time")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                val dMax = dailyObj?.get("temperature_2m_max")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
                val dMin = dailyObj?.get("temperature_2m_min")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
                val dCode = dailyObj?.get("weather_code")?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty()

                val dailyList = dTimes.indices.map { idx ->
                    DailyWeatherItem(
                        date = dTimes.getOrElse(idx) { "" },
                        tempMax = dMax.getOrElse(idx) { 0.0 },
                        tempMin = dMin.getOrElse(idx) { 0.0 },
                        weatherCode = dCode.getOrElse(idx) { 0 }
                    )
                }

                WeatherForecastData(
                    cityName = cityName,
                    country = country,
                    latitude = lat,
                    longitude = lon,
                    tempCurrent = temp,
                    feelsLike = feels,
                    windSpeed = wind,
                    humidity = hum,
                    weatherCode = code,
                    hourlyTemps = hourlyList,
                    dailyForecast = dailyList,
                    updatedAt = timeStr.substringAfter("T", timeStr)
                )
            }
        }
    }

    suspend fun searchWeatherCity(query: String): Result<Triple<String, Double, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=ru&format=json"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyText).jsonObject
                val first = root["results"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: throw IllegalStateException("Город не найден")
                val name = first["name"]?.jsonPrimitive?.contentOrNull ?: query
                val lat = first["latitude"]?.jsonPrimitive?.doubleOrNull ?: 42.8746
                val lon = first["longitude"]?.jsonPrimitive?.doubleOrNull ?: 74.5698
                Triple(name, lat, lon)
            }
        }
    }

    // External Crypto API (1:1 with frontend/src/services/currencyService.js)
    suspend fun fetchCryptoRates(): Result<List<CryptoCoinRate>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=30&page=1&sparkline=false"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val arr = json.parseToJsonElement(bodyText).jsonArray
                arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val sym = obj["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: id.uppercase()
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: sym
                    val price = obj["current_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val chg = obj["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    CryptoCoinRate(id, sym, name, price, chg)
                }
            }
        }
    }

    suspend fun searchMusicTracks(accessToken: String, query: String): Result<List<MusicTrackDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val request = Request.Builder()
                    .url(apiUrl("/youtube/search?q=$encoded&maxResults=20"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use emptyList()
                    json.decodeFromString<MusicSearchResponse>(bodyText).items
                }
            }
        }

    suspend fun getMusicFavorites(accessToken: String): Result<List<MusicTrackDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/music/favorites"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use emptyList()
                    json.decodeFromString<MusicFavoritesResponse>(bodyText).favorites
                }
            }
        }

    suspend fun getWalletSessions(accessToken: String): Result<List<WalletSessionItemDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/wallet/sessions"))
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use emptyList()
                    json.decodeFromString<WalletSessionsResponse>(bodyText).sessions
                }
            }
        }

    suspend fun sendMediaMessage(
        accessToken: String,
        chatId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        type: String = "PHOTO",
        content: String? = null,
        replyToId: String? = null
    ): Result<MessageDto> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = runCatching { mimeType.toMediaType() }.getOrElse { "application/octet-stream".toMediaType() }
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName.ifBlank { "upload.jpg" }, bytes.toRequestBody(mediaType))
                .addFormDataPart("type", type)
                .addFormDataPart("clientMessageId", UUID.randomUUID().toString())
            if (!content.isNullOrBlank()) {
                bodyBuilder.addFormDataPart("content", content)
            }
            if (!replyToId.isNullOrBlank()) {
                bodyBuilder.addFormDataPart("replyToId", replyToId)
            }

            val request = Request.Builder()
                .url(apiUrl("/messages/chat/$chatId"))
                .header("Authorization", "Bearer $accessToken")
                .post(bodyBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: Ошибка загрузки медиа")
                json.decodeFromString<SendMessageResponse>(bodyText).message
                    ?: throw IllegalStateException("Пустой ответ сервера")
            }
        }
    }

    suspend fun uploadAvatar(
        accessToken: String,
        bytes: ByteArray,
        fileName: String = "avatar.jpg",
        mimeType: String = "image/jpeg"
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = runCatching { mimeType.toMediaType() }.getOrElse { "image/jpeg".toMediaType() }
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("avatar", fileName.ifBlank { "avatar.jpg" }, bytes.toRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url(apiUrl("/users/me/avatar"))
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: Ошибка загрузки аватара")
                val parsed = json.decodeFromString<AvatarUploadResponse>(bodyText)
                parsed.user ?: getMe(accessToken).getOrThrow()
            }
        }
    }

    suspend fun checkAuthQrStatus(token: String): Result<QrStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(token.trim(), "UTF-8")
            val request = Request.Builder()
                .url(apiUrl("/auth/qr/status/$encoded"))
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<QrStatusResponse>(bodyText)
            }
        }
    }

    suspend fun finalizeAuthQr(token: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("token", token.trim())
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("clientPlatform", "android-kotlin")
                put("clientOsName", "Android")
                put("clientOsVersion", Build.VERSION.RELEASE ?: "14")
                put("clientDeviceType", "mobile")
                put("clientAppVersion", "1.0.0-kotlin")
            }.toString()

            val request = Request.Builder()
                .url(apiUrl("/auth/qr/finalize"))
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<AuthResponse>(bodyText)
            }
        }
    }

    suspend fun confirmDeviceQr(accessToken: String, token: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("token", token.trim())
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            }.toString()
            val request = Request.Builder()
                .url(apiUrl("/qr/confirm"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun get2faStatus(accessToken: String): Result<TwoFactorStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/2fa/status"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<TwoFactorStatusResponse>(bodyText)
            }
        }
    }

    suspend fun start2faSetup(accessToken: String): Result<TwoFactorSetupResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/2fa/setup/start"))
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<TwoFactorSetupResponse>(bodyText)
            }
        }
    }

    suspend fun confirm2faSetup(accessToken: String, code: String): Result<TwoFactorStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("code", code.trim()) }.toString()
            val request = Request.Builder()
                .url(apiUrl("/2fa/setup/confirm"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: Неверный 2FA код")
                json.decodeFromString<TwoFactorStatusResponse>(bodyText)
            }
        }
    }

    suspend fun disable2fa(accessToken: String, code: String): Result<TwoFactorStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("code", code.trim()) }.toString()
            val request = Request.Builder()
                .url(apiUrl("/2fa/disable"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: Неверный 2FA код")
                json.decodeFromString<TwoFactorStatusResponse>(bodyText)
            }
        }
    }

    suspend fun set2faDeviceApproval(accessToken: String, enabled: Boolean): Result<TwoFactorStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("enabled", enabled) }.toString()
            val request = Request.Builder()
                .url(apiUrl("/2fa/device-approval"))
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<TwoFactorStatusResponse>(bodyText)
            }
        }
    }

    suspend fun listBlockedUsers(accessToken: String): Result<List<BlockedUserEntryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("/users/blocks"))
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use emptyList()
                json.decodeFromString<BlocksListResponse>(bodyText).blocks
            }
        }
    }

    suspend fun reportTarget(
        accessToken: String,
        targetId: String,
        targetType: String,
        reason: String,
        comment: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val contentText = buildString {
                append("[REPORT] ")
                append("Type: $targetType | Target: $targetId | Reason: $reason")
                if (comment.isNotBlank()) append(" | Note: $comment")
            }
            val payload = buildJsonObject {
                put("content", contentText)
                put("targetId", targetId)
                put("targetType", targetType)
                put("reason", reason)
                if (comment.isNotBlank()) put("comment", comment)
            }.toString()
            val request = Request.Builder()
                .url(apiUrl("/support/message"))
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }
    }

    suspend fun addGroupMembers(accessToken: String, groupId: String, memberIds: List<String>): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    putJsonArray("memberIds") { memberIds.forEach { add(it) } }
                }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/groups/$groupId/members"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun removeGroupMember(accessToken: String, groupId: String, userId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/groups/$groupId/members/$userId"))
                    .header("Authorization", "Bearer $accessToken")
                    .delete()
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun setGroupMemberRole(accessToken: String, groupId: String, userId: String, role: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("role", role) }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/groups/$groupId/members/$userId/role"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun muteGroupMember(accessToken: String, groupId: String, userId: String, duration: String = "1h"): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("duration", duration) }.toString()
                val request = Request.Builder()
                    .url(apiUrl("/groups/$groupId/members/$userId/mute"))
                    .header("Authorization", "Bearer $accessToken")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun leaveGroup(accessToken: String, groupId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/groups/$groupId/leave"))
                    .header("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }
        }

    suspend fun updateGroupSettings(
        accessToken: String,
        groupId: String,
        title: String? = null,
        isPrivateGroup: Boolean? = null,
        joinApprovalRequired: Boolean? = null
    ): Result<ChatDto> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                title?.let { put("title", it.trim()) }
                isPrivateGroup?.let { put("isPrivateGroup", it) }
                joinApprovalRequired?.let { put("joinApprovalRequired", it) }
            }.toString()
            val request = Request.Builder()
                .url(apiUrl("/groups/$groupId"))
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                json.decodeFromString<GroupResponse>(bodyText).group
                    ?: throw IllegalStateException("Failed to update group")
            }
        }
    }

    suspend fun refreshGroupInviteLink(accessToken: String, groupId: String): Result<ChatDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl("/groups/$groupId/invite-link/refresh"))
                    .header("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    json.decodeFromString<GroupResponse>(bodyText).group
                        ?: throw IllegalStateException("Failed to refresh invite link")
                }
            }
        }
}
