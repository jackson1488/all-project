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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.HorizontalDivider
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
import space.neongram.app.core.ui.CyberBrandTitle
import space.neongram.app.core.ui.ListRow
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeonAvatar
import space.neongram.app.core.ui.NeonInput
import space.neongram.app.core.ui.PageScaffold
import space.neongram.app.core.ui.PrimaryButton
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.ActiveOverlayScreen
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel

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
        "VOICE", "AUDIO" -> tr(lang, "Голосовое", "Үн билдирүү", "Voice message")
        "FILE", "DOCUMENT" -> msg.fileName?.takeIf { it.isNotBlank() } ?: tr(lang, "Файл", "Файл", "File")
        else -> msg.cleanContent.ifBlank { tr(lang, "Сообщение", "Билдирүү", "Message") }
    }
}

/**
 * 1:1 with `ChatsScreen.jsx` mobile layout:
 * - Header (`paddingHorizontal = 20.dp, paddingTop = 8.dp, paddingBottom = 12.dp`):
 *   `CyberBrandTitle("Neongram")` + search bar (`minHeight = 46.dp, borderRadius = 16.dp`).
 * - LazyColumn of chats filtering out support chats (`!chat.isSupport`).
 * - Search mode (`>= 2` chars): triggers `viewModel.searchUsers(query)`, shows `"ЧАТЫ"` & `"ПОЛЬЗОВАТЕЛИ"`.
 * - Normal mode: shows `"ЗАКРЕПЛЕННЫЕ"` section if `pinnedChats.isNotEmpty()`, then `pinnedChats`, then `normalChats`.
 * - Long-press / 3-dots opens Chat Context Action Sheet + Delete Confirm Dialog with `"Удалить у всех"` checkbox.
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
            // Header (`paddingHorizontal = 20.dp, paddingTop = 8.dp, paddingBottom = 12.dp`)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CyberBrandTitle(text = "Neongram")

                    // AI quick pill button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surface)
                            .border(1.dp, scheme.border, RoundedCornerShape(12.dp))
                            .clickable { viewModel.openAiAssistantChat() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Neongram AI",
                            tint = scheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "AI",
                            color = scheme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Search bar (`minHeight = 46.dp, borderRadius = 16.dp`, `surface` bg, `border`, search icon, `"Поиск"` placeholder, clear button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = scheme.muted,
                        modifier = Modifier.size(18.dp)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = tr(lang, "Поиск", "Издөө", "Search"),
                                color = scheme.muted,
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
                                color = scheme.text,
                                fontSize = (15f * scheme.fontScale).sp
                            ),
                            cursorBrush = SolidColor(scheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable {
                                    searchQuery = ""
                                    viewModel.searchUsers("")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = scheme.muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Chats & Search Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            ChatRowItem(
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
                            val resolvedAvatar = resolveFullMediaUrl(user.avatarUrl, serverUrl)
                            ListRow(
                                title = user.effectiveName,
                                subtitle = "@${user.username}",
                                avatarUrl = resolvedAvatar,
                                avatarLabel = user.effectiveName,
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
                                ChatRowItem(
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
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }

                        items(
                            items = normalChats,
                            key = { "normal_chat_${it.id}" }
                        ) { chat ->
                            ChatRowItem(
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

    // Chat Context Action Sheet / Dialog
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
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { contextMenuChat = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.muted.copy(alpha = 0.45f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Text(
                        text = chatTitle,
                        color = scheme.text,
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

    // Delete Confirm Dialog with "Удалить у всех" checkbox
    deleteConfirmChat?.let { targetChat ->
        val chatTitle = targetChat.resolveDisplayTitle(
            currentUserId = currentUserId,
            savedLabel = tr(lang, "Избранное", "Тандалмалар", "Saved Messages")
        )
        Dialog(onDismissRequest = { deleteConfirmChat = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr(lang, "Удалить чат?", "Чатты өчүрөсүзбү?", "Delete chat?"),
                    color = scheme.text,
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
                    color = scheme.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceAlt)
                        .clickable { deleteForEveryone = !deleteForEveryone }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteForEveryone,
                        onCheckedChange = { deleteForEveryone = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = scheme.primary,
                            uncheckedColor = scheme.muted,
                            checkmarkColor = Color(0xFF06070C)
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr(lang, "Удалить у всех", "Баарынан өчүрүү", "Delete for everyone"),
                        color = scheme.text,
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceAlt)
                            .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                            .clickable { deleteConfirmChat = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Отмена", "Жокко чыгаруу", "Cancel"),
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(14.dp))
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
    val scheme = LocalNeonScheme.current
    Text(
        text = title.uppercase(),
        color = scheme.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyHintCard(text: String) {
    val scheme = LocalNeonScheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = scheme.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * 1:1 with `ChatRowItem` in `ChatsScreen.jsx`:
 * - Container: `borderRadius = 16.dp`, `paddingHorizontal = 11.dp, paddingVertical = 9.dp`,
 *   `surface` bg (or purple tint `Color(0x1AA855F7)` + border `Color(0x8CA855F7)` if `chat.isSecure`).
 * - Left: `NeonAvatar` (`50.dp`, cornerRadius `17.dp`, Saved/AI/System/Peer avatar).
 * - Middle/Right:
 *   Top line: lock icon if `isSecure` + Title (`17.sp`, Bold) + formatted timestamp (`HH:mm`, `11.sp`, muted).
 *   Bottom line: Last message preview (`13.sp`, PHOTO -> `"Фото"`, VIDEO -> `"Видео"`, VOICE -> `"Голосовое"`, FILE -> `"Файл"`, or text)
 *   + right indicators: pin icon if `chat.pinned`, mute icon if `chat.isMuted`, unread badge if applicable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRowItem(
    chat: ChatDto,
    currentUserId: String,
    serverUrl: String,
    lang: String,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(16.dp)

    val containerBg = if (chat.isSecure) Color(0x1AA855F7) else scheme.surface
    val containerBorder = if (chat.isSecure) Color(0x8CA855F7) else scheme.border

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
            .background(containerBg)
            .border(1.dp, containerBorder, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOpenMenu
            )
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box {
            NeonAvatar(
                avatarUrl = avatarUrl,
                label = displayTitle,
                sizeDp = 50.dp,
                cornerRadiusDp = 17.dp,
                isSaved = chat.isSavedMessages,
                isAi = chat.isAiChat,
                isSystem = chat.isSystemChat
            )
            if (peerOnline) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                        .border(2.dp, scheme.surface, CircleShape)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top line: lock icon if isSecure + Title (17.sp, Bold) + formatted timestamp (HH:mm, 11.sp, muted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (chat.isSecure) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Chat",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = displayTitle,
                        color = scheme.text,
                        fontSize = (17f * scheme.fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (timestampText.isNotBlank()) {
                        Text(
                            text = timestampText,
                            color = scheme.muted,
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenMenu),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Chat options",
                            tint = scheme.muted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Bottom line: Last message preview (13.sp) + right indicators (pin, mute, unread badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = previewText,
                    color = scheme.muted,
                    fontSize = (13f * scheme.fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chat.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = scheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.isMuted) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Muted",
                            tint = scheme.muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .widthIn(min = 18.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "1",
                                color = if (scheme.isDark) Color(0xFF06070C) else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
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
    val fg = if (danger) scheme.danger else scheme.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
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
 * 1:1 with `ChatConversation.jsx`, `ChatHeader.jsx`, `MessageBubble.jsx`, `Composer.jsx`, and `AttachSheet.jsx`.
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
            // 1. ChatHeader (`minHeight = 56.dp`, `surface` bg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(scheme.surface)
                    .border(width = 1.dp, color = scheme.border)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back button (`36.dp`, `chevron-back`) -> `viewModel.navigateBack()`
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.navigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = scheme.text,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Tappable Profile area: NeonAvatar (40.dp, cornerRadius 15.dp) + Title (17.sp, Bold) + Subtitle
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
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
                    NeonAvatar(
                        avatarUrl = avatarUrl,
                        label = displayTitle,
                        sizeDp = 40.dp,
                        cornerRadiusDp = 15.dp,
                        isSaved = chat.isSavedMessages,
                        isAi = chat.isAiChat,
                        isSystem = chat.isSystemChat
                    )

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
                                color = scheme.text,
                                fontSize = (17f * scheme.fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chat.isAiChat) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(scheme.primary.copy(alpha = 0.16f))
                                        .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = aiMode,
                                        color = scheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = subtitleText,
                            color = if (peerUser?.isOnline == true) scheme.primary else scheme.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right action icons: Audio Call, Video Call (for DIRECT chats), and 3-dots Menu
                if (chat.type == "DIRECT" && !chat.isSavedMessages && !chat.isAiChat && !chat.isSystemChat) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.showToast(tr(lang, "Аудиозвонок...", "Аудио чалуу...", "Starting audio call..."))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Audio Call",
                            tint = scheme.text,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.showToast(tr(lang, "Видеозвонок...", "Видео чалуу...", "Starting video call..."))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = scheme.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (chat.type == "GROUP") {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.showToast(tr(lang, "Групповой звонок...", "Тайпалык чалуу...", "Group audio call..."))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Group Call",
                            tint = scheme.text,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // 3-dots Menu button (`MoreHoriz`) opening an AnchoredMenu / DropdownMenu
                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { showHeaderMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More options",
                            tint = scheme.text,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        modifier = Modifier
                            .background(scheme.surface)
                            .border(1.dp, scheme.border, RoundedCornerShape(12.dp))
                    ) {
                        when {
                            chat.type == "GROUP" -> {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tr(lang, "Управление группой", "Тайпаны башкаруу", "Manage group"),
                                            color = scheme.text
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
                                            color = scheme.text
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
                                            color = scheme.text
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
                                            color = scheme.text
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
                                            color = scheme.text
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

            // 2. Pinned Messages Bar (shown below header when `state.pinnedMessages.isNotEmpty()`)
            if (state.pinnedMessages.isNotEmpty()) {
                val topPinned = state.pinnedMessages.last()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.primary)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tr(lang, "Закрепленное сообщение", "Кадалган билдирүү", "Pinned message"),
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = formatLastMessagePreview(topPinned, lang),
                            color = scheme.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.togglePinMessage(topPinned.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unpin message",
                            tint = scheme.muted,
                            modifier = Modifier.size(16.dp)
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
                            color = scheme.primary,
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
                            color = scheme.muted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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

            // 4. Composer Bar (hidden if `chat.isSystemChat`)
            if (!chat.isSystemChat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Reply / Edit Preview Bar above input pill
                    val activeReply = state.replyToMessage
                    val activeEdit = state.editingMessage
                    if (activeReply != null || activeEdit != null) {
                        val isEditing = activeEdit != null
                        val targetMsg = activeEdit ?: activeReply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.surface)
                                .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(scheme.primary)
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
                                    color = scheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatLastMessagePreview(targetMsg, lang),
                                    color = scheme.muted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
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
                                    tint = scheme.muted,
                                    modifier = Modifier.size(16.dp)
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
                                .clip(RoundedCornerShape(16.dp))
                                .background(scheme.surface)
                                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickEmojis.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { composerText += emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 19.sp
                                    )
                                }
                            }
                        }
                    }

                    // Main Input Pill (`borderRadius = 26.dp`, `minHeight = 54.dp`, `surface` bg)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(scheme.surface)
                            .border(1.dp, scheme.border, RoundedCornerShape(26.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left Emoji button (`34.dp`, rounded `10.dp`)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (showEmojiBar) scheme.surfaceAlt else Color.Transparent)
                                .clickable { showEmojiBar = !showEmojiBar },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = "Emoji",
                                tint = if (showEmojiBar) scheme.primary else scheme.muted,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        // Multiline TextField (`placeholder = "Сообщение"`)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (composerText.isEmpty()) {
                                Text(
                                    text = tr(lang, "Сообщение", "Билдирүү", "Message"),
                                    color = scheme.muted,
                                    fontSize = (16f * scheme.fontScale).sp
                                )
                            }
                            BasicTextField(
                                value = composerText,
                                onValueChange = { composerText = it },
                                singleLine = false,
                                maxLines = 5,
                                textStyle = TextStyle(
                                    color = scheme.text,
                                    fontSize = (16f * scheme.fontScale).sp
                                ),
                                cursorBrush = SolidColor(scheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right Attach button (`32.dp` circle, opens Attach Bottom Sheet Modal)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(scheme.surfaceAlt)
                                .clickable { showAttachSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (chat.isAiChat) Icons.Default.AutoAwesome else Icons.Default.AttachFile,
                                contentDescription = "Attach",
                                tint = if (chat.isAiChat) scheme.primary else scheme.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Primary Send / Mic button (`36.dp` circle, `scheme.primary` bg, `Send` icon when text non-empty, `Mic` icon when empty)
                        val hasText = composerText.trim().isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
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
                                tint = if (scheme.isDark) Color(0xFF06070C) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Action Sheet / Dialog
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
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { selectedMessageForActions = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.muted.copy(alpha = 0.45f))
                            .align(Alignment.CenterHorizontally)
                    )

                    // Reactions row: ['👍', '❤️', '🔥', '😂', '😮', '🙏', '🎯', '💯']
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surfaceAlt)
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

    // Attach Bottom Sheet Modal (`AttachSheet.jsx`: 6 exact options or AI model/mode picker if `chat.isAiChat`)
    if (showAttachSheet) {
        Dialog(
            onDismissRequest = { showAttachSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showAttachSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.muted.copy(alpha = 0.45f))
                            .align(Alignment.CenterHorizontally)
                    )

                    if (chat.isAiChat) {
                        Text(
                            text = tr(lang, "Режим Neongram AI", "Neongram AI режими", "Neongram AI Mode"),
                            color = scheme.text,
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) scheme.bg else scheme.surfaceAlt)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) scheme.primary else scheme.border,
                                        shape = RoundedCornerShape(14.dp)
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
                                        color = scheme.text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = modeSubtitle,
                                        color = scheme.muted,
                                        fontSize = 12.sp
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = scheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = tr(lang, "Прикрепить", "Тиркөө", "Attach"),
                            color = scheme.text,
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
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(scheme.surfaceAlt)
                                            .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
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
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(scheme.primary.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = scheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = label,
                                            color = scheme.text,
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
 * 1:1 with `MessageBubble.jsx`:
 * - Outgoing (`isMe`): aligned right, `scheme.bubbleMe` bg, corner radii `(topStart = 18.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)`.
 * - Incoming (`!isMe`): aligned left, `scheme.bubbleOther` bg, corner radii `(topStart = 8.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)`.
 * - Supports Group Author Name, Reply Quote Block, Forwarded Label, PHOTO / VOICE / FILE / SYSTEM / TEXT with code blocks,
 *   Reactions Row, and Meta Footer (`"изменено"` + `HH:mm` + `DoneAll` checkmarks).
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
            topStart = 18.dp,
            topEnd = 8.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        )
    }

    val bubbleBg = if (isMe) scheme.bubbleMe else scheme.bubbleOther
    val timeText = formatIsoTimeToHhMm(msg.createdAt)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 310.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(1.dp, scheme.border.copy(alpha = 0.7f), bubbleShape)
                .clickable(onClick = onClickMessage)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Group author name in `scheme.primary` (`12.sp`, Bold) if `chat.type == "GROUP" && !isMe`
            if (isGroupChat && !isMe) {
                val authorName = msg.sender?.effectiveName?.takeIf { it.isNotBlank() } ?: "Neongram User"
                Text(
                    text = authorName,
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Reply Quote Block (if `msg.replyTo != null`): rounded-10 box with 3.dp vertical primary bar + author name + reply snippet
            msg.replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.bg.copy(alpha = 0.38f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.primary)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = reply.sender?.effectiveName ?: tr(lang, "Сообщение", "Билдирүү", "Message"),
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = reply.content?.removePrefix("[FWD]")?.trim()?.ifBlank { reply.type }
                                ?: reply.type,
                            color = scheme.bubbleMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Forwarded Label (if `msg.isForwarded`): `"ПЕРЕСЛАНО"` (`11.sp`, uppercase, `bubbleMuted`)
            if (msg.isForwarded) {
                Text(
                    text = tr(lang, "ПЕРЕСЛАНО", "ЖӨНӨТҮЛГӨН", "FORWARDED"),
                    color = scheme.bubbleMuted,
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surface.copy(alpha = 0.7f))
                            .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
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
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = sysTitle,
                                color = scheme.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = sysBody,
                            color = scheme.bubbleText,
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Photo",
                                tint = scheme.muted,
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
                                color = scheme.bubbleText,
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.bg.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "File",
                                tint = scheme.primary,
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
                                color = scheme.bubbleText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (msg.cleanContent.isNotBlank()) {
                                Text(
                                    text = msg.cleanContent,
                                    color = scheme.bubbleMuted,
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
                                .background(scheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = scheme.primary,
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

            // Reactions Row (if `msg.reactions.isNotEmpty()`): grouped emoji pills with counts
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.bg.copy(alpha = 0.45f))
                                .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable { onToggleReaction(emoji) }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 12.sp
                            )
                            Text(
                                text = count.toString(),
                                color = scheme.bubbleText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Meta Footer: `"изменено"` (if `msg.editedAt != null`) + time `HH:mm` (`11.sp`, `bubbleMuted`) + `DoneAll` checkmarks (`12.dp`, `primary`) for `isMe`
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!msg.editedAt.isNullOrBlank()) {
                    Text(
                        text = tr(lang, "изменено", "өзгөртүлдү", "edited"),
                        color = scheme.bubbleMuted,
                        fontSize = 11.sp
                    )
                }
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        color = scheme.bubbleMuted,
                        fontSize = 11.sp
                    )
                }
                if (isMe) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Read",
                        tint = scheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * `VOICE`: `AudioMessagePlayer` row with `34.dp` Play button (`primary` tint), 24 vertical waveform bars, and duration `00:12`.
 */
@Composable
private fun AudioMessagePlayerRow() {
    val scheme = LocalNeonScheme.current
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
                .background(scheme.primary.copy(alpha = 0.18f))
                .border(1.dp, scheme.primary, CircleShape)
                .clickable { isPlaying = !isPlaying },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play voice message",
                tint = scheme.primary,
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
                            if (activeBar) scheme.primary else scheme.primary.copy(alpha = 0.45f)
                        )
                )
            }
        }

        Text(
            text = "00:12",
            color = scheme.bubbleMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * `TEXT`: message text (`16.sp * scheme.fontScale`) with code block formatting if triple backticks are present.
 */
@Composable
private fun FormattedMessageText(
    text: String,
    fontScale: Float
) {
    val scheme = LocalNeonScheme.current
    if (!text.contains("```")) {
        Text(
            text = text,
            color = scheme.bubbleText,
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
                    // Code block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF080A12))
                            .border(1.dp, scheme.border, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = cleanSeg,
                            color = scheme.primary,
                            fontSize = (13.5f * fontScale).sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (18f * fontScale).sp
                        )
                    }
                } else {
                    Text(
                        text = cleanSeg,
                        color = scheme.bubbleText,
                        fontSize = (16f * fontScale).sp,
                        lineHeight = (21f * fontScale).sp
                    )
                }
            }
        }
    }
}

/**
 * 1:1 with `GroupInfoScreen.jsx`:
 * - `PageScaffold` with back button and group title.
 * - Hero Card (`borderRadius = 20.dp`) with 16:9 banner box, group title (`20.sp`, Bold),
 *   `"Участников: ${chat.members.size}"`, `"Публичная группа"` / `"Приватная группа"`.
 * - Quick Actions Row (`"Ссылка"`, `"Добавить участника"`, `"Покинуть группу"`).
 * - `"Участники"` section listing all `chat.members` using `ListRow` with role badge (`"Владелец"`, `"Админ"`, `"Участник"`).
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
        // Hero Card (`borderRadius = 20.dp`) with 16:9 banner box, group title (`20.sp`, Bold),
        // `"Участников: ${chat.members.size}"`, `"Публичная группа"` / `"Приватная группа"`.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                scheme.accent.copy(alpha = 0.55f),
                                scheme.primary.copy(alpha = 0.45f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = groupTitle.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 42.sp,
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = groupTitle,
                    color = scheme.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${tr(lang, "Участников", "Катышуучулар", "Members")}: ${chat.members.size}",
                        color = scheme.muted,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "•",
                        color = scheme.muted,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isPrivate) {
                            tr(lang, "Приватная группа", "Жеке тайпа", "Private group")
                        } else {
                            tr(lang, "Публичная группа", "Ачык тайпа", "Public group")
                        },
                        color = scheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Quick Actions Row (`"Ссылка"`, `"Добавить участника"`, `"Покинуть группу"`)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        // `"Участники"` section listing all `chat.members` using `ListRow` with role badge (`"Владелец"`, `"Админ"`, `"Участник"`)
        SectionUppercaseHeader(
            title = tr(lang, "Участники", "Катышуучулар", "Members")
        )

        if (chat.members.isEmpty()) {
            EmptyHintCard(
                text = tr(lang, "Список участников пуст", "Катышуучулар тизмеси бош", "No members listed")
            )
        } else {
            chat.members.forEach { member ->
                GroupMemberListRow(
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr(lang, "Добавить участника", "Катышуучу кошуу", "Add member"),
                    color = scheme.text,
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
    val fg = if (danger) scheme.danger else scheme.text
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (danger) scheme.danger else scheme.primary,
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
private fun GroupMemberListRow(
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
        "OWNER", "CREATOR" -> tr(lang, "Владелец", "Ээси", "Owner") to scheme.primary
        "ADMIN", "ADMINISTRATOR" -> tr(lang, "Админ", "Админ", "Admin") to scheme.accent
        else -> tr(lang, "Участник", "Катышуучу", "Member") to scheme.muted
    }

    ListRow(
        title = title,
        subtitle = subtitle,
        avatarUrl = avatarUrl,
        avatarLabel = title,
        showChevron = false,
        onClick = onClick,
        rightContent = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(roleColor.copy(alpha = 0.15f))
                    .border(1.dp, roleColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = roleLabel,
                    color = roleColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
