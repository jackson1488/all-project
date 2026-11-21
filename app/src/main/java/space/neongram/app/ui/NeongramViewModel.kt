package space.neongram.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    SERVICE_WEATHER,
    SERVICE_CURRENCY,
    SERVICE_MUSIC,
    SERVICE_GAMES,
    SERVICE_WALLET,
    SERVICE_BANK,
    SERVICE_BROWSER,
    SETTINGS_ACCOUNT,
    SETTINGS_ACCOUNT_INFO,
    SETTINGS_PRIVACY,
    SETTINGS_SECURITY,
    SETTINGS_DEVICES,
    SETTINGS_APPEARANCE,
    SETTINGS_STORAGE,
    SETTINGS_SERVER,
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
    val searchResults: List<UserDto> = emptyList(),
    val peerProfileUser: UserDto? = null,
    val myStories: List<StoryDto> = emptyList(),
    val activeSessions: List<SessionItemDto> = emptyList(),
    val currentSessionId: String? = null,
    val qrAuthToken: String = "",
    val twoFactorChallengeId: String? = null,
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

    private val _uiState = MutableStateFlow(NeongramUiState())
    val uiState: StateFlow<NeongramUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { stored ->
                api.baseUrl = stored.serverUrl.ifBlank { "https://cloud.neongram.space" }
                _uiState.update { it.copy(session = stored) }
                if (stored.isAuthenticated) {
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
                            overlayScreen = ActiveOverlayScreen.NONE
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
            ActiveOverlayScreen.SERVICE_WEATHER -> loadWeather()
            ActiveOverlayScreen.SERVICE_CURRENCY -> loadCryptoRates()
            ActiveOverlayScreen.SERVICE_MUSIC -> loadMusic()
            ActiveOverlayScreen.SERVICE_WALLET -> loadWallet()
            ActiveOverlayScreen.SETTINGS_DEVICES -> loadSessions()
            else -> {}
        }
    }

    fun navigateBack() {
        val current = _uiState.value.overlayScreen
        val next = when (current) {
            ActiveOverlayScreen.GROUP_INFO -> ActiveOverlayScreen.CHAT_ROOM
            ActiveOverlayScreen.PEER_PROFILE -> if (_uiState.value.activeChat != null) ActiveOverlayScreen.CHAT_ROOM else ActiveOverlayScreen.NONE
            ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO -> ActiveOverlayScreen.SETTINGS_ACCOUNT
            ActiveOverlayScreen.CHAT_ROOM -> {
                _uiState.update { it.copy(activeChat = null, messages = emptyList(), replyToMessage = null, editingMessage = null) }
                refreshChats()
                ActiveOverlayScreen.NONE
            }
            else -> ActiveOverlayScreen.NONE
        }
        _uiState.update { it.copy(overlayScreen = next) }
    }

    fun login(username: String, password: String, serverUrl: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Заполните все поля") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            api.baseUrl = serverUrl.trimEnd('/')
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

    fun register(username: String, password: String, confirmPassword: String, serverUrl: String) {
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
            api.baseUrl = serverUrl.trimEnd('/')
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
        viewModelScope.launch {
            api.startAuthQr().onSuccess { resp ->
                _uiState.update { it.copy(qrAuthToken = resp.token) }
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
        }
    }

    fun refreshChats() {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.getChats(token).onSuccess { list ->
                _uiState.update { it.copy(chats = list) }
            }
        }
    }

    fun openChat(chat: ChatDto) {
        val token = _uiState.value.session.accessToken
        _uiState.update {
            it.copy(
                activeChat = chat,
                messages = emptyList(),
                pinnedMessages = emptyList(),
                replyToMessage = null,
                editingMessage = null,
                isLoading = true,
                overlayScreen = ActiveOverlayScreen.CHAT_ROOM
            )
        }
        socketManager.joinChatRoom(chat.id)
        viewModelScope.launch {
            api.getChatMessages(token, chat.id).onSuccess { list ->
                _uiState.update { it.copy(messages = list, isLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message, isLoading = false) }
            }
            api.getPinnedMessages(token, chat.id).onSuccess { pins ->
                _uiState.update { it.copy(pinnedMessages = pins) }
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

    fun sendMessage(text: String) {
        val content = text.trim()
        val chat = _uiState.value.activeChat ?: return
        val token = _uiState.value.session.accessToken
        if (content.isBlank() || token.isBlank()) return

        val editing = _uiState.value.editingMessage
        if (editing != null) {
            viewModelScope.launch {
                api.editMessage(token, editing.id, content).onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            editingMessage = null,
                            messages = state.messages.map { if (it.id == updated.id) updated else it }
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
                    if (exists) state else state.copy(messages = state.messages + sent)
                }
                refreshChats()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val token = _uiState.value.session.accessToken
        val chat = _uiState.value.activeChat ?: return
        viewModelScope.launch {
            api.deleteMessageForEveryone(token, messageId).onSuccess {
                _uiState.update { state ->
                    state.copy(messages = state.messages.filterNot { it.id == messageId })
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

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            api.baseUrl = url.trimEnd('/')
            sessionStore.setServerUrl(url.trimEnd('/'))
            showToast("Сервер сохранен: ${api.baseUrl}")
        }
    }

    fun testServerHealth(url: String) {
        viewModelScope.launch {
            api.checkHealth(url).onSuccess { ok ->
                showToast(if (ok) "Сервер доступен (OK)" else "Сервер недоступен")
            }.onFailure {
                showToast("Ошибка соединения с сервером")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            socketManager.disconnect()
            sessionStore.clearSession()
        }
    }
}
