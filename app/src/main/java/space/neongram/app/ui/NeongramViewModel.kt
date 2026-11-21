package space.neongram.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import space.neongram.app.core.model.ChatDto
import space.neongram.app.core.model.MessageDto
import space.neongram.app.core.model.UserDto
import space.neongram.app.core.network.NeongramApi
import space.neongram.app.core.network.SocketManager
import space.neongram.app.core.session.SessionStore
import space.neongram.app.core.session.StoredSession

data class NeongramUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: StoredSession = StoredSession(),
    val currentUser: UserDto? = null,
    val chats: List<ChatDto> = emptyList(),
    val activeChat: ChatDto? = null,
    val messages: List<MessageDto> = emptyList(),
    val searchResults: List<UserDto> = emptyList(),
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
                            messages = emptyList()
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

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login(username: String, password: String, serverUrl: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Введите логин и пароль") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            api.baseUrl = serverUrl.trimEnd('/')
            val result = api.login(username, password)
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
                } else if (resp.requiresTwoFactor) {
                    _uiState.update { it.copy(errorMessage = "Для аккаунта включена 2FA") }
                } else {
                    _uiState.update { it.copy(errorMessage = resp.error ?: "Не удалось войти") }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Ошибка подключения к серверу") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun register(username: String, password: String, serverUrl: String) {
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
                } else {
                    _uiState.update { it.copy(errorMessage = resp.error ?: "Ошибка регистрации") }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Ошибка подключения к серверу") }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshAll() {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            api.getMe(token).onSuccess { me ->
                _uiState.update { it.copy(currentUser = me) }
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
        _uiState.update { it.copy(activeChat = chat, messages = emptyList(), isLoading = true) }
        socketManager.joinChatRoom(chat.id)
        viewModelScope.launch {
            api.getChatMessages(token, chat.id).onSuccess { list ->
                _uiState.update { it.copy(messages = list, isLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message, isLoading = false) }
            }
        }
    }

    fun closeActiveChat() {
        _uiState.update { it.copy(activeChat = null, messages = emptyList()) }
        refreshChats()
    }

    fun sendMessage(text: String) {
        val content = text.trim()
        val chat = _uiState.value.activeChat ?: return
        val token = _uiState.value.session.accessToken
        if (content.isBlank() || token.isBlank()) return

        viewModelScope.launch {
            api.sendMessage(token, chat.id, content).onSuccess { sent ->
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

    fun updateMyProfile(displayName: String, bio: String) {
        val token = _uiState.value.session.accessToken
        if (token.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            api.updateProfile(token, displayName, bio).onSuccess { updated ->
                _uiState.update { it.copy(currentUser = updated, isLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message, isLoading = false) }
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
