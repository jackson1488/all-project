package space.neongram.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: String? = null,
    val showOnline: Boolean = true,
    val showLastSeen: Boolean = true
) {
    val effectiveName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username.ifBlank { "Neongram User" }
}

@Serializable
data class AuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val csrfToken: String? = null,
    val requiresTwoFactor: Boolean = false,
    val challengeId: String? = null,
    val user: UserDto? = null,
    val error: String? = null
)

@Serializable
data class ChatMemberDto(
    val userId: String = "",
    val role: String = "MEMBER",
    val user: UserDto? = null
)

@Serializable
data class MessageReactionDto(
    val id: String = "",
    val emoji: String = "",
    val userId: String = ""
)

@Serializable
data class MessageReplyDto(
    val id: String = "",
    val type: String = "TEXT",
    val content: String? = null,
    val sender: UserDto? = null
)

@Serializable
data class MessageDto(
    val id: String = "",
    val chatId: String = "",
    val senderId: String? = null,
    val type: String = "TEXT",
    val content: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val createdAt: String = "",
    val editedAt: String? = null,
    val sender: UserDto? = null,
    val replyTo: MessageReplyDto? = null,
    val reactions: List<MessageReactionDto> = emptyList()
)

@Serializable
data class ChatDto(
    val id: String = "",
    val type: String = "DIRECT",
    val title: String? = null,
    val avatarUrl: String? = null,
    val isSecure: Boolean = false,
    val pinned: Boolean = false,
    val updatedAt: String = "",
    val members: List<ChatMemberDto> = emptyList(),
    val messages: List<MessageDto> = emptyList()
) {
    fun resolveDisplayTitle(currentUserId: String): String {
        if (!title.isNullOrBlank()) return title
        if (type == "DIRECT") {
            val peer = members.firstOrNull { it.userId != currentUserId }?.user
                ?: members.firstOrNull()?.user
            if (peer != null) return peer.effectiveName
        }
        return "Neongram Chat"
    }

    fun resolvePeerUser(currentUserId: String): UserDto? {
        return members.firstOrNull { it.userId != currentUserId }?.user
            ?: members.firstOrNull()?.user
    }
}

@Serializable
data class ChatsListResponse(
    val chats: List<ChatDto> = emptyList()
)

@Serializable
data class MessagesListResponse(
    val messages: List<MessageDto> = emptyList()
)

@Serializable
data class SendMessageResponse(
    val message: MessageDto? = null,
    val error: String? = null
)

@Serializable
data class UsersSearchResponse(
    val users: List<UserDto> = emptyList()
)

@Serializable
data class MeResponse(
    val user: UserDto? = null
)

@Serializable
data class CreateChatResponse(
    val chat: ChatDto? = null
)
