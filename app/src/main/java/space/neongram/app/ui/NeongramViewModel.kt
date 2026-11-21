package space.neongram.app.ui

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.neongram.app.core.model.*
import space.neongram.app.core.network.NeongramApi
import space.neongram.app.core.network.SocketManager
import space.neongram.app.core.session.SessionStore
import space.neongram.app.core.session.StoredSession

enum class ActiveOverlayScreen {
    NONE,
    AUTH_QR,
    CHAT_ROOM,
    GROUP_INFO,
    PEER_PROFILE,
    SETTINGS_ACCOUNT,
    SETTINGS_ACCOUNT_INFO,
    SETTINGS_PRIVACY,
    SETTINGS_SECURITY,
    SETTINGS_DEVICES,
    SETTINGS_APPEARANCE,
    SETTINGS_STORAGE,
    SETTINGS_ABOUT
}

data class NeongramUiState(
    val isLoading: Boolean = false,
    val toastMessage: String? = null,
    val errorMessage: String? = null,
    val session: StoredSession = StoredSession(),
    val currentUser: UserDto? = null,
    val chats: List<ChatDto> = emptyList(),
    val activeChat: ChatDto? = null,
    val messages: List<MessageDto> = emptyList(),
    val pinnedMessages: List<MessageDto> = emptyList(),
    val replyToMessage: MessageDto? = null,
    val editingMessage: MessageDto? = null,
    val forwardingMessage: MessageDto? = null,
    val searchResults: List<UserDto> = emptyList(),
    val peerProfileUser: UserDto? = null,
    val myStories: List<StoryDto> = emptyList(),
    val activeSessions: List<SessionItemDto> = emptyList(),
    val currentSessionId: String? = null,
    val qrAuthToken: String = "",
    val qrAuthStatus: String = "pending",
    val qrApprovedBy: UserDto? = null,
    val twoFactorChallengeId: String? = null,
    val twoFactorStatus: TwoFactorStatusResponse = TwoFactorStatusResponse(),
    val twoFactorSetupData: TwoFactorSetupResponse? = null,
    val blockedUsers: List<BlockedUserEntryDto> = emptyList(),
    val devicePhotos: List<DevicePhotoItem> = emptyList(),
    val isAppLocked: Boolean = false,
    val chatFolderFilter: String = "ALL",
    val aiModel: String = "fast",
    val aiMode: String = "default",
    val weatherData: WeatherForecastData = WeatherForecastData(),
    val cryptoRates: List<CryptoCoinRate> = emptyList(),
    val musicTracks: List<MusicTrackDto> = emptyList(),
    val musicFavorites: List<MusicTrackDto> = emptyList(),
    val walletSessions: List<WalletSessionItemDto> = emptyList(),
    val overlayScreen: ActiveOverlayScreen = ActiveOverlayScreen.NONE,
    val selectedTabIndex: Int = 0,
    val isSocketConnected: Boolean = false
)

class NeongramViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val api = NeongramApi()
    private val socketManager = SocketManager()
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private val messagesCache = mutableMapOf<String, List<MessageDto>>()
    private var qrPollingJob: Job? = null
    private var initialPinLockChecked = false

    private val _uiState = MutableStateFlow(NeongramUiState())
    val uiState: StateFlow<NeongramUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { stored ->
                api.baseUrl = "https://cloud.neongram.space"
                val shouldLockOnStart = !initialPinLockChecked && stored.isAuthenticated && stored.pinCode.isNotBlank()
                if (stored.isAuthenticated) {
                    initialPinLockChecked = true
                }
                _uiState.update { state ->
                    state.copy(
                        session = stored,
                        isAppLocked = if (shouldLockOnStart) true else if (stored.pinCode.isBlank()) false else state.isAppLocked
                    )
                }
                if (stored.isAuthenticated) {
                    qrPollingJob?.cancel()
                    socketManager.connect(api.baseUrl, stored.accessToken)
                    refreshAll()
                } else {
                    socketManager.disconnect()
                    _uiState.update {
                        it.copy(
                            currentUser = null,
                            chats = emptyList(),
                            activeChat = null,
                            messages = emptyList(),
                            isAppLocked = false,
                            overlayScreen = if (it.overlayScreen == ActiveOverlayScreen.AUTH_QR) ActiveOverlayScreen.AUTH_QR else ActiveOverlayScreen.NONE
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            socketManager.connected.collect { connected ->
                _uiState.update { it.copy(isSocketConnected = connected) }
            }
        }

        viewModelScope.launch {
            socketManager.incomingMessages.collect { incoming ->
                val cachedList = messagesCache[incoming.chatId].orEmpty()
                if (cachedList.none { it.id == incoming.id }) {
                    messagesCache[incoming.chatId] = cachedList + incoming
                }
                val currentChatId = _uiState.value.activeChat?.id
                if (currentChatId == incoming.chatId) {
                    _uiState.update { state ->
                        val exists = state.messages.any { it.id == incoming.id }
                        if (exists) state else state.copy(messages = state.messages + incoming)
                    }
                }
                refreshChats()
            }
        }

        viewModelScope.launch {
            socketManager.chatUpdates.collect {
                refreshChats()
            }
        }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            sessionStore.completeOnboarding()
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            sessionStore.setLanguage(lang)
        }
    }

    fun setChatFolderFilter(folder: String) {
        _uiState.update { it.copy(chatFolderFilter = folder) }
    }

    fun setAiModel(model: String) {
        _uiState.update { it.copy(aiModel = model) }
    }

    fun setAiMode(mode: String) {
        _uiState.update { it.copy(aiMode = mode) }
    }

    fun sendAiPrompt(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            openAiAssistantChat()
            return
        }
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return

        val existingAiChat = _uiState.value.chats.firstOrNull { it.isAiChat }
        if (existingAiChat != null) {
            openChat(existingAiChat)
            sendMessage(cleanPrompt)
        } else {
            viewModelScope.launch {
                api.getChats(token).onSuccess { list ->
                    _uiState.update { it.copy(chats = list) }
                    val aiChat = list.firstOrNull { it.isAiChat }
                    if (aiChat != null) {
                        openChat(aiChat)
                        sendMessage(cleanPrompt)
                    } else {
                        showToast("Чат Neongram AI не найден")
                    }
                }.onFailure {
                    showToast("Чат Neongram AI загружается...")
                }
            }
        }
    }

    fun updateAppearance(
        themeId: String? = null,
        useSystemTheme: Boolean? = null,
        fontSize: String? = null,
        bubbleColorHex: String? = null,
        bubbleScope: String? = null,
        wallpaper: String? = null
    ) {
        viewModelScope.launch {
            sessionStore.updateAppearance(
                themeId = themeId,
                useSystemTheme = useSystemTheme,
                fontSize = fontSize,
                bubbleColorHex = bubbleColorHex,
                bubbleScope = bubbleScope,
                wallpaper = wallpaper
            )
            showToast("Оформление обновлено")
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index, overlayScreen = ActiveOverlayScreen.NONE) }
    }

    fun navigateToOverlay(screen: ActiveOverlayScreen) {
        _uiState.update { it.copy(overlayScreen = screen) }
        when (screen) {
            ActiveOverlayScreen.AUTH_QR -> generateQrLoginToken()
            ActiveOverlayScreen.SETTINGS_DEVICES -> loadSessions()
            ActiveOverlayScreen.SETTINGS_SECURITY -> load2faStatus()
            ActiveOverlayScreen.SETTINGS_PRIVACY -> loadBlockedUsers()
            else -> {}
        }
    }

    fun navigateBack() {
        val current = _uiState.value.overlayScreen
        if (current == ActiveOverlayScreen.AUTH_QR) {
            qrPollingJob?.cancel()
        }
        val next = when (current) {
            ActiveOverlayScreen.GROUP_INFO -> ActiveOverlayScreen.CHAT_ROOM
            ActiveOverlayScreen.PEER_PROFILE -> if (_uiState.value.activeChat != null) ActiveOverlayScreen.CHAT_ROOM else ActiveOverlayScreen.NONE
            ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO -> ActiveOverlayScreen.SETTINGS_ACCOUNT
            ActiveOverlayScreen.CHAT_ROOM -> {
                _uiState.update {
                    it.copy(
                        activeChat = null,
                        messages = emptyList(),
                        replyToMessage = null,
                        editingMessage = null
                    )
                }
                refreshChats()
                ActiveOverlayScreen.NONE
            }
            else -> ActiveOverlayScreen.NONE
        }
        _uiState.update { it.copy(overlayScreen = next) }
    }

    fun login(username: String, password: String, serverUrl: String = "https://cloud.neongram.space") {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Заполните все поля") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            api.baseUrl = "https://cloud.neongram.space"
            val result = api.login(username, password)
            result.onSuccess { resp ->
                val token = resp.accessToken.orEmpty()
                val user = resp.user
                if (resp.requiresTwoFactor && !resp.challengeId.isNullOrBlank()) {
                    _uiState.update { it.copy(twoFactorChallengeId = resp.challengeId) }
                } else if (token.isNotBlank() && user != null) {
                    _uiState.update { it.copy(twoFactorChallengeId = null) }
                    sessionStore.saveSession(
                        accessToken = token,
                        refreshToken = resp.refreshToken.orEmpty(),
                        userId = user.id,
                        username = user.username,
                        displayName = user.effectiveName,
                        serverUrl = api.baseUrl
                    )
                    showToast("Успешный вход")
                } else {
                    _uiState.update { it.copy(errorMessage = resp.error ?: "Не удалось выполнить вход") }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Неверный логин или пароль") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun confirmTwoFactor(code: String) {
        val challengeId = _uiState.value.twoFactorChallengeId ?: return
        if (code.length < 6) {
            _uiState.update { it.copy(errorMessage = "Введите 6-значный код") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            api.completeTwoFactorLogin(challengeId, code).onSuccess { resp ->
                val token = resp.accessToken.orEmpty()
                val user = resp.user
                if (token.isNotBlank() && user != null) {
                    _uiState.update { it.copy(twoFactorChallengeId = null) }
                    sessionStore.saveSession(
                        accessToken = token,
                        refreshToken = resp.refreshToken.orEmpty(),
                        userId = user.id,
                        username = user.username,
                        displayName = user.effectiveName,
                        serverUrl = api.baseUrl
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Неверный 2FA код") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun cancelTwoFactor() {
        _uiState.update { it.copy(twoFactorChallengeId = null, errorMessage = null) }
    }

    fun register(username: String, password: String, confirmPassword: String, serverUrl: String = "https://cloud.neongram.space") {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Заполните все поля") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Пароли не совпадают") }
            return
        }
        if (username.length < 3 || password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Логин от 3 символов, пароль от 6 символов") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            api.baseUrl = "https://cloud.neongram.space"
            val result = api.register(username, password)
            result.onSuccess { resp ->
                val token = resp.accessToken.orEmpty()
                val user = resp.user
                if (token.isNotBlank() && user != null) {
                    sessionStore.saveSession(
                        accessToken = token,
                        refreshToken = resp.refreshToken.orEmpty(),
                        userId = user.id,
                        username = user.username,
                        displayName = user.effectiveName,
                        serverUrl = api.baseUrl
                    )
                    showToast("Успешный вход")
                } else {
                    _uiState.update { it.copy(errorMessage = resp.error ?: "Ошибка регистрации") }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Ошибка подключения к серверу") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun generateQrLoginToken() {
        qrPollingJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(qrAuthStatus = "pending", qrApprovedBy = null) }
            api.startAuthQr().onSuccess { resp ->
                val token = resp.token
                _uiState.update { it.copy(qrAuthToken = token, qrAuthStatus = "pending", qrApprovedBy = null) }
                if (token.isNotBlank() && !_uiState.value.session.isAuthenticated) {
                    startQrPolling(token)
                }
            }
        }
    }

    private fun startQrPolling(token: String) {
        qrPollingJob?.cancel()
        qrPollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.overlayScreen == ActiveOverlayScreen.AUTH_QR && !_uiState.value.session.isAuthenticated) {
                delay(2000L)
                api.checkAuthQrStatus(token).onSuccess { statusResp ->
                    val st = statusResp.status.lowercase()
                    _uiState.update {
                        it.copy(
                            qrAuthStatus = st,
                            qrApprovedBy = statusResp.approvedBy ?: statusResp.user ?: it.qrApprovedBy
                        )
                    }
                    if (st == "approved") {
                        api.finalizeAuthQr(token).onSuccess { authResp ->
                            val accessToken = authResp.accessToken.orEmpty()
                            val user = authResp.user
                            if (accessToken.isNotBlank() && user != null) {
                                _uiState.update { it.copy(overlayScreen = ActiveOverlayScreen.NONE) }
                                sessionStore.saveSession(
                                    accessToken = accessToken,
                                    refreshToken = authResp.refreshToken.orEmpty(),
                                    userId = user.id,
                                    username = user.username,
                                    displayName = user.effectiveName,
                                    serverUrl = api.baseUrl
                                )
                                showToast("Вход по QR выполнен")
                            }
                        }
                        break
                    } else if (st == "expired") {
                        break
                    }
                }
            }
        }
    }

    fun approveQrToken(token: String) {
        val accessToken = _uiState.value.session.accessToken
        if (accessToken.isBlank() || token.isBlank()) return
        val cleanToken = token.removePrefix("neongram://login/").trim()
        viewModelScope.launch {
            api.approveAuthQr(accessToken, cleanToken).onSuccess { ok ->
                if (ok) showToast("Вход разрешен") else showToast("Неверный QR токен")
            }.onFailure {
                showToast("Не удалось подтвердить QR вход")
            }
        }
    }

    fun handleScannedQrPayload(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val accessToken = _uiState.value.session.accessToken

        // 1. JSON payload check ("neongram_login" or "neongram_web_connect")
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val parsedObj = runCatching { jsonParser.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            if (parsedObj != null) {
                val type = parsedObj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val token = parsedObj["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val username = parsedObj["username"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (type == "neongram_login" && token.isNotBlank()) {
                    approveQrToken(token)
                    return
                }
                if (type == "neongram_web_connect" && token.isNotBlank()) {
                    if (accessToken.isNotBlank()) {
                        viewModelScope.launch {
                            api.confirmDeviceQr(accessToken, token).onSuccess { ok ->
                                showToast(if (ok) "Устройство подключено" else "Не удалось подключить устройство")
                            }
                        }
                    }
                    return
                }
                if (username.isNotBlank()) {
                    addContactByLogin(username)
                    return
                }
            }
        }

        // 2. neongram://login/<token>
        if (trimmed.startsWith("neongram://login/", ignoreCase = true)) {
            val token = trimmed.substringAfter("neongram://login/", "").trim()
            if (token.isNotBlank()) {
                approveQrToken(token)
                return
            }
        }

        // 3. neongram://user/<username> or neongram://contact/<username> or /u/<username> or @username
        when {
            trimmed.startsWith("neongram://user/", ignoreCase = true) -> {
                val u = trimmed.substringAfter("neongram://user/").substringBefore("?").trim('/')
                if (u.isNotBlank()) addContactByLogin(u)
                return
            }
            trimmed.startsWith("neongram://contact/", ignoreCase = true) -> {
                val u = trimmed.substringAfter("neongram://contact/").substringBefore("?").trim('/')
                if (u.isNotBlank()) addContactByLogin(u)
                return
            }
            trimmed.contains("/u/", ignoreCase = true) -> {
                val u = trimmed.substringAfter("/u/").substringBefore("?").trim('/')
                if (u.isNotBlank()) addContactByLogin(u)
                return
            }
            trimmed.startsWith("@") && trimmed.length >= 4 -> {
                addContactByLogin(trimmed.removePrefix("@"))
                return
            }
        }

        // 4. Otherwise if 16+ hex/alphanumeric token -> approveQrToken
        if (trimmed.length >= 16 && trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            approveQrToken(trimmed)
        } else if (trimmed.length in 3..32 && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '.' }) {
            addContactByLogin(trimmed)
        } else {
            showToast("Неизвестный формат QR-кода")
        }
    }

    fun refreshAll() {
        val token = _uiState.value.session.accessToken
        val userId = _uiState.value.session.userId
        if (token.isBlank()) return
        viewModelScope.launch {
            api.getMe(token).onSuccess { me ->
                _uiState.update { it.copy(currentUser = me) }
            }
            if (userId.isNotBlank()) {
                api.getUserStories(token, userId).onSuccess { stories ->
                    _uiState.update { it.copy(myStories = stories) }
                }
            }
            refreshChats()
            load2faStatus()
            loadBlockedUsers()
        }
    }

    fun refreshChats() {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.getChats(token).onSuccess { list ->
                list.forEach { chat ->
                    if (chat.messages.isNotEmpty() && messagesCache[chat.id].isNullOrEmpty()) {
                        messagesCache[chat.id] = chat.messages
                    }
                }
                val activeId = _uiState.value.activeChat?.id
                val updatedActive = if (activeId != null) list.firstOrNull { it.id == activeId } ?: _uiState.value.activeChat else null
                _uiState.update { it.copy(chats = list, activeChat = updatedActive) }
            }
        }
    }

    fun openChat(chat: ChatDto) {
        val token = _uiState.value.session.accessToken
        val immediateMessages = messagesCache[chat.id]?.takeIf { it.isNotEmpty() } ?: chat.messages
        if (immediateMessages.isNotEmpty()) {
            messagesCache[chat.id] = immediateMessages
        }
        _uiState.update {
            it.copy(
                activeChat = chat,
                messages = immediateMessages,
                pinnedMessages = emptyList(),
                replyToMessage = null,
                editingMessage = null,
                isLoading = false,
                overlayScreen = ActiveOverlayScreen.CHAT_ROOM
            )
        }
        socketManager.joinChatRoom(chat.id)
        viewModelScope.launch {
            api.getChatMessages(token, chat.id).onSuccess { list ->
                messagesCache[chat.id] = list
                _uiState.update { state ->
                    if (state.activeChat?.id == chat.id) {
                        state.copy(messages = list, isLoading = false)
                    } else {
                        state
                    }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message, isLoading = false) }
            }
            api.getPinnedMessages(token, chat.id).onSuccess { pins ->
                _uiState.update { state ->
                    if (state.activeChat?.id == chat.id) state.copy(pinnedMessages = pins) else state
                }
            }
        }
    }

    fun openAiAssistantChat() {
        val aiChat = _uiState.value.chats.firstOrNull { it.isAiChat }
        if (aiChat != null) {
            openChat(aiChat)
        } else {
            showToast("Чат Neongram AI загружается...")
            refreshChats()
        }
    }

    fun openPeerProfile(user: UserDto) {
        val token = _uiState.value.session.accessToken
        _uiState.update { it.copy(peerProfileUser = user, overlayScreen = ActiveOverlayScreen.PEER_PROFILE) }
        if (token.isNotBlank() && user.id.isNotBlank()) {
            viewModelScope.launch {
                api.getUserById(token, user.id).onSuccess { full ->
                    _uiState.update { it.copy(peerProfileUser = full) }
                }
            }
        }
    }

    fun setReplyTarget(msg: MessageDto?) {
        _uiState.update { it.copy(replyToMessage = msg, editingMessage = null) }
    }

    fun setEditTarget(msg: MessageDto?) {
        _uiState.update { it.copy(editingMessage = msg, replyToMessage = null) }
    }

    fun setForwardTarget(msg: MessageDto?) {
        _uiState.update { it.copy(forwardingMessage = msg) }
    }

    fun forwardMessageToChat(targetChat: ChatDto) {
        val msg = _uiState.value.forwardingMessage ?: return
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        _uiState.update { it.copy(forwardingMessage = null) }
        viewModelScope.launch {
            api.forwardMessage(token, msg.id, targetChat.id).onSuccess { forwarded ->
                val cached = messagesCache[targetChat.id].orEmpty()
                messagesCache[targetChat.id] = cached + forwarded
                showToast("Сообщение переслано")
                openChat(targetChat)
                refreshChats()
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось переслать сообщение")
            }
        }
    }

    fun sendMessage(text: String) {
        val content = text.trim()
        val chat = _uiState.value.activeChat ?: return
        val token = _uiState.value.session.accessToken
        if (content.isBlank() || token.isBlank()) return

        val editing = _uiState.value.editingMessage
        if (editing != null) {
            viewModelScope.launch {
                api.editMessage(token, editing.id, content).onSuccess { updated ->
                    val updatedList = _uiState.value.messages.map { if (it.id == updated.id) updated else it }
                    messagesCache[chat.id] = updatedList
                    _uiState.update { state ->
                        state.copy(
                            editingMessage = null,
                            messages = updatedList
                        )
                    }
                }
            }
            return
        }

        val replyId = _uiState.value.replyToMessage?.id
        _uiState.update { it.copy(replyToMessage = null) }

        viewModelScope.launch {
            api.sendMessage(token, chat.id, content, replyId).onSuccess { sent ->
                _uiState.update { state ->
                    val exists = state.messages.any { it.id == sent.id }
                    val newList = if (exists) state.messages else state.messages + sent
                    messagesCache[chat.id] = newList
                    if (exists) state else state.copy(messages = newList)
                }
                refreshChats()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun loadDevicePhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.MIME_TYPE
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val items = mutableListOf<DevicePhotoItem>()
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    while (cursor.moveToNext() && items.size < 60) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "photo_$id.jpg"
                        val dateAdded = cursor.getLong(dateCol)
                        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        items.add(
                            DevicePhotoItem(
                                uriString = contentUri.toString(),
                                displayName = name,
                                dateAdded = dateAdded,
                                mimeType = mime
                            )
                        )
                    }
                }
                _uiState.update { it.copy(devicePhotos = items) }
            }
        }
    }

    fun sendMediaFromUri(uri: Uri, type: String = "PHOTO", caption: String = "") {
        val chat = _uiState.value.activeChat ?: return
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        val replyId = _uiState.value.replyToMessage?.id
        _uiState.update { it.copy(replyToMessage = null) }

        viewModelScope.launch {
            val mediaPayload = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val mimeType = resolver.getType(uri) ?: when (type.uppercase()) {
                        "VIDEO" -> "video/mp4"
                        "VOICE", "AUDIO" -> "audio/ogg"
                        "PHOTO" -> "image/jpeg"
                        else -> "application/octet-stream"
                    }
                    var fileName = "upload_${System.currentTimeMillis()}"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Не удалось прочитать файл")
                    Triple(bytes, fileName, mimeType)
                }
            }

            mediaPayload.onSuccess { (bytes, fileName, mimeType) ->
                val resolvedType = when {
                    type.uppercase() != "AUTO" -> type.uppercase()
                    mimeType.startsWith("image/") -> "PHOTO"
                    mimeType.startsWith("video/") -> "VIDEO"
                    mimeType.startsWith("audio/") -> "AUDIO"
                    else -> "DOCUMENT"
                }
                api.sendMediaMessage(
                    accessToken = token,
                    chatId = chat.id,
                    bytes = bytes,
                    fileName = fileName,
                    mimeType = mimeType,
                    type = resolvedType,
                    content = caption.trim().takeIf { it.isNotBlank() },
                    replyToId = replyId
                ).onSuccess { sent ->
                    _uiState.update { state ->
                        val exists = state.messages.any { it.id == sent.id }
                        val newList = if (exists) state.messages else state.messages + sent
                        messagesCache[chat.id] = newList
                        if (exists) state else state.copy(messages = newList)
                    }
                    refreshChats()
                }.onFailure { err ->
                    showToast(err.message ?: "Ошибка отправки файла")
                }
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось открыть файл")
            }
        }
    }

    fun uploadMyAvatarFromUri(uri: Uri) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val mimeType = resolver.getType(uri) ?: "image/jpeg"
                    var fileName = "avatar_${System.currentTimeMillis()}.jpg"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Не удалось прочитать изображение")
                    Triple(bytes, fileName, mimeType)
                }
            }
            payload.onSuccess { (bytes, fileName, mimeType) ->
                api.uploadAvatar(token, bytes, fileName, mimeType).onSuccess { updatedUser ->
                    _uiState.update { it.copy(currentUser = updatedUser) }
                    showToast("Аватар обновлен")
                }.onFailure { err ->
                    showToast(err.message ?: "Ошибка загрузки аватара")
                }
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось выбрать изображение")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val token = _uiState.value.session.accessToken
        val chat = _uiState.value.activeChat ?: return
        viewModelScope.launch {
            api.deleteMessageForEveryone(token, messageId).onSuccess {
                val filtered = _uiState.value.messages.filterNot { it.id == messageId }
                messagesCache[chat.id] = filtered
                _uiState.update { state ->
                    state.copy(messages = filtered)
                }
                refreshChats()
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val token = _uiState.value.session.accessToken
        val chat = _uiState.value.activeChat ?: return
        viewModelScope.launch {
            api.toggleReaction(token, messageId, emoji).onSuccess {
                api.getChatMessages(token, chat.id).onSuccess { list ->
                    messagesCache[chat.id] = list
                    _uiState.update { it.copy(messages = list) }
                }
            }
        }
    }

    fun togglePinMessage(messageId: String) {
        val token = _uiState.value.session.accessToken
        val chat = _uiState.value.activeChat ?: return
        val alreadyPinned = _uiState.value.pinnedMessages.any { it.id == messageId }
        viewModelScope.launch {
            if (alreadyPinned) {
                api.unpinMessage(token, chat.id, messageId)
                showToast("Сообщение откреплено")
            } else {
                api.pinMessage(token, chat.id, messageId)
                showToast("Сообщение закреплено")
            }
            api.getPinnedMessages(token, chat.id).onSuccess { pins ->
                _uiState.update { it.copy(pinnedMessages = pins) }
            }
        }
    }

    fun toggleChatPin(chat: ChatDto) {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.updateChatPreferences(token, chat.id, pinned = !chat.pinned).onSuccess {
                refreshChats()
                showToast(if (!chat.pinned) "Чат закреплен" else "Чат откреплен")
            }
        }
    }

    fun toggleChatMute(chat: ChatDto) {
        val token = _uiState.value.session.accessToken
        val muteMins = if (chat.isMuted) 0 else 480
        viewModelScope.launch {
            api.updateChatPreferences(token, chat.id, muteMinutes = muteMins).onSuccess {
                refreshChats()
                showToast(if (muteMins > 0) "Уведомления отключены" else "Уведомления включены")
            }
        }
    }

    fun clearChatHistory(chatId: String) {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.clearChatForEveryone(token, chatId).onSuccess {
                messagesCache.remove(chatId)
                _uiState.update { state ->
                    if (state.activeChat?.id == chatId) state.copy(messages = emptyList()) else state
                }
                refreshChats()
                showToast("История чата очищена")
            }
        }
    }

    fun deleteChat(chatId: String) {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.deleteChatForEveryone(token, chatId).onSuccess {
                messagesCache.remove(chatId)
                if (_uiState.value.activeChat?.id == chatId) {
                    navigateBack()
                }
                refreshChats()
                showToast("Чат удален")
            }
        }
    }

    fun searchUsers(query: String) {
        val token = _uiState.value.session.accessToken
        if (query.trim().length < 2 || token.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            api.searchUsers(token, query).onSuccess { users ->
                _uiState.update { it.copy(searchResults = users) }
            }
        }
    }

    fun addContactByLogin(username: String) {
        val token = _uiState.value.session.accessToken
        if (username.isBlank() || token.isBlank()) return
        viewModelScope.launch {
            api.findUserByUsername(token, username).onSuccess { user ->
                startDirectChat(user)
            }.onFailure { err ->
                showToast(err.message ?: "Пользователь не найден")
            }
        }
    }

    fun startDirectChat(user: UserDto) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.createDirectChat(token, user.id).onSuccess { chat ->
                refreshChats()
                openChat(chat)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun createGroup(title: String, memberIds: List<String>) {
        val token = _uiState.value.session.accessToken
        if (title.trim().length < 2 || memberIds.isEmpty()) {
            showToast("Укажите название и выберите участников")
            return
        }
        viewModelScope.launch {
            api.createGroupChat(token, title, memberIds).onSuccess { chat ->
                refreshChats()
                openChat(chat)
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось создать группу")
            }
        }
    }

    fun addMemberToActiveGroup(username: String) {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank() || username.isBlank()) return
        viewModelScope.launch {
            api.findUserByUsername(token, username).onSuccess { user ->
                api.addGroupMembers(token, group.id, listOf(user.id)).onSuccess { ok ->
                    if (ok) {
                        refreshChats()
                        showToast("${user.effectiveName} добавлен(а) в группу")
                    } else {
                        showToast("Не удалось добавить участника")
                    }
                }
            }.onFailure { err ->
                showToast(err.message ?: "Пользователь не найден")
            }
        }
    }

    fun removeMemberFromActiveGroup(userId: String) {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            api.removeGroupMember(token, group.id, userId).onSuccess { ok ->
                if (ok) {
                    refreshChats()
                    showToast("Участник удален из группы")
                }
            }
        }
    }

    fun setMemberRoleInActiveGroup(userId: String, role: String) {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            api.setGroupMemberRole(token, group.id, userId, role).onSuccess { ok ->
                if (ok) {
                    refreshChats()
                    showToast("Роль участника обновлена: $role")
                }
            }
        }
    }

    fun muteMemberInActiveGroup(userId: String, duration: String = "1h") {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            api.muteGroupMember(token, group.id, userId, duration).onSuccess { ok ->
                if (ok) {
                    refreshChats()
                    showToast("Ограничение обновлено ($duration)")
                }
            }
        }
    }

    fun updateActiveGroupSettings(
        title: String? = null,
        isPrivate: Boolean? = null,
        joinApproval: Boolean? = null
    ) {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank()) return
        viewModelScope.launch {
            api.updateGroupSettings(token, group.id, title, isPrivate, joinApproval).onSuccess { updatedGroup ->
                _uiState.update { it.copy(activeChat = updatedGroup) }
                refreshChats()
                showToast("Настройки группы сохранены")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось обновить группу")
            }
        }
    }

    fun refreshActiveGroupInviteLink() {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank()) return
        viewModelScope.launch {
            api.refreshGroupInviteLink(token, group.id).onSuccess { updatedGroup ->
                _uiState.update { it.copy(activeChat = updatedGroup) }
                refreshChats()
                showToast("Пригласительная ссылка обновлена")
            }
        }
    }

    fun leaveActiveGroup() {
        val token = _uiState.value.session.accessToken
        val group = _uiState.value.activeChat ?: return
        if (token.isBlank()) return
        viewModelScope.launch {
            api.leaveGroup(token, group.id).onSuccess { ok ->
                if (ok) {
                    messagesCache.remove(group.id)
                    _uiState.update { it.copy(activeChat = null, overlayScreen = ActiveOverlayScreen.NONE) }
                    refreshChats()
                    showToast("Вы покинули группу")
                }
            }
        }
    }

    fun load2faStatus() {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.get2faStatus(token).onSuccess { st ->
                _uiState.update { it.copy(twoFactorStatus = st) }
            }
        }
    }

    fun start2faSetup() {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.start2faSetup(token).onSuccess { setupData ->
                _uiState.update { it.copy(twoFactorSetupData = setupData) }
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось начать настройку 2FA")
            }
        }
    }

    fun confirm2faSetup(code: String) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank() || code.trim().length < 6) {
            showToast("Введите 6-значный код из приложения")
            return
        }
        viewModelScope.launch {
            api.confirm2faSetup(token, code.trim()).onSuccess { st ->
                _uiState.update { it.copy(twoFactorStatus = st, twoFactorSetupData = null) }
                showToast("Двухфакторная защита (2FA) включена")
            }.onFailure { err ->
                showToast(err.message ?: "Неверный код подтверждения")
            }
        }
    }

    fun disable2fa(code: String) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank() || code.trim().length < 6) {
            showToast("Введите 6-значный код для отключения 2FA")
            return
        }
        viewModelScope.launch {
            api.disable2fa(token, code.trim()).onSuccess { st ->
                _uiState.update { it.copy(twoFactorStatus = st, twoFactorSetupData = null) }
                showToast("2FA отключена")
            }.onFailure { err ->
                showToast(err.message ?: "Неверный 2FA код")
            }
        }
    }

    fun toggle2faDeviceApproval(enabled: Boolean) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.set2faDeviceApproval(token, enabled).onSuccess { st ->
                _uiState.update { it.copy(twoFactorStatus = st) }
                showToast(if (enabled) "Подтверждение входа с устройства включено" else "Подтверждение входа отключено")
            }
        }
    }

    fun setPinCode(pin: String) {
        viewModelScope.launch {
            val clean = pin.trim()
            sessionStore.setPinCode(clean)
            if (clean.isBlank()) {
                _uiState.update { it.copy(isAppLocked = false) }
                showToast("PIN-код отключен")
            } else {
                showToast("PIN-код установлен")
            }
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val storedPin = _uiState.value.session.pinCode
        return if (storedPin.isBlank() || pin.trim() == storedPin) {
            _uiState.update { it.copy(isAppLocked = false) }
            true
        } else {
            showToast("Неверный PIN-код")
            false
        }
    }

    fun lockAppNow() {
        if (_uiState.value.session.pinCode.isNotBlank()) {
            _uiState.update { it.copy(isAppLocked = true) }
        } else {
            showToast("Сначала установите PIN-код в разделе Безопасность")
        }
    }

    fun loadBlockedUsers() {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.listBlockedUsers(token).onSuccess { list ->
                _uiState.update { it.copy(blockedUsers = list) }
            }
        }
    }

    fun blockUser(userId: String) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            api.blockUser(token, userId).onSuccess { ok ->
                if (ok) {
                    loadBlockedUsers()
                    showToast("Пользователь заблокирован")
                }
            }
        }
    }

    fun unblockUser(userId: String) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            api.unblockUser(token, userId).onSuccess { ok ->
                if (ok) {
                    loadBlockedUsers()
                    showToast("Пользователь разблокирован")
                }
            }
        }
    }

    fun reportTarget(targetId: String, targetType: String, reason: String, comment: String = "") {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.reportTarget(token, targetId, targetType, reason, comment).onSuccess { ok ->
                if (ok) {
                    showToast("Жалоба отправлена модераторам Neongram")
                } else {
                    showToast("Не удалось отправить жалобу")
                }
            }
        }
    }

    fun updateMyProfile(displayName: String, username: String, bio: String, birthday: String?) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            api.updateProfile(token, displayName, username, bio, birthday).onSuccess { updated ->
                _uiState.update { it.copy(currentUser = updated, isLoading = false) }
                showToast("Профиль сохранен")
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message, isLoading = false) }
            }
        }
    }

    fun updatePrivacySettings(
        showOnline: Boolean,
        showLastSeen: Boolean,
        allowCalls: Boolean,
        callsContactsOnly: Boolean,
        readReceipts: Boolean
    ) {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.updatePrivacy(token, showOnline, showLastSeen, allowCalls, callsContactsOnly, readReceipts).onSuccess {
                refreshAll()
                showToast("Настройки приватности сохранены")
            }
        }
    }

    fun changePassword(currentPass: String, newPass: String) {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.changePassword(token, currentPass, newPass).onSuccess {
                showToast("Пароль успешно изменен")
            }.onFailure { err ->
                showToast(err.message ?: "Ошибка смены пароля")
            }
        }
    }

    fun loadSessions() {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.getSessions(token).onSuccess { resp ->
                _uiState.update {
                    it.copy(
                        activeSessions = resp.sessions,
                        currentSessionId = resp.currentSessionId
                    )
                }
            }
        }
    }

    fun terminateOtherSessions() {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.terminateOtherSessions(token).onSuccess {
                loadSessions()
                showToast("Остальные сеансы завершены")
            }
        }
    }

    fun terminateSession(sessionId: String) {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.terminateSession(token, sessionId).onSuccess {
                loadSessions()
                showToast("Сеанс завершен")
            }
        }
    }

    fun loadWeather(cityName: String = "Bishkek", lat: Double = 42.8746, lon: Double = 74.5698) {
        viewModelScope.launch {
            api.fetchWeatherForecast(cityName, "Kyrgyzstan", lat, lon).onSuccess { data ->
                _uiState.update { it.copy(weatherData = data) }
            }
        }
    }

    fun searchAndLoadWeatherCity(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            api.searchWeatherCity(query).onSuccess { (name, lat, lon) ->
                loadWeather(name, lat, lon)
            }.onFailure {
                showToast("Город не найден")
            }
        }
    }

    fun loadCryptoRates() {
        viewModelScope.launch {
            api.fetchCryptoRates().onSuccess { list ->
                _uiState.update { it.copy(cryptoRates = list) }
            }
        }
    }

    fun loadMusic(query: String = "Phonk Neon") {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.searchMusicTracks(token, query).onSuccess { tracks ->
                _uiState.update { it.copy(musicTracks = tracks) }
            }
            api.getMusicFavorites(token).onSuccess { favs ->
                _uiState.update { it.copy(musicFavorites = favs) }
            }
        }
    }

    fun loadWallet() {
        val token = _uiState.value.session.accessToken
        viewModelScope.launch {
            api.getWalletSessions(token).onSuccess { list ->
                _uiState.update { it.copy(walletSessions = list) }
            }
        }
    }

    fun saveServerUrl(url: String = "https://cloud.neongram.space") {
        viewModelScope.launch {
            api.baseUrl = "https://cloud.neongram.space"
            sessionStore.setServerUrl("https://cloud.neongram.space")
            showToast("Сервер: ${api.baseUrl}")
        }
    }

    fun testServerHealth(url: String = "https://cloud.neongram.space") {
        viewModelScope.launch {
            api.checkHealth("https://cloud.neongram.space").onSuccess { ok ->
                showToast(if (ok) "Сервер доступен (OK)" else "Сервер недоступен")
            }.onFailure {
                showToast("Ошибка соединения с сервером")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            qrPollingJob?.cancel()
            messagesCache.clear()
            socketManager.disconnect()
            sessionStore.clearSession()
        }
    }
}
