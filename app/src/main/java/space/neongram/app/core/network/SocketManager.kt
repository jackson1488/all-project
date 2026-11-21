package space.neongram.app.core.network

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.json.JSONObject
import space.neongram.app.core.model.MessageDto
import java.net.URI

class SocketManager {
    private var socket: Socket? = null
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _incomingMessages = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MessageDto> = _incomingMessages

    private val _chatUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val chatUpdates: SharedFlow<String> = _chatUpdates

    fun connect(serverUrl: String, accessToken: String) {
        disconnect()
        if (accessToken.isBlank()) return

        runCatching {
            val options = IO.Options.builder()
                .setTransports(arrayOf("websocket", "polling"))
                .setAuth(
                    mapOf(
                        "token" to accessToken,
                        "platform" to "android-kotlin",
                        "foreground" to "1"
                    )
                )
                .setExtraHeaders(
                    mapOf("Authorization" to listOf("Bearer $accessToken"))
                )
                .setReconnection(true)
                .build()

            val s = IO.socket(URI.create(serverUrl.trimEnd('/')), options)

            s.on(Socket.EVENT_CONNECT) {
                _connected.value = true
                val presence = JSONObject().apply {
                    put("active", true)
                    put("platform", "android-kotlin")
                }
                s.emit("client:presence", presence)
            }

            s.on(Socket.EVENT_DISCONNECT) {
                _connected.value = false
            }

            s.on("message:new") { args ->
                val raw = args.firstOrNull()?.toString() ?: return@on
                runCatching {
                    val msg = json.decodeFromString<MessageDto>(raw)
                    _incomingMessages.tryEmit(msg)
                }
            }

            s.on("chat:updated") { args ->
                val raw = args.firstOrNull()?.toString().orEmpty()
                _chatUpdates.tryEmit(raw)
            }

            s.on("chat:created") { args ->
                val raw = args.firstOrNull()?.toString().orEmpty()
                _chatUpdates.tryEmit(raw)
            }

            s.connect()
            socket = s
        }
    }

    fun joinChatRoom(chatId: String) {
        val payload = JSONObject().apply {
            put("chatId", chatId)
        }
        socket?.emit("chat:join", payload)
    }

    fun sendTyping(chatId: String, isTyping: Boolean) {
        val payload = JSONObject().apply {
            put("chatId", chatId)
            put("isTyping", isTyping)
        }
        socket?.emit("typing", payload)
    }

    fun disconnect() {
        runCatching {
            socket?.off()
            socket?.disconnect()
        }
        socket = null
        _connected.value = false
    }
}
