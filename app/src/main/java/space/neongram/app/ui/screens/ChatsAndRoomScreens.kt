package space.neongram.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import space.neongram.app.core.model.ChatDto
import space.neongram.app.core.model.ChatMemberDto
import space.neongram.app.core.model.MessageDto
import space.neongram.app.core.model.UserDto
import space.neongram.app.core.ui.AnimatedNeonBackground
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeonAvatar
import space.neongram.app.core.ui.NeonInput
import space.neongram.app.core.ui.PageScaffold
import space.neongram.app.core.ui.PrimaryButton
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.ActiveOverlayScreen
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel

// 2026 iOS 18 Liquid Glass & Aurora Violet Brushes & Palette
private val LiquidGlassFillBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.05f)
    )
)

private val LiquidGlassSpecularBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.38f),
        Color(0xFF00F0FF).copy(alpha = 0.22f),
        Color(0xFFA855F7).copy(alpha = 0.12f)
    )
)

private val NeonAvatarRingBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF00F0FF),
        Color(0xFFA855F7),
        Color(0xFFEC4899)
    )
)

private val OutgoingAuroraBubbleBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xCC6D28D9),
        Color(0xB30891B2)
    )
)

private fun formatIsoTimeToHhMm(iso: String?): String {
    val raw = iso?.trim().orEmpty()
    if (raw.isBlank()) return ""
    val tIndex = raw.indexOf('T')
    if (tIndex != -1 && raw.length >= tIndex + 6) {
        return raw.substring(tIndex + 1, tIndex + 6)
    }
    if (raw.length >= 5 && raw[2] == ':') {
        return raw.take(5)
    }
    return raw.take(10)
}

private fun resolveFullMediaUrl(rawUrl: String?, serverBaseUrl: String): String? {
    val clean = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    val base = serverBaseUrl.trimEnd('/')
    return if (clean.startsWith("/")) "$base$clean" else "$base/$clean"
}

private fun formatLastMessagePreview(msg: MessageDto?, lang: String): String {
    if (msg == null) {
        return tr(lang, "Нет сообщений", "Билдирүүлөр жок", "No messages yet")
    }
    if (msg.systemInboxEntry != null || msg.type.uppercase() == "SYSTEM") {
        val sysTitle = msg.systemInboxEntry?.title ?: msg.cleanContent
        return sysTitle.ifBlank { tr(lang, "Системное уведомление", "Тутумдук билдирүү", "System notification") }
    }
    return when (msg.type.uppercase()) {
        "PHOTO", "IMAGE" -> {
            val cap = msg.cleanContent
            if (cap.isNotBlank()) "📷 $cap" else tr(lang, "Фото", "Сүрөт", "Photo")
        }
        "VIDEO" -> {
            val cap = msg.cleanContent
            if (cap.isNotBlank()) "🎬 $cap" else tr(lang, "Видео", "Видео", "Video")
        }
        "VOICE", "AUDIO" -> tr(lang, "Голосовое сообщение", "Үн билдирүү", "Voice message")
        "FILE", "DOCUMENT" -> msg.fileName?.takeIf { it.isNotBlank() } ?: tr(lang, "Файл", "Файл", "File")
        else -> msg.cleanContent.ifBlank { tr(lang, "Сообщение", "Билдирүү", "Message") }
    }
}

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet ChatsTabScreen (`template_3_ios_liquid_aurora`):
 * - Top Frosted Capsule Search Bar (`RoundedCornerShape(24.dp)`, `height = 50.dp`, translucent glass fill `Color.White.copy(alpha = 0.09f)`, `1.dp` specular glass border, search icon + `"Search chats / Поиск чатов"`).
 * - Large bold header row: `"Chats / Чаты"` (`34.sp`, ExtraBold, white) + right circular frosted glass compose/AI button (`42.dp` circle with `1.dp` specular border and glowing cyan icon).
 * - Liquid Glass Chat Cards (`LazyColumn` with `12.dp` spacing, `paddingHorizontal = 16.dp`, `paddingBottom = 110.dp` for the floating bottom glass dock).
 */
@Composable
fun ChatsTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language.ifBlank { "ru" }
    val currentUserId = state.session.userId
    val serverUrl = state.session.serverUrl

    var searchQuery by remember { mutableStateOf("") }
    var contextMenuChat by remember { mutableStateOf<ChatDto?>(null) }
    var deleteConfirmChat by remember { mutableStateOf<ChatDto?>(null) }
    var deleteForEveryone by remember { mutableStateOf(true) }

    val visibleChats = remember(state.chats) {
        state.chats.filter { !it.isSupport }
    }

    val isSearchActive = searchQuery.trim().length >= 2

    val filteredLocalChats = remember(visibleChats, searchQuery, currentUserId, lang) {
        val q = searchQuery.trim().lowercase()
        if (q.length < 2) {
            visibleChats
        } else {
            visibleChats.filter { chat ->
                val title = chat.resolveDisplayTitle(
                    currentUserId = currentUserId,
                    savedLabel = tr(lang, "Избранное", "Тандалмалар", "Saved Messages")
                ).lowercase()
                val peerUser = chat.resolvePeerUser(currentUserId)?.username?.lowercase().orEmpty()
                title.contains(q) || peerUser.contains(q)
            }
        }
    }

    val pinnedChats = remember(visibleChats) {
        visibleChats.filter { it.pinned }
    }

    val normalChats = remember(visibleChats) {
        visibleChats.filter { !it.pinned }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.bg)
    ) {
        AnimatedNeonBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Header & Frosted Capsule Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Frosted Capsule Search Bar (`RoundedCornerShape(24.dp)`, `height = 50.dp`)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(scheme.surface.copy(alpha = 0.65f))
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(
                            width = 1.dp,
                            brush = LiquidGlassSpecularBorderBrush,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFFB4BEE0),
                        modifier = Modifier.size(20.dp)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = tr(lang, "Поиск чатов", "Чаттарды издөө", "Search chats"),
                                color = Color(0xFF9AA6D1),
                                fontSize = (15f * scheme.fontScale).sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { next ->
                                searchQuery = next
                                viewModel.searchUsers(next)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color(0xFFF8FAFF),
                                fontSize = (15f * scheme.fontScale).sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF00F0FF)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    searchQuery = ""
                                    viewModel.searchUsers("")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFFF8FAFF),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Large bold header row: "Chats / Чаты" (34.sp, ExtraBold, white) + right circular frosted glass compose/AI button (42.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tr(lang, "Чаты", "Чаттар", "Chats"),
                        color = Color(0xFFF8FAFF),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Circular Frosted Glass AI / Compose Button (42.dp circle with 1.dp specular border and glowing cyan icon)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(scheme.surface.copy(alpha = 0.65f))
                                .background(LiquidGlassFillBrush)
                                .border(1.dp, LiquidGlassSpecularBorderBrush, CircleShape)
                                .clickable { viewModel.openAiAssistantChat() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Neongram AI",
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Liquid Glass Chat Cards (`LazyColumn` with `12.dp` spacing, `paddingHorizontal = 16.dp`, `paddingBottom = 110.dp`)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSearchActive) {
                    item(key = "section_chats_header") {
                        SectionUppercaseHeader(
                            title = tr(lang, "ЧАТЫ", "ЧАТТАР", "CHATS")
                        )
                    }

                    if (filteredLocalChats.isEmpty()) {
                        item(key = "no_local_chats") {
                            EmptyHintCard(
                                text = tr(lang, "Чаты не найдены", "Чаттар табылган жок", "No chats found")
                            )
                        }
                    } else {
                        items(
                            items = filteredLocalChats,
                            key = { "search_chat_${it.id}" }
                        ) { chat ->
                            LiquidGlassChatCard(
                                chat = chat,
                                currentUserId = currentUserId,
                                serverUrl = serverUrl,
                                lang = lang,
                                onClick = { viewModel.openChat(chat) },
                                onOpenMenu = { contextMenuChat = chat }
                            )
                        }
                    }

                    item(key = "section_users_header") {
                        Spacer(modifier = Modifier.height(6.dp))
                        SectionUppercaseHeader(
                            title = tr(lang, "ПОЛЬЗОВАТЕЛИ", "КОЛДОНУУЧУЛАР", "USERS")
                        )
                    }

                    if (state.searchResults.isEmpty()) {
                        item(key = "no_remote_users") {
                            EmptyHintCard(
                                text = tr(lang, "Пользователи не найдены", "Колдонуучулар табылган жок", "No users found")
                            )
                        }
                    } else {
                        items(
                            items = state.searchResults,
                            key = { "search_user_${it.id}" }
                        ) { user ->
                            LiquidGlassUserSearchCard(
                                user = user,
                                serverUrl = serverUrl,
                                onClick = { viewModel.startDirectChat(user) }
                            )
                        }
                    }
                } else {
                    if (visibleChats.isEmpty()) {
                        item(key = "empty_chats_state") {
                            EmptyHintCard(
                                text = tr(
                                    lang,
                                    "Список чатов пуст. Найдите собеседника через поиск выше.",
                                    "Чаттар тизмеси бош. Жогорудагы издөө аркылуу маектеш табыңыз.",
                                    "No chats yet. Find someone using the search bar above."
                                )
                            )
                        }
                    } else {
                        if (pinnedChats.isNotEmpty()) {
                            item(key = "pinned_section_header") {
                                SectionUppercaseHeader(
                                    title = tr(lang, "ЗАКРЕПЛЕННЫЕ", "КАДАЛГАНДАР", "PINNED")
                                )
                            }

                            items(
                                items = pinnedChats,
                                key = { "pinned_chat_${it.id}" }
                            ) { chat ->
                                LiquidGlassChatCard(
                                    chat = chat,
                                    currentUserId = currentUserId,
                                    serverUrl = serverUrl,
                                    lang = lang,
                                    onClick = { viewModel.openChat(chat) },
                                    onOpenMenu = { contextMenuChat = chat }
                                )
                            }

                            if (normalChats.isNotEmpty()) {
                                item(key = "pinned_divider_spacer") {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }

                        items(
                            items = normalChats,
                            key = { "normal_chat_${it.id}" }
                        ) { chat ->
                            LiquidGlassChatCard(
                                chat = chat,
                                currentUserId = currentUserId,
                                serverUrl = serverUrl,
                                lang = lang,
                                onClick = { viewModel.openChat(chat) },
                                onOpenMenu = { contextMenuChat = chat }
                            )
                        }
                    }
                }
            }
        }
    }

    // Frosted Glass Bottom Sheet / Dialog for Chat Context Actions
    contextMenuChat?.let { targetChat ->
        val chatTitle = targetChat.resolveDisplayTitle(
            currentUserId = currentUserId,
            savedLabel = tr(lang, "Избранное", "Тандалмалар", "Saved Messages")
        )
        Dialog(
            onDismissRequest = { contextMenuChat = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable { contextMenuChat = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(scheme.surface.copy(alpha = 0.92f))
                        .background(LiquidGlassFillBrush)
                        .border(
                            width = 1.dp,
                            brush = LiquidGlassSpecularBorderBrush,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .clickable(enabled = false) {}
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Text(
                        text = chatTitle,
                        color = Color(0xFFF8FAFF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    ContextActionRow(
                        icon = Icons.Default.PushPin,
                        label = if (targetChat.pinned) {
                            tr(lang, "Открепить", "Бошотуу", "Unpin")
                        } else {
                            tr(lang, "Закрепить", "Кадоо", "Pin")
                        },
                        onClick = {
                            contextMenuChat = null
                            viewModel.toggleChatPin(targetChat)
                        }
                    )

                    ContextActionRow(
                        icon = if (targetChat.isMuted) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        label = if (targetChat.isMuted) {
                            tr(lang, "Со звуком", "Үнүн күйгүзүү", "Unmute")
                        } else {
                            tr(lang, "Без звука", "Үнсүз", "Mute")
                        },
                        onClick = {
                            contextMenuChat = null
                            viewModel.toggleChatMute(targetChat)
                        }
                    )

                    ContextActionRow(
                        icon = Icons.Default.Delete,
                        label = tr(lang, "Очистить", "Тазалоо", "Clear history"),
                        onClick = {
                            contextMenuChat = null
                            viewModel.clearChatHistory(targetChat.id)
                        }
                    )

                    ContextActionRow(
                        icon = Icons.Default.Delete,
                        label = tr(lang, "Удалить", "Өчүрүү", "Delete"),
                        danger = true,
                        onClick = {
                            val selected = targetChat
                            contextMenuChat = null
                            deleteForEveryone = true
                            deleteConfirmChat = selected
                        }
                    )
                }
            }
        }
    }

    // Delete Confirm Frosted Glass Dialog with "Удалить у всех" checkbox
    deleteConfirmChat?.let { targetChat ->
        val chatTitle = targetChat.resolveDisplayTitle(
            currentUserId = currentUserId,
            savedLabel = tr(lang, "Избранное", "Тандалмалар", "Saved Messages")
        )
        Dialog(onDismissRequest = { deleteConfirmChat = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surface.copy(alpha = 0.94f))
                    .background(LiquidGlassFillBrush)
                    .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(26.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr(lang, "Удалить чат?", "Чатты өчүрөсүзбү?", "Delete chat?"),
                    color = Color(0xFFF8FAFF),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = tr(
                        lang,
                        "Чат «$chatTitle» и вся переписка будут удалены.",
                        "«$chatTitle» чаты жана бардык билдирүүлөр өчүрүлөт.",
                        "Chat \"$chatTitle\" and its history will be deleted."
                    ),
                    color = Color(0xFFB4BEE0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                        .clickable { deleteForEveryone = !deleteForEveryone }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteForEveryone,
                        onCheckedChange = { deleteForEveryone = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF00F0FF),
                            uncheckedColor = Color(0xFF9AA6D1),
                            checkmarkColor = Color(0xFF06070C)
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr(lang, "Удалить у всех", "Баарынан өчүрүү", "Delete for everyone"),
                        color = Color(0xFFF8FAFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .clickable { deleteConfirmChat = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Отмена", "Жокко чыгаруу", "Cancel"),
                            color = Color(0xFFF8FAFF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.danger)
                            .clickable {
                                val id = targetChat.id
                                deleteConfirmChat = null
                                viewModel.deleteChat(id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Удалить", "Өчүрүү", "Delete"),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionUppercaseHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = Color(0xFF9AA6D1),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.0.sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyHintCard(text: String) {
    val scheme = LocalNeonScheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surface.copy(alpha = 0.65f))
            .background(LiquidGlassFillBrush)
            .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(26.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFB4BEE0),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun LiquidGlassUserSearchCard(
    user: UserDto,
    serverUrl: String,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(26.dp)
    val resolvedAvatar = resolveFullMediaUrl(user.avatarUrl, serverUrl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface.copy(alpha = 0.72f))
            .background(LiquidGlassFillBrush)
            .border(1.dp, LiquidGlassSpecularBorderBrush, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(2.dp, NeonAvatarRingBrush, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            NeonAvatar(
                avatarUrl = resolvedAvatar,
                label = user.effectiveName,
                sizeDp = 50.dp,
                cornerRadiusDp = 25.dp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = user.effectiveName,
                color = Color(0xFFF8FAFF),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${user.username}",
                color = Color(0xFFB4BEE0),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Frosted Liquid Glass Chat Card (`template_3_ios_liquid_aurora`):
 * - `RoundedCornerShape(26.dp)`, translucent gradient fill `Color.White.copy(alpha = 0.12f)` -> `Color.White.copy(alpha = 0.05f)` over `scheme.surface`,
 *   `1.dp` specular gradient border `White@0.38 -> Cyan@0.22 -> Purple@0.12`, `padding = 14.dp`.
 * - Left: Circular Avatar (`54.dp`) with glowing Cyan-to-Violet neon ring (`NeonAvatar` with `cornerRadiusDp = 27.dp`).
 * - Center: Chat Title (`17.sp`, Bold, `#F8FAFF`) + Message Preview (`14.sp`, `#B4BEE0`, 1-2 lines, with sparkle/media icon).
 * - Right: Timestamp (`12.sp`, `#9AA6D1`) + Glowing Cyan Circle Badge (`24.dp`, `Color(0xFF00F0FF)`, bold dark text) or glowing violet indicator dot / pin icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiquidGlassChatCard(
    chat: ChatDto,
    currentUserId: String,
    serverUrl: String,
    lang: String,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(26.dp)

    val displayTitle = chat.resolveDisplayTitle(
        currentUserId = currentUserId,
        savedLabel = tr(lang, "Избранное", "Тандалмалар", "Saved Messages")
    )
    val avatarUrl = chat.resolveAvatarUrl(currentUserId, serverUrl)
    val lastMsg = chat.messages.lastOrNull()
    val timestampText = formatIsoTimeToHhMm(lastMsg?.createdAt ?: chat.updatedAt)
    val previewText = formatLastMessagePreview(lastMsg, lang)

    val hasUnread = remember(chat, currentUserId) {
        val last = chat.messages.lastOrNull()
        val lastRead = chat.lastReadAt
        if (last == null || last.senderId == currentUserId) {
            false
        } else if (lastRead.isNullOrBlank()) {
            true
        } else {
            last.createdAt > lastRead
        }
    }

    val peerOnline = remember(chat, currentUserId) {
        if (chat.type == "DIRECT" && !chat.isSavedMessages && !chat.isAiChat && !chat.isSystemChat) {
            chat.resolvePeerUser(currentUserId)?.isOnline == true
        } else {
            false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (chat.isSecure) {
                    Color(0x286D28D9)
                } else {
                    scheme.surface.copy(alpha = 0.72f)
                }
            )
            .background(LiquidGlassFillBrush)
            .border(
                width = 1.dp,
                brush = LiquidGlassSpecularBorderBrush,
                shape = shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOpenMenu
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Left: Circular Avatar (54.dp) with glowing Cyan-to-Violet neon ring
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(2.dp, NeonAvatarRingBrush, CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                NeonAvatar(
                    avatarUrl = avatarUrl,
                    label = displayTitle,
                    sizeDp = 50.dp,
                    cornerRadiusDp = 27.dp,
                    isSaved = chat.isSavedMessages,
                    isAi = chat.isAiChat,
                    isSystem = chat.isSystemChat
                )
            }

            if (peerOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF00F0FF))
                        .border(2.dp, Color(0xFF0F1222), CircleShape)
                )
            }
        }

        // Center: Chat Title (17.sp, Bold, #F8FAFF) + Message Preview (14.sp, #B4BEE0, 1-2 lines, with sparkle/media icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (chat.isSecure) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure Chat",
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = displayTitle,
                    color = Color(0xFFF8FAFF),
                    fontSize = (17f * scheme.fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (chat.isAiChat) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = previewText,
                    color = Color(0xFFB4BEE0),
                    fontSize = (14f * scheme.fontScale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }

        // Right: Timestamp (12.sp, #9AA6D1) + Glowing Cyan Circle Badge (24.dp, Color(0xFF00F0FF), bold dark text) or glowing violet indicator dot / pin icon
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (timestampText.isNotBlank()) {
                    Text(
                        text = timestampText,
                        color = Color(0xFF9AA6D1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenMenu),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Chat options",
                        tint = Color(0xFF9AA6D1),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (chat.isMuted) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Muted",
                        tint = Color(0xFF9AA6D1),
                        modifier = Modifier.size(15.dp)
                    )
                }

                when {
                    hasUnread -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F0FF))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "1",
                                color = Color(0xFF06070C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    chat.pinned -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA855F7).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    chat.isAiChat || peerOnline -> {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA855F7))
                                .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val fg = if (danger) scheme.danger else Color(0xFFF8FAFF)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (danger) scheme.danger else Color(0xFF00F0FF),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet ChatRoomScreen (`template_3_ios_liquid_aurora`):
 * - Floating Frosted Glass Header (`RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)`, translucent glass fill + bottom specular border):
 *   Circular glass back button (`38.dp`), `NeonAvatar` (`42.dp` circle with glowing ring), Title + online/AI subtitle, and circular glass Call / Video / Menu buttons.
 * - Liquid Glass Message Bubbles:
 *   Outgoing (`isMe`): `RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)`, filled with translucent Aurora Violet-Cyan gradient and `1.dp` specular border (`Color.White.copy(alpha = 0.35f)`).
 *   Incoming (`!isMe`): `RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)`, filled with frosted glass (`Color.White.copy(alpha = 0.11f)`) and `1.dp` glass border (`Color.White.copy(alpha = 0.22f)`).
 * - Floating Liquid Glass Capsule Composer at bottom (`padding = 12.dp`, `RoundedCornerShape(28.dp)`, translucent glass fill + `1.dp` specular border, emoji toggle, attach sheet, and glowing cyan/violet circular send button).
 */
@Composable
fun ChatRoomScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language.ifBlank { "ru" }
    val currentUserId = state.session.userId
    val serverUrl = state.session.serverUrl
    val clipboardManager = LocalClipboardManager.current

    val chat = state.activeChat ?: return
    val listState = rememberLazyListState()

    var composerText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<MessageDto?>(null) }
    var aiMode by remember { mutableStateOf("Fast • Neon-4o") }

    // Sync composer text when user selects a message to edit
    LaunchedEffect(state.editingMessage?.id) {
        val editing = state.editingMessage
        if (editing != null) {
            composerText = editing.cleanContent
        }
    }

    // Auto-scroll to bottom when messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    val displayTitle = chat.resolveDisplayTitle(
        currentUserId = currentUserId,
        savedLabel = tr(lang, "Избранное", "Тандалмалар", "Saved Messages")
    )
    val peerUser = chat.resolvePeerUser(currentUserId)
    val avatarUrl = chat.resolveAvatarUrl(currentUserId, serverUrl)

    val subtitleText = remember(chat, peerUser, lang) {
        when {
            chat.isAiChat -> "Neongram AI"
            chat.isSavedMessages -> tr(lang, "Личное облачное хранилище", "Жеке булут сактагыч", "Personal cloud storage")
            chat.isSystemChat -> tr(lang, "Официальные уведомления", "Расмий билдирүүлөр", "Official notifications")
            chat.type == "GROUP" -> "${tr(lang, "Участников", "Катышуучулар", "Members")}: ${chat.members.size.coerceAtLeast(1)}"
            peerUser?.isOnline == true -> tr(lang, "в сети", "онлайн", "online")
            else -> tr(lang, "был недавно", "жакында болгон", "last seen recently")
        }
    }

    val headerShape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.bg)
    ) {
        AnimatedNeonBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Floating Frosted Glass Header (`RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(scheme.surface.copy(alpha = 0.76f))
                    .background(LiquidGlassFillBrush)
                    .border(
                        width = 1.dp,
                        brush = LiquidGlassSpecularBorderBrush,
                        shape = headerShape
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular glass back button (38.dp)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                        .clickable { viewModel.navigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color(0xFFF8FAFF),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Tappable Profile area: NeonAvatar (42.dp circle with glowing ring) + Title + online/AI subtitle
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            when {
                                chat.type == "GROUP" -> viewModel.navigateToOverlay(ActiveOverlayScreen.GROUP_INFO)
                                chat.type == "DIRECT" && peerUser != null && !chat.isSavedMessages && !chat.isAiChat && !chat.isSystemChat -> {
                                    viewModel.openPeerProfile(peerUser)
                                }
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(1.8.dp, NeonAvatarRingBrush, CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NeonAvatar(
                            avatarUrl = avatarUrl,
                            label = displayTitle,
                            sizeDp = 38.dp,
                            cornerRadiusDp = 19.dp,
                            isSaved = chat.isSavedMessages,
                            isAi = chat.isAiChat,
                            isSystem = chat.isSystemChat
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayTitle,
                                color = Color(0xFFF8FAFF),
                                fontSize = (17f * scheme.fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chat.isAiChat) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF00F0FF).copy(alpha = 0.16f))
                                        .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = aiMode,
                                        color = Color(0xFF00F0FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = subtitleText,
                            color = if (peerUser?.isOnline == true || chat.isAiChat) Color(0xFF00F0FF) else Color(0xFF9AA6D1),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right circular glass Call / Video / Menu buttons
                if (chat.type == "DIRECT" && !chat.isSavedMessages && !chat.isAiChat && !chat.isSystemChat) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                            .clickable {
                                viewModel.showToast(tr(lang, "Аудиозвонок...", "Аудио чалуу...", "Starting audio call..."))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Audio Call",
                            tint = Color(0xFFF8FAFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                            .clickable {
                                viewModel.showToast(tr(lang, "Видеозвонок...", "Видео чалуу...", "Starting video call..."))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = Color(0xFFF8FAFF),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else if (chat.type == "GROUP") {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                            .clickable {
                                viewModel.showToast(tr(lang, "Групповой звонок...", "Тайпалык чалуу...", "Group audio call..."))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Group Call",
                            tint = Color(0xFFF8FAFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Circular glass Menu button
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                            .clickable { showHeaderMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More options",
                            tint = Color(0xFFF8FAFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        modifier = Modifier
                            .background(scheme.surface.copy(alpha = 0.95f))
                            .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(14.dp))
                    ) {
                        when {
                            chat.type == "GROUP" -> {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Управление группой", "Тайпаны башкаруу", "Manage group"),
                                            color = Color(0xFFF8FAFF)
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.navigateToOverlay(ActiveOverlayScreen.GROUP_INFO)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Очистить историю группы", "Тайпа тарыхын тазалоо", "Clear group history"),
                                            color = Color(0xFFF8FAFF)
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.clearChatHistory(chat.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Покинуть группу", "Тайпадан чыгуу", "Leave group"),
                                            color = scheme.danger
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.deleteChat(chat.id)
                                    }
                                )
                            }
                            chat.isAiChat || chat.isSavedMessages || chat.isSystemChat -> {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Очистить историю", "Тарыхты тазалоо", "Clear history"),
                                            color = Color(0xFFF8FAFF)
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.clearChatHistory(chat.id)
                                    }
                                )
                            }
                            else -> {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Профиль контакта", "Байланыш профили", "Contact profile"),
                                            color = Color(0xFFF8FAFF)
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        peerUser?.let { viewModel.openPeerProfile(it) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Очистить историю", "Тарыхты тазалоо", "Clear history"),
                                            color = Color(0xFFF8FAFF)
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.clearChatHistory(chat.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Удалить чат", "Чатты өчүрүү", "Delete chat"),
                                            color = scheme.danger
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        viewModel.deleteChat(chat.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Pinned Messages Bar (Frosted Glass Pill below header when `state.pinnedMessages.isNotEmpty()`)
            if (state.pinnedMessages.isNotEmpty()) {
                val topPinned = state.pinnedMessages.last()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surface.copy(alpha = 0.7f))
                        .background(LiquidGlassFillBrush)
                        .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF00F0FF))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tr(lang, "Закрепленное сообщение", "Кадалган билдирүү", "Pinned message"),
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = formatLastMessagePreview(topPinned, lang),
                            color = Color(0xFFF8FAFF),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { viewModel.togglePinMessage(topPinned.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unpin message",
                            tint = Color(0xFFB4BEE0),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // 3. Messages Area (`LazyColumn`, auto-scrolls to bottom)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.isLoading && state.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00F0FF),
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                } else if (state.messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(
                                lang,
                                "Сообщений пока нет. Напишите первым!",
                                "Азырынча билдирүүлөр жок. Биринчи болуп жазыңыз!",
                                "No messages yet. Say hello!"
                            ),
                            color = Color(0xFFB4BEE0),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id.ifBlank { it.createdAt + it.content.orEmpty() } }
                        ) { msg ->
                            val isMe = msg.senderId == currentUserId || (chat.isSavedMessages && msg.senderId != null)
                            MessageBubbleItem(
                                msg = msg,
                                isMe = isMe,
                                isGroupChat = chat.type == "GROUP",
                                serverUrl = serverUrl,
                                lang = lang,
                                onClickMessage = { selectedMessageForActions = msg },
                                onToggleReaction = { emoji -> viewModel.toggleReaction(msg.id, emoji) }
                            )
                        }
                    }
                }
            }

            // 4. Floating Liquid Glass Capsule Composer at bottom (`padding = 12.dp`, `RoundedCornerShape(28.dp)`)
            if (!chat.isSystemChat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reply / Edit Preview Bar above input capsule
                    val activeReply = state.replyToMessage
                    val activeEdit = state.editingMessage
                    if (activeReply != null || activeEdit != null) {
                        val isEditing = activeEdit != null
                        val targetMsg = activeEdit ?: activeReply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(scheme.surface.copy(alpha = 0.78f))
                                .background(LiquidGlassFillBrush)
                                .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF00F0FF))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isEditing) {
                                        tr(lang, "Редактировать", "Түзөтүү", "Edit message")
                                    } else {
                                        val author = targetMsg?.sender?.effectiveName
                                            ?: tr(lang, "Сообщение", "Билдирүү", "Message")
                                        "${tr(lang, "Ответить", "Жооп берүү", "Reply")}: $author"
                                    },
                                    color = Color(0xFF00F0FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatLastMessagePreview(targetMsg, lang),
                                    color = Color(0xFFB4BEE0),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        if (isEditing) {
                                            viewModel.setEditTarget(null)
                                            composerText = ""
                                        } else {
                                            viewModel.setReplyTarget(null)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel reply or edit",
                                    tint = Color(0xFFB4BEE0),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Inline Quick Emoji Bar when emoji toggle is open
                    if (showEmojiBar) {
                        val quickEmojis = listOf("😀", "😂", "🥳", "❤️", "🔥", "👍", "🙏", "🎯")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(scheme.surface.copy(alpha = 0.78f))
                                .background(LiquidGlassFillBrush)
                                .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(22.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickEmojis.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { composerText += emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    // Floating Liquid Glass Capsule Composer (`RoundedCornerShape(28.dp)`)
                    val capsuleShape = RoundedCornerShape(28.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(capsuleShape)
                            .background(scheme.surface.copy(alpha = 0.76f))
                            .background(LiquidGlassFillBrush)
                            .border(
                                width = 1.dp,
                                brush = LiquidGlassSpecularBorderBrush,
                                shape = capsuleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left Emoji button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showEmojiBar) Color.White.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .clickable { showEmojiBar = !showEmojiBar },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = "Emoji",
                                tint = if (showEmojiBar) Color(0xFF00F0FF) else Color(0xFFB4BEE0),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Multiline TextField
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (composerText.isEmpty()) {
                                Text(
                                    text = tr(lang, "Сообщение...", "Билдирүү...", "Message..."),
                                    color = Color(0xFF9AA6D1),
                                    fontSize = (16f * scheme.fontScale).sp
                                )
                            }
                            BasicTextField(
                                value = composerText,
                                onValueChange = { composerText = it },
                                singleLine = false,
                                maxLines = 5,
                                textStyle = TextStyle(
                                    color = Color(0xFFF8FAFF),
                                    fontSize = (16f * scheme.fontScale).sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF00F0FF)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right Attach button (`34.dp` frosted glass circle)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                .clickable { showAttachSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (chat.isAiChat) Icons.Default.AutoAwesome else Icons.Default.AttachFile,
                                contentDescription = "Attach",
                                tint = if (chat.isAiChat) Color(0xFF00F0FF) else Color(0xFFB4BEE0),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Glowing Cyan/Violet Circular Send / Mic Button (`40.dp` circle)
                        val hasText = composerText.trim().isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF00F0FF),
                                            Color(0xFF7C3AED)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                .clickable {
                                    if (hasText) {
                                        val payload = composerText.trim()
                                        composerText = ""
                                        showEmojiBar = false
                                        viewModel.sendMessage(payload)
                                    } else {
                                        viewModel.showToast(
                                            tr(
                                                lang,
                                                "Запись голосового сообщения...",
                                                "Үн билдирүү жаздырылууда...",
                                                "Recording voice message..."
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = if (hasText) "Send" else "Voice message",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Action Sheet / Dialog (`👍 ❤️ 🔥 😂 😮 🙏 🎯 💯`, Reply, Edit, Copy, Pin, Delete)
    selectedMessageForActions?.let { targetMsg ->
        val isMe = targetMsg.senderId == currentUserId
        val isPinned = state.pinnedMessages.any { it.id == targetMsg.id }
        val reactionOptions = listOf("👍", "❤️", "🔥", "😂", "😮", "🙏", "🎯", "💯")

        Dialog(
            onDismissRequest = { selectedMessageForActions = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable { selectedMessageForActions = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(scheme.surface.copy(alpha = 0.92f))
                        .background(LiquidGlassFillBrush)
                        .border(
                            width = 1.dp,
                            brush = LiquidGlassSpecularBorderBrush,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .clickable(enabled = false) {}
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .align(Alignment.CenterHorizontally)
                    )

                    // Reactions row: ['👍', '❤️', '🔥', '😂', '😮', '🙏', '🎯', '💯']
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reactionOptions.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val id = targetMsg.id
                                        selectedMessageForActions = null
                                        viewModel.toggleReaction(id, emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }

                    ContextActionRow(
                        icon = Icons.Default.Reply,
                        label = tr(lang, "Ответить", "Жооп берүү", "Reply"),
                        onClick = {
                            val msg = targetMsg
                            selectedMessageForActions = null
                            viewModel.setReplyTarget(msg)
                        }
                    )

                    if (isMe) {
                        ContextActionRow(
                            icon = Icons.Default.Edit,
                            label = tr(lang, "Редактировать", "Түзөтүү", "Edit"),
                            onClick = {
                                val msg = targetMsg
                                selectedMessageForActions = null
                                viewModel.setEditTarget(msg)
                            }
                        )
                    }

                    ContextActionRow(
                        icon = Icons.Default.ContentCopy,
                        label = tr(lang, "Копировать", "Көчүрүү", "Copy"),
                        onClick = {
                            val textToCopy = targetMsg.cleanContent
                            selectedMessageForActions = null
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            viewModel.showToast(tr(lang, "Скопировано", "Көчүрүлдү", "Copied"))
                        }
                    )

                    ContextActionRow(
                        icon = Icons.Default.PushPin,
                        label = if (isPinned) {
                            tr(lang, "Открепить сообщение", "Билдирүүнү бошотуу", "Unpin message")
                        } else {
                            tr(lang, "Закрепить сообщение", "Билдирүүнү кадоо", "Pin message")
                        },
                        onClick = {
                            val id = targetMsg.id
                            selectedMessageForActions = null
                            viewModel.togglePinMessage(id)
                        }
                    )

                    ContextActionRow(
                        icon = Icons.Default.Delete,
                        label = tr(lang, "Удалить", "Өчүрүү", "Delete"),
                        danger = true,
                        onClick = {
                            val id = targetMsg.id
                            selectedMessageForActions = null
                            viewModel.deleteMessage(id)
                        }
                    )
                }
            }
        }
    }

    // Attach Frosted Glass Bottom Sheet Modal
    if (showAttachSheet) {
        Dialog(
            onDismissRequest = { showAttachSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable { showAttachSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(scheme.surface.copy(alpha = 0.92f))
                        .background(LiquidGlassFillBrush)
                        .border(
                            width = 1.dp,
                            brush = LiquidGlassSpecularBorderBrush,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .clickable(enabled = false) {}
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .align(Alignment.CenterHorizontally)
                    )

                    if (chat.isAiChat) {
                        Text(
                            text = tr(lang, "Режим Neongram AI", "Neongram AI режими", "Neongram AI Mode"),
                            color = Color(0xFFF8FAFF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val aiModes = listOf(
                            "Fast • Neon-4o" to tr(lang, "Быстрые ответы на каждый день", "Күнүмдүк тез жооптор", "Fast everyday responses"),
                            "Deep • Neon-R1" to tr(lang, "Глубокое рассуждение и код", "Терең ой жүгүртүү жана код", "Deep reasoning & coding"),
                            "Creative • Neon-Vision" to tr(lang, "Креатив и генерация идей", "Креатив жана идеялар", "Creative writing & ideas")
                        )
                        aiModes.forEach { (modeTitle, modeSubtitle) ->
                            val selected = aiMode == modeTitle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (selected) Color(0xFF00F0FF).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        aiMode = modeTitle
                                        showAttachSheet = false
                                        viewModel.showToast("AI: $modeTitle")
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = modeTitle,
                                        color = Color(0xFFF8FAFF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = modeSubtitle,
                                        color = Color(0xFFB4BEE0),
                                        fontSize = 12.sp
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00F0FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = tr(lang, "Прикрепить", "Тиркөө", "Attach"),
                            color = Color(0xFFF8FAFF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val attachOptions = listOf(
                            Triple(Icons.Default.CameraAlt, tr(lang, "Снять фото", "Сүрөт тартуу", "Take photo"), "📷 [Фото с камеры]"),
                            Triple(Icons.Default.Videocam, tr(lang, "Снять видео", "Видео тартуу", "Record video"), "🎬 [Видео с камеры]"),
                            Triple(Icons.Default.Image, tr(lang, "Фото", "Сүрөт", "Photo"), "🖼 [Фото из галереи]"),
                            Triple(Icons.Default.Movie, tr(lang, "Видео", "Видео", "Video"), "🎥 [Видео из галереи]"),
                            Triple(Icons.Default.MusicNote, tr(lang, "Аудио", "Аудио", "Audio"), "🎵 [Аудиофайл]"),
                            Triple(Icons.Default.Description, tr(lang, "Файл", "Файл", "File"), "📎 [Документ]")
                        )

                        attachOptions.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { (icon, label, sampleTag) ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                                            .clickable {
                                                showAttachSheet = false
                                                viewModel.sendMessage(sampleTag)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF00F0FF).copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = Color(0xFF00F0FF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = label,
                                            color = Color(0xFFF8FAFF),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Liquid Glass Message Bubbles (`template_3_ios_liquid_aurora`):
 * - Outgoing (`isMe`): `RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)`,
 *   filled with translucent Aurora Violet-Cyan gradient (`Brush.horizontalGradient(listOf(Color(0xCC6D28D9), Color(0xB30891B2)))`)
 *   and `1.dp` specular border (`Color.White.copy(alpha = 0.35f)`).
 * - Incoming (`!isMe`): `RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)`,
 *   filled with frosted glass (`Color.White.copy(alpha = 0.11f)`) and `1.dp` glass border (`Color.White.copy(alpha = 0.22f)`).
 */
@Composable
private fun MessageBubbleItem(
    msg: MessageDto,
    isMe: Boolean,
    isGroupChat: Boolean,
    serverUrl: String,
    lang: String,
    onClickMessage: () -> Unit,
    onToggleReaction: (String) -> Unit
) {
    val scheme = LocalNeonScheme.current

    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 22.dp,
            topEnd = 22.dp,
            bottomStart = 22.dp,
            bottomEnd = 6.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 22.dp,
            topEnd = 22.dp,
            bottomStart = 6.dp,
            bottomEnd = 22.dp
        )
    }

    val bubbleBorderColor = if (isMe) {
        Color.White.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.22f)
    }

    val timeText = formatIsoTimeToHhMm(msg.createdAt)

    val bubbleModifier = if (isMe) {
        Modifier
            .widthIn(min = 96.dp, max = 315.dp)
            .clip(bubbleShape)
            .background(OutgoingAuroraBubbleBrush)
            .border(1.dp, bubbleBorderColor, bubbleShape)
            .clickable(onClick = onClickMessage)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    } else {
        Modifier
            .widthIn(min = 96.dp, max = 315.dp)
            .clip(bubbleShape)
            .background(scheme.surface.copy(alpha = 0.55f))
            .background(Color.White.copy(alpha = 0.11f))
            .border(1.dp, bubbleBorderColor, bubbleShape)
            .clickable(onClick = onClickMessage)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = bubbleModifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Group author name if `isGroupChat && !isMe`
            if (isGroupChat && !isMe) {
                val authorName = msg.sender?.effectiveName?.takeIf { it.isNotBlank() } ?: "Neongram User"
                Text(
                    text = authorName,
                    color = Color(0xFF00F0FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Reply Quote Block
            msg.replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.26f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF00F0FF))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = reply.sender?.effectiveName ?: tr(lang, "Сообщение", "Билдирүү", "Message"),
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = reply.content?.removePrefix("[FWD]")?.trim()?.ifBlank { reply.type }
                                ?: reply.type,
                            color = Color(0xFFD6E0FF),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Forwarded Label
            if (msg.isForwarded) {
                Text(
                    text = tr(lang, "ПЕРЕСЛАНО", "ЖӨНӨТҮЛГӨН", "FORWARDED"),
                    color = Color(0xFFD6E0FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Content by Type
            val upperType = msg.type.uppercase()
            when {
                msg.systemInboxEntry != null || upperType == "SYSTEM" -> {
                    val sysEntry = msg.systemInboxEntry
                    val sysTitle = sysEntry?.title?.takeIf { it.isNotBlank() }
                        ?: tr(lang, "Системное уведомление", "Тутумдук билдирүү", "System Security")
                    val sysBody = sysEntry?.body?.takeIf { it.isNotBlank() }
                        ?: msg.cleanContent.ifBlank { tr(lang, "Событие безопасности аккаунта", "Коопсуздук окуясы", "Account security event") }
                    val isAlert = sysEntry?.type?.contains("ALERT", ignoreCase = true) == true

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isAlert) Icons.Default.Bolt else Icons.Default.Shield,
                                contentDescription = "System",
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = sysTitle,
                                color = Color(0xFFF8FAFF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = sysBody,
                            color = Color(0xFFF8FAFF),
                            fontSize = (14f * scheme.fontScale).sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                upperType == "PHOTO" || upperType == "IMAGE" -> {
                    val photoUrl = resolveFullMediaUrl(msg.fileUrl, serverUrl)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Photo",
                                tint = Color(0xFFB4BEE0),
                                modifier = Modifier.size(36.dp)
                            )
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = msg.cleanContent.ifBlank { "Photo" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (msg.cleanContent.isNotBlank()) {
                            Text(
                                text = msg.cleanContent,
                                color = Color(0xFFF8FAFF),
                                fontSize = (15f * scheme.fontScale).sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                upperType == "VOICE" || upperType == "AUDIO" -> {
                    AudioMessagePlayerRow()
                }

                upperType == "FILE" || upperType == "DOCUMENT" -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF00F0FF).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "File",
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = msg.fileName?.takeIf { it.isNotBlank() }
                                    ?: tr(lang, "Файл", "Файл", "File"),
                                color = Color(0xFFF8FAFF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (msg.cleanContent.isNotBlank()) {
                                Text(
                                    text = msg.cleanContent,
                                    color = Color(0xFFD6E0FF),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F0FF).copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                else -> {
                    FormattedMessageText(
                        text = msg.cleanContent,
                        fontScale = scheme.fontScale
                    )
                }
            }

            // Reactions Row: grouped emoji pills with counts
            if (msg.reactions.isNotEmpty()) {
                val grouped = remember(msg.reactions) {
                    msg.reactions.groupBy { it.emoji }.mapValues { it.value.size }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    grouped.forEach { (emoji, count) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.30f))
                                .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable { onToggleReaction(emoji) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 12.sp
                            )
                            Text(
                                text = count.toString(),
                                color = Color(0xFFF8FAFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Meta Footer: "изменено" + HH:mm + DoneAll checkmarks
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!msg.editedAt.isNullOrBlank()) {
                    Text(
                        text = tr(lang, "изменено", "өзгөртүлдү", "edited"),
                        color = Color(0xFFD6E0FF).copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        color = Color(0xFFD6E0FF).copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
                if (isMe) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Read",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * Voice waveform player row inside Liquid Glass bubble
 */
@Composable
private fun AudioMessagePlayerRow() {
    var isPlaying by remember { mutableStateOf(false) }
    val barHeights = remember {
        listOf(
            8, 14, 18, 11, 20, 15, 9, 17, 22, 13, 10, 19,
            16, 21, 12, 15, 18, 10, 14, 19, 11, 16, 12, 9
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF00F0FF).copy(alpha = 0.22f))
                .border(1.dp, Color(0xFF00F0FF), CircleShape)
                .clickable { isPlaying = !isPlaying },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play voice message",
                tint = Color(0xFFF8FAFF),
                modifier = Modifier.size(18.dp)
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            barHeights.forEachIndexed { idx, h ->
                val activeBar = isPlaying && idx < 12
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (activeBar) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.55f)
                        )
                )
            }
        }

        Text(
            text = "00:12",
            color = Color(0xFFD6E0FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Message text with code block formatting when triple backticks are present
 */
@Composable
private fun FormattedMessageText(
    text: String,
    fontScale: Float
) {
    if (!text.contains("```")) {
        Text(
            text = text,
            color = Color(0xFFF8FAFF),
            fontSize = (16f * fontScale).sp,
            lineHeight = (21f * fontScale).sp
        )
        return
    }

    val segments = remember(text) { text.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEachIndexed { index, segment ->
            val cleanSeg = segment.trim('\n', '\r')
            if (cleanSeg.isNotBlank()) {
                if (index % 2 == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF080A14).copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = cleanSeg,
                            color = Color(0xFF00F0FF),
                            fontSize = (13.5f * fontScale).sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (18f * fontScale).sp
                        )
                    }
                } else {
                    Text(
                        text = cleanSeg,
                        color = Color(0xFFF8FAFF),
                        fontSize = (16f * fontScale).sp,
                        lineHeight = (21f * fontScale).sp
                    )
                }
            }
        }
    }
}

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet GroupInfoScreen:
 * - Styled with Frosted Liquid Glass Hero Card (`RoundedCornerShape(28.dp)`) and Liquid Glass member cards.
 */
@Composable
fun GroupInfoScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language.ifBlank { "ru" }
    val currentUserId = state.session.userId
    val serverUrl = state.session.serverUrl
    val clipboardManager = LocalClipboardManager.current

    val chat = state.activeChat
    if (chat == null) {
        PageScaffold(
            title = tr(lang, "О группе", "Тайпа тууралуу", "Group Info"),
            onBack = { viewModel.navigateBack() }
        ) {
            EmptyHintCard(
                text = tr(lang, "Группа не выбрана", "Тайпа тандалган жок", "No group selected")
            )
        }
        return
    }

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var addUsernameInput by remember { mutableStateOf("") }

    val groupTitle = chat.resolveDisplayTitle(currentUserId)
    val bannerUrl = chat.resolveAvatarUrl(currentUserId, serverUrl)
    val isPrivate = chat.isPrivateGroup

    PageScaffold(
        title = groupTitle,
        onBack = { viewModel.navigateBack() }
    ) {
        // Frosted Liquid Glass Hero Card (`RoundedCornerShape(28.dp)`)
        val heroShape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(scheme.surface.copy(alpha = 0.72f))
                .background(LiquidGlassFillBrush)
                .border(1.dp, LiquidGlassSpecularBorderBrush, heroShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6D28D9).copy(alpha = 0.75f),
                                Color(0xFF00F0FF).copy(alpha = 0.55f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = groupTitle.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black
                )
                if (!bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = groupTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = groupTitle,
                    color = Color(0xFFF8FAFF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${tr(lang, "Участников", "Катышуучулар", "Members")}: ${chat.members.size}",
                        color = Color(0xFFB4BEE0),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "•",
                        color = Color(0xFF9AA6D1),
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isPrivate) {
                            tr(lang, "Приватная группа", "Жеке тайпа", "Private group")
                        } else {
                            tr(lang, "Публичная группа", "Ачык тайпа", "Public group")
                        },
                        color = Color(0xFF00F0FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick Actions Row (`"Ссылка"`, `"Добавить участника"`, `"Покинуть группу"`)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GroupQuickActionButton(
                icon = Icons.Default.Link,
                label = tr(lang, "Ссылка", "Шилтеме", "Invite Link"),
                modifier = Modifier.weight(1f),
                onClick = {
                    val inviteLink = "https://cloud.neongram.space/join/${chat.inviteToken ?: chat.id}"
                    clipboardManager.setText(AnnotatedString(inviteLink))
                    viewModel.showToast(tr(lang, "Ссылка скопирована", "Шилтеме көчүрүлдү", "Invite link copied"))
                }
            )

            GroupQuickActionButton(
                icon = Icons.Default.PersonAdd,
                label = tr(lang, "Добавить участника", "Кошуу", "Add member"),
                modifier = Modifier.weight(1f),
                onClick = { showAddMemberDialog = true }
            )

            GroupQuickActionButton(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = tr(lang, "Покинуть группу", "Чыгуу", "Leave group"),
                danger = true,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.deleteChat(chat.id) }
            )
        }

        // Members Section
        SectionUppercaseHeader(
            title = tr(lang, "Участники", "Катышуучулар", "Members")
        )

        if (chat.members.isEmpty()) {
            EmptyHintCard(
                text = tr(lang, "Список участников пуст", "Катышуучулар тизмеси бош", "No members listed")
            )
        } else {
            chat.members.forEach { member ->
                GroupMemberGlassCard(
                    member = member,
                    serverUrl = serverUrl,
                    lang = lang,
                    onClick = {
                        member.user?.let { user ->
                            viewModel.openPeerProfile(user)
                        }
                    }
                )
            }
        }
    }

    if (showAddMemberDialog) {
        Dialog(onDismissRequest = { showAddMemberDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surface.copy(alpha = 0.94f))
                    .background(LiquidGlassFillBrush)
                    .border(1.dp, LiquidGlassSpecularBorderBrush, RoundedCornerShape(26.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr(lang, "Добавить участника", "Катышуучу кошуу", "Add member"),
                    color = Color(0xFFF8FAFF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                NeonInput(
                    value = addUsernameInput,
                    onValueChange = { addUsernameInput = it },
                    placeholder = tr(lang, "Логин (@username)", "Логин (@username)", "Username (@username)")
                )

                PrimaryButton(
                    label = tr(lang, "Пригласить", "Чакыруу", "Invite"),
                    onClick = {
                        val clean = addUsernameInput.trim().removePrefix("@")
                        showAddMemberDialog = false
                        addUsernameInput = ""
                        if (clean.isNotBlank()) {
                            viewModel.showToast(
                                tr(
                                    lang,
                                    "Приглашение отправлено @$clean",
                                    "@$clean колдонуучусуна чакыруу жөнөтүлдү",
                                    "Invitation sent to @$clean"
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupQuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(22.dp)
    val fg = if (danger) scheme.danger else Color(0xFFF8FAFF)

    Column(
        modifier = modifier
            .clip(shape)
            .background(scheme.surface.copy(alpha = 0.72f))
            .background(LiquidGlassFillBrush)
            .border(1.dp, LiquidGlassSpecularBorderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (danger) scheme.danger else Color(0xFF00F0FF),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupMemberGlassCard(
    member: ChatMemberDto,
    serverUrl: String,
    lang: String,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val user = member.user
    val title = user?.effectiveName ?: member.userId.ifBlank { "Neongram User" }
    val subtitle = if (!user?.username.isNullOrBlank()) "@${user?.username}" else member.status
    val avatarUrl = resolveFullMediaUrl(user?.avatarUrl, serverUrl)

    val (roleLabel, roleColor) = when (member.role.uppercase()) {
        "OWNER", "CREATOR" -> tr(lang, "Владелец", "Ээси", "Owner") to Color(0xFF00F0FF)
        "ADMIN", "ADMINISTRATOR" -> tr(lang, "Админ", "Админ", "Admin") to Color(0xFFA855F7)
        else -> tr(lang, "Участник", "Катышуучу", "Member") to Color(0xFF9AA6D1)
    }

    val cardShape = RoundedCornerShape(24.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(scheme.surface.copy(alpha = 0.72f))
            .background(LiquidGlassFillBrush)
            .border(1.dp, LiquidGlassSpecularBorderBrush, cardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(1.6.dp, NeonAvatarRingBrush, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            NeonAvatar(
                avatarUrl = avatarUrl,
                label = title,
                sizeDp = 42.dp,
                cornerRadiusDp = 21.dp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFFF8FAFF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFFB4BEE0),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(roleColor.copy(alpha = 0.16f))
                .border(1.dp, roleColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = roleLabel,
                color = roleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
