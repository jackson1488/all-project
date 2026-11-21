package space.neongram.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val bio: String? = null,
    val birthday: String? = null,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: String? = null,
    val showOnline: Boolean = true,
    val showLastSeen: Boolean = true,
    val allowCalls: Boolean = true,
    val callsContactsOnly: Boolean = false,
    val readReceipts: Boolean = true,
    val socialLinks: Map<String, String> = emptyMap()
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
    val deviceApprovalEnabled: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class ChatMemberDto(
    val userId: String = "",
    val role: String = "MEMBER",
    val status: String = "ACTIVE",
    val user: UserDto? = null
)

@Serializable
data class MessageReactionDto(
    val id: String = "",
    val emoji: String = "",
    val userId: String = ""
)

@Serializable
data class MessageReceiptDto(
    val userId: String = "",
    val readAt: String? = null
)

@Serializable
data class MessageReplyDto(
    val id: String = "",
    val type: String = "TEXT",
    val content: String? = null,
    val sender: UserDto? = null
)

@Serializable
data class SystemInboxEntryDto(
    val id: String = "",
    val type: String = "",
    val title: String? = null,
    val body: String? = null,
    val isRead: Boolean = false,
    val createdAt: String = ""
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
    val fileSize: Long? = null,
    val createdAt: String = "",
    val editedAt: String? = null,
    val sender: UserDto? = null,
    val replyTo: MessageReplyDto? = null,
    val reactions: List<MessageReactionDto> = emptyList(),
    val receipts: List<MessageReceiptDto> = emptyList(),
    val systemInboxEntry: SystemInboxEntryDto? = null
) {
    val isForwarded: Boolean
        get() = content?.startsWith("[FWD]") == true

    val cleanContent: String
        get() = content?.removePrefix("[FWD]")?.trim().orEmpty()
}

@Serializable
data class ChatDto(
    val id: String = "",
    val type: String = "DIRECT",
    val title: String? = null,
    val avatarUrl: String? = null,
    val isSecure: Boolean = false,
    val isAi: Boolean = false,
    val isSystem: Boolean = false,
    val isSupport: Boolean = false,
    val isPrivateGroup: Boolean = false,
    val joinApprovalRequired: Boolean = false,
    val inviteToken: String? = null,
    val pinned: Boolean = false,
    val muteUntil: String? = null,
    val lastReadAt: String? = null,
    val updatedAt: String = "",
    val members: List<ChatMemberDto> = emptyList(),
    val messages: List<MessageDto> = emptyList()
) {
    val isSavedMessages: Boolean
        get() = title == "Saved Messages" || (type == "DIRECT" && members.size == 1)

    val isAiChat: Boolean
        get() = isAi || type == "AI" || title?.contains("Neongram AI", ignoreCase = true) == true

    val isSystemChat: Boolean
        get() = isSystem || type == "SYSTEM" || title?.contains("Системный", ignoreCase = true) == true || title?.contains("System", ignoreCase = true) == true

    val isMuted: Boolean
        get() = !muteUntil.isNullOrBlank()

    fun resolveDisplayTitle(currentUserId: String, savedLabel: String = "Избранное"): String {
        if (isSavedMessages) return savedLabel
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

    fun resolveAvatarUrl(currentUserId: String, serverBaseUrl: String = "https://cloud.neongram.space"): String? {
        val raw = avatarUrl?.takeIf { it.isNotBlank() }
            ?: resolvePeerUser(currentUserId)?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = serverBaseUrl.ifBlank { "https://cloud.neongram.space" }.trimEnd('/')
        return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
    }
}

@Serializable
data class PinnedMessageItemDto(
    val id: String = "",
    val chatId: String = "",
    val message: MessageDto? = null
)

@Serializable
data class PinsListResponse(
    val pins: List<PinnedMessageItemDto> = emptyList()
)

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
data class GroupsSearchResponse(
    val groups: List<ChatDto> = emptyList()
)

@Serializable
data class MeResponse(
    val user: UserDto? = null
)

@Serializable
data class CreateChatResponse(
    val chat: ChatDto? = null
)

@Serializable
data class SessionItemDto(
    val id: String = "",
    val deviceName: String? = null,
    val clientPlatform: String? = null,
    val clientOsName: String? = null,
    val ipAddress: String? = null,
    val lastActiveAt: String? = null,
    val createdAt: String? = null
)

@Serializable
data class SessionsListResponse(
    val sessions: List<SessionItemDto> = emptyList(),
    val currentSessionId: String? = null
)

@Serializable
data class StoryDto(
    val id: String = "",
    val userId: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "PHOTO",
    val caption: String? = null,
    val isHidden: Boolean = false,
    val createdAt: String = ""
)

@Serializable
data class StoriesListResponse(
    val stories: List<StoryDto> = emptyList()
)

@Serializable
data class QrStartResponse(
    val token: String = "",
    val expiresAt: String? = null
)

@Serializable
data class QrStatusResponse(
    val status: String = "pending",
    val approvedBy: UserDto? = null
)

@Serializable
data class TwoFactorStatusResponse(
    val enabled: Boolean = false,
    val setupPending: Boolean = false,
    val deviceApprovalEnabled: Boolean = false
)

@Serializable
data class TwoFactorSetupResponse(
    val secret: String = "",
    val otpauthUrl: String = ""
)

@Serializable
data class MusicTrackDto(
    val videoId: String = "",
    val title: String = "",
    val channelTitle: String = "",
    val thumbnailUrl: String? = null,
    val durationText: String? = null
)

@Serializable
data class MusicSearchResponse(
    val items: List<MusicTrackDto> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class MusicFavoritesResponse(
    val favorites: List<MusicTrackDto> = emptyList()
)

@Serializable
data class WalletSessionItemDto(
    val walletAddress: String = "",
    val walletName: String? = null,
    val walletAppName: String? = null,
    val network: String = "mainnet",
    val isActive: Boolean = false
)

@Serializable
data class WalletSessionsResponse(
    val sessions: List<WalletSessionItemDto> = emptyList()
)

@Serializable
data class BankHomeResponse(
    val registered: Boolean = false,
    val balance: Double = 0.0,
    val cardNumber: String? = null,
    val currency: String = "KGS"
)

data class WeatherForecastData(
    val cityName: String = "Bishkek",
    val country: String = "Kyrgyzstan",
    val latitude: Double = 42.8746,
    val longitude: Double = 74.5698,
    val tempCurrent: Double = 0.0,
    val feelsLike: Double = 0.0,
    val windSpeed: Double = 0.0,
    val humidity: Int = 0,
    val weatherCode: Int = 0,
    val hourlyTemps: List<Pair<String, Double>> = emptyList(),
    val dailyForecast: List<DailyWeatherItem> = emptyList(),
    val updatedAt: String = ""
)

data class DailyWeatherItem(
    val date: String,
    val tempMax: Double,
    val tempMin: Double,
    val weatherCode: Int
)

data class CryptoCoinRate(
    val id: String,
    val symbol: String,
    val name: String,
    val currentPriceUsd: Double,
    val change24hPercent: Double
)
