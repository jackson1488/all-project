package space.neongram.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import space.neongram.app.core.model.UserDto
import space.neongram.app.core.ui.LanguageSwitcher
import space.neongram.app.core.ui.ListRow
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeonAvatar
import space.neongram.app.core.ui.NeonInput
import space.neongram.app.core.ui.PageScaffold
import space.neongram.app.core.ui.PrimaryButton
import space.neongram.app.core.ui.parseHexColorOrNull
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.ActiveOverlayScreen
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel

private fun resolveFullMediaUrl(rawUrl: String?, serverBaseUrl: String): String? {
    val clean = rawUrl?.takeIf { it.isNotBlank() } ?: return null
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    val base = serverBaseUrl.trimEnd('/')
    return if (clean.startsWith("/")) "$base$clean" else "$base/$clean"
}

/**
 * 1:1 with `ProfileScreen.jsx`.
 */
@Composable
fun ProfileTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val clipboard = LocalClipboardManager.current
    val serverBaseUrl = state.session.serverUrl

    val user = state.currentUser ?: UserDto(
        id = state.session.userId,
        username = state.session.username,
        displayName = state.session.displayName
    )

    var showTopMenu by remember { mutableStateOf(false) }

    PageScaffold(
        title = tr(lang, "Профиль", "Профиль", "Profile"),
        rightContent = {
            Box {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, CircleShape)
                        .clickable { showTopMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Menu",
                        tint = scheme.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showTopMenu,
                    onDismissRequest = { showTopMenu = false },
                    modifier = Modifier.background(scheme.surface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tr(lang, "Скопировать @username", "@username көчүрүү", "Copy @username"),
                                color = scheme.text
                            )
                        },
                        onClick = {
                            showTopMenu = false
                            clipboard.setText(AnnotatedString("@${user.username}"))
                            viewModel.showToast(tr(lang, "@username скопирован", "@username көчүрүлдү", "@username copied"))
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tr(lang, "Скопировать ID", "ID көчүрүү", "Copy ID"),
                                color = scheme.text
                            )
                        },
                        onClick = {
                            showTopMenu = false
                            clipboard.setText(AnnotatedString(user.id))
                            viewModel.showToast(tr(lang, "ID скопирован", "ID көчүрүлдү", "ID copied"))
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tr(lang, "Скопировать ссылку", "Шилтемени көчүрүү", "Copy link"),
                                color = scheme.text
                            )
                        },
                        onClick = {
                            showTopMenu = false
                            clipboard.setText(AnnotatedString("neongram://contact/${user.username}"))
                            viewModel.showToast(tr(lang, "Ссылка скопирована", "Шилтеме көчүрүлдү", "Link copied"))
                        }
                    )
                }
            }
        }
    ) {
        // Hero Card (borderRadius = 20.dp, padding = 16.dp, centered)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonAvatar(
                avatarUrl = resolveFullMediaUrl(user.avatarUrl, serverBaseUrl),
                label = user.effectiveName,
                sizeDp = 96.dp,
                cornerRadiusDp = 32.dp
            )

            Text(
                text = user.effectiveName,
                color = scheme.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = tr(lang, "в сети", "тармакта", "online"),
                color = scheme.muted,
                fontSize = 12.sp
            )

            // 3 Action Buttons Row (minHeight = 34.dp, borderRadius = 10.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileActionChip(
                    label = tr(lang, "Выбрать фото", "Сүрөт тандоо", "Choose photo"),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.showToast(tr(lang, "Выбор фото профиля", "Профиль сүрөтүн тандоо", "Choose profile photo"))
                    }
                )
                ProfileActionChip(
                    label = tr(lang, "Изменить инфо", "Инфо өзгөртүү", "Edit info"),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO)
                    }
                )
                ProfileActionChip(
                    label = tr(lang, "Настройки", "Жөндөөлөр", "Settings"),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.selectTab(3)
                    }
                )
            }
        }

        // Bio & Details Card (borderRadius = 16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileDetailField(
                label = tr(lang, "ЛОГИН", "ЛОГИН", "USERNAME"),
                value = "@${user.username.ifBlank { "neongram" }}"
            )
            ProfileDetailField(
                label = "ID",
                value = user.id.ifBlank { "-" }
            )
            ProfileDetailField(
                label = tr(lang, "ДЕНЬ РОЖДЕНИЯ", "ТУУЛГАН КҮНҮ", "BIRTHDAY"),
                value = user.birthday?.takeIf { it.isNotBlank() } ?: "-"
            )
            ProfileDetailField(
                label = tr(lang, "О СЕБЕ", "ӨЗҮ ЖӨНҮНДӨ", "BIO"),
                value = user.bio?.takeIf { it.isNotBlank() }
                    ?: tr(lang, "Пользователь Neongram", "Neongram колдонуучусу", "Neongram User")
            )
        }

        // Stories / Posts Strip Card (borderRadius = 18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(lang, "Мои истории", "Менин окуяларым", "My Stories"),
                    color = scheme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceAlt)
                        .border(1.dp, scheme.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.showToast(tr(lang, "Добавление истории", "Окуя кошуу", "Add story"))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tr(lang, "+ Добавить историю", "+ Окуя кошуу", "+ Add story"),
                        color = scheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.myStories.isEmpty()) {
                Text(
                    text = tr(
                        lang,
                        "У вас пока нет опубликованных историй",
                        "Сизде азырынча жарыяланган окуялар жок",
                        "You have no published stories yet"
                    ),
                    color = scheme.muted,
                    fontSize = 13.sp
                )
            } else {
                // 3-Column Grid of Stories
                state.myStories.chunked(3).forEach { rowStories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowStories.forEach { story ->
                            val mediaUrl = resolveFullMediaUrl(story.mediaUrl, serverBaseUrl)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceAlt)
                                    .border(1.dp, scheme.border, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                if (!mediaUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = mediaUrl,
                                        contentDescription = story.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (!story.caption.isNullOrBlank()) {
                                    Text(
                                        text = story.caption,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                        repeat(3 - rowStories.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileActionChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceAlt)
            .border(1.dp, scheme.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = scheme.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileDetailField(
    label: String,
    value: String
) {
    val scheme = LocalNeonScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = scheme.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = scheme.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// PEER PROFILE SCREEN (`UserProfileScreen.jsx`)
// ============================================================================

@Composable
fun PeerProfileScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val serverBaseUrl = state.session.serverUrl

    val peer = state.peerProfileUser
        ?: state.activeChat?.resolvePeerUser(state.session.userId)
        ?: UserDto(id = "peer", username = "neongram_user", displayName = "Neongram User")

    var selectedTab by remember { mutableIntStateOf(0) }

    PageScaffold(
        title = peer.effectiveName,
        onBack = { viewModel.navigateBack() }
    ) {
        // Hero Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonAvatar(
                avatarUrl = resolveFullMediaUrl(peer.avatarUrl, serverBaseUrl),
                label = peer.effectiveName,
                sizeDp = 96.dp,
                cornerRadiusDp = 32.dp
            )

            Text(
                text = peer.effectiveName,
                color = scheme.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "@${peer.username} • " + if (peer.isOnline) {
                    tr(lang, "в сети", "тармакта", "online")
                } else {
                    tr(lang, "не в сети", "тармакта эмес", "offline")
                },
                color = scheme.muted,
                fontSize = 13.sp
            )

            // 4 Action Icon Buttons: Написать, В контакты, Позвонить, Секретный чат
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PeerActionIconButton(
                    label = tr(lang, "Написать", "Жазуу", "Message"),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onClick = { viewModel.startDirectChat(peer) }
                )
                PeerActionIconButton(
                    label = tr(lang, "В контакты", "Байланышка", "Add Contact"),
                    icon = Icons.Default.PersonAdd,
                    onClick = {
                        viewModel.showToast(tr(lang, "Добавлено в контакты", "Байланыштарга кошулду", "Added to contacts"))
                    }
                )
                PeerActionIconButton(
                    label = tr(lang, "Позвонить", "Чалуу", "Call"),
                    icon = Icons.Default.Call,
                    onClick = {
                        viewModel.showToast(tr(lang, "Аудиозвонок @${peer.username}", "Чалуу @${peer.username}", "Calling @${peer.username}"))
                    }
                )
                PeerActionIconButton(
                    label = tr(lang, "Секретный чат", "Жашыруун чат", "Secret Chat"),
                    icon = Icons.Default.Lock,
                    onClick = {
                        viewModel.startDirectChat(peer)
                        viewModel.showToast(tr(lang, "E2EE чат открыт", "E2EE чат ачылды", "E2EE chat opened"))
                    }
                )
            }
        }

        // 3-Tab Segmented Switcher: Инфо / Медиа / Файлы
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                tr(lang, "Инфо", "Инфо", "Info"),
                tr(lang, "Медиа", "Медиа", "Media"),
                tr(lang, "Файлы", "Файлдар", "Files")
            ).forEachIndexed { idx, tabLabel ->
                val active = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) scheme.primary else Color.Transparent)
                        .clickable { selectedTab = idx },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = if (active) Color(0xFF06070C) else scheme.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileDetailField(
                        label = tr(lang, "ЛОГИН", "ЛОГИН", "USERNAME"),
                        value = "@${peer.username}"
                    )
                    ProfileDetailField(
                        label = "ID",
                        value = peer.id.ifBlank { "-" }
                    )
                    ProfileDetailField(
                        label = tr(lang, "ДЕНЬ РОЖДЕНИЯ", "ТУУЛГАН КҮНҮ", "BIRTHDAY"),
                        value = peer.birthday?.takeIf { it.isNotBlank() } ?: "-"
                    )
                    ProfileDetailField(
                        label = tr(lang, "О СЕБЕ", "ӨЗҮ ЖӨНҮНДӨ", "BIO"),
                        value = peer.bio?.takeIf { it.isNotBlank() }
                            ?: tr(lang, "Пользователь Neongram", "Neongram колдонуучусу", "Neongram User")
                    )
                }
            }
            1 -> {
                val mediaMsgs = state.messages.filter {
                    it.type == "IMAGE" || it.type == "VIDEO" || !it.fileUrl.isNullOrBlank()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    if (mediaMsgs.isEmpty()) {
                        Text(
                            text = tr(lang, "Общих медиафайлов пока нет", "Жалпы медиа жок", "No shared media yet"),
                            color = scheme.muted,
                            fontSize = 13.sp
                        )
                    } else {
                        mediaMsgs.forEach { msg ->
                            Text(
                                text = msg.fileName ?: msg.fileUrl.orEmpty(),
                                color = scheme.primary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                val fileMsgs = state.messages.filter { !it.fileName.isNullOrBlank() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    if (fileMsgs.isEmpty()) {
                        Text(
                            text = tr(lang, "Общих документов пока нет", "Жалпы файлдар жок", "No shared files yet"),
                            color = scheme.muted,
                            fontSize = 13.sp
                        )
                    } else {
                        fileMsgs.forEach { msg ->
                            Text(
                                text = msg.fileName.orEmpty(),
                                color = scheme.text,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerActionIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceAlt)
                .border(1.dp, scheme.border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            color = scheme.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// SETTINGS TAB SCREEN (`SettingsScreen.jsx`)
// ============================================================================

@Composable
fun SettingsTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language

    PageScaffold(
        title = tr(lang, "Настройки", "Жөндөөлөр", "Settings")
    ) {
        ListRow(
            title = tr(lang, "Аккаунт", "Аккаунт", "Account"),
            subtitle = tr(lang, "Профиль, смена аккаунта и выход", "Профиль жана чыгуу", "Profile, switch account & logout"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ACCOUNT) }
        )
        ListRow(
            title = tr(lang, "Конфиденциальность", "Купуялуулук", "Privacy"),
            subtitle = tr(lang, "Статус в сети, звонки, чтение и ЧС", "Тармактагы статус жана чалуулар", "Online status, calls & read receipts"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_PRIVACY) }
        )
        ListRow(
            title = tr(lang, "Безопасность", "Коопсуздук", "Security"),
            subtitle = tr(lang, "Код-пароль, биометрия, 2FA и пароль", "2FA жана сырсөз", "Passcode, biometrics, 2FA & password"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_SECURITY) }
        )
        ListRow(
            title = tr(lang, "Устройства", "Түзмөктөр", "Devices"),
            subtitle = tr(lang, "Активные сеансы и вход по QR", "Активдүү сеанстар жана QR кирүү", "Active sessions & QR login"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_DEVICES) }
        )
        ListRow(
            title = tr(lang, "Оформление", "Көрүнүш", "Appearance"),
            subtitle = tr(lang, "Темы, язык, размер шрифта, пузыри и обои", "Темалар, тил, шрифт жана обои", "Themes, language, font size, bubbles & wallpaper"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_APPEARANCE) }
        )
        ListRow(
            title = tr(lang, "Хранилище и данные", "Сактагыч жана маалымат", "Storage & Data"),
            subtitle = tr(lang, "Использование памяти и очистка кэша", "Эстутум жана кэшти тазалоо", "Memory usage & clear cache"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_STORAGE) }
        )
        ListRow(
            title = tr(lang, "Сервер", "Сервер", "Server"),
            subtitle = tr(lang, "Настройка адреса API и проверка связи", "API дареги жана байланыш текшерүү", "Configure API URL & health check"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_SERVER) }
        )
        ListRow(
            title = tr(lang, "О приложении", "Тиркеме жөнүндө", "About App"),
            subtitle = tr(lang, "Версия, поддержка, FAQ и условия", "Версия, колдоо жана FAQ", "Version, support, FAQ & terms"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ABOUT) }
        )
    }
}

// ============================================================================
// 1. ACCOUNT SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun AccountSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val user = state.currentUser ?: UserDto(
        id = state.session.userId,
        username = state.session.username,
        displayName = state.session.displayName
    )

    PageScaffold(
        title = tr(lang, "Аккаунт", "Аккаунт", "Account"),
        onBack = { viewModel.navigateBack() }
    ) {
        // Saved Account Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NeonAvatar(
                avatarUrl = resolveFullMediaUrl(user.avatarUrl, state.session.serverUrl),
                label = user.effectiveName,
                sizeDp = 48.dp,
                cornerRadiusDp = 16.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.effectiveName,
                    color = scheme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${user.username}",
                    color = scheme.muted,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primary.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tr(lang, "Активен", "Активдүү", "Active"),
                    color = scheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ListRow(
            title = tr(lang, "Профиль", "Профиль", "Profile Info"),
            subtitle = tr(lang, "Имя, @username, дата рождения и описание", "Аты, @username жана өзү жөнүндө", "Display name, @username, birthday & bio"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO) }
        )

        ListRow(
            title = tr(lang, "Пароль", "Сырсөз", "Password"),
            subtitle = tr(lang, "Изменить пароль и параметры входа", "Сырсөздү өзгөртүү", "Change password & 2FA settings"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_SECURITY) }
        )

        ListRow(
            title = tr(lang, "Выйти из аккаунта", "Аккаунттан чыгуу", "Log Out"),
            subtitle = tr(lang, "Завершить текущий сеанс на этом устройстве", "Учурдагы сеансты аяктоо", "End current session on this device"),
            danger = true,
            showChevron = false,
            onClick = { viewModel.logout() }
        )
    }
}

// ============================================================================
// 2. ACCOUNT INFO EDIT SUB-SCREEN
// ============================================================================

@Composable
fun AccountInfoEditScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val user = state.currentUser ?: UserDto(
        id = state.session.userId,
        username = state.session.username,
        displayName = state.session.displayName
    )

    var displayName by remember(user) { mutableStateOf(user.displayName ?: user.effectiveName) }
    var username by remember(user) { mutableStateOf(user.username) }
    var bio by remember(user) { mutableStateOf(user.bio.orEmpty()) }
    var birthday by remember(user) { mutableStateOf(user.birthday.orEmpty()) }

    PageScaffold(
        title = tr(lang, "Изменить инфо", "Инфо өзгөртүү", "Edit Profile"),
        onBack = { viewModel.navigateBack() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr(lang, "Отображаемое имя", "Көрсөтүлгөн аты", "Display Name"),
                color = scheme.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            NeonInput(
                value = displayName,
                onValueChange = { displayName = it },
                placeholder = tr(lang, "Ваше имя", "Сиздин атыңыз", "Your display name")
            )

            Text(
                text = tr(lang, "Имя пользователя (@username)", "Колдонуучу аты (@username)", "Username (@username)"),
                color = scheme.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            NeonInput(
                value = username,
                onValueChange = { username = it.removePrefix("@") },
                placeholder = "username"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tr(lang, "О себе (макс. 60)", "Өзү жөнүндө (макс. 60)", "Bio (max 60)"),
                    color = scheme.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${bio.length}/60",
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            NeonInput(
                value = bio,
                onValueChange = { if (it.length <= 60) bio = it },
                placeholder = tr(lang, "Расскажите о себе", "Өзүңүз жөнүндө жазыңыз", "Tell about yourself")
            )

            Text(
                text = tr(lang, "День рождения (YYYY-MM-DD)", "Туулган күнү (YYYY-MM-DD)", "Birthday (YYYY-MM-DD)"),
                color = scheme.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            NeonInput(
                value = birthday,
                onValueChange = { birthday = it },
                placeholder = "2000-01-25"
            )

            Spacer(modifier = Modifier.height(4.dp))

            PrimaryButton(
                label = tr(lang, "Сохранить", "Сактоо", "Save"),
                onClick = {
                    viewModel.updateMyProfile(
                        displayName = displayName.trim(),
                        username = username.trim(),
                        bio = bio.trim(),
                        birthday = birthday.trim().ifBlank { null }
                    )
                }
            )
        }
    }
}

// ============================================================================
// 3. PRIVACY SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun PrivacySettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val user = state.currentUser ?: UserDto()

    var showOnline by remember(user) { mutableStateOf(user.showOnline) }
    var showLastSeen by remember(user) { mutableStateOf(user.showLastSeen) }
    var allowCalls by remember(user) { mutableStateOf(user.allowCalls) }
    var callsContactsOnly by remember(user) { mutableStateOf(user.callsContactsOnly) }
    var readReceipts by remember(user) { mutableStateOf(user.readReceipts) }

    fun pushUpdate(
        online: Boolean = showOnline,
        lastSeen: Boolean = showLastSeen,
        calls: Boolean = allowCalls,
        contactsOnly: Boolean = callsContactsOnly,
        receipts: Boolean = readReceipts
    ) {
        showOnline = online
        showLastSeen = lastSeen
        allowCalls = calls
        callsContactsOnly = contactsOnly
        readReceipts = receipts
        viewModel.updatePrivacySettings(online, lastSeen, calls, contactsOnly, receipts)
    }

    PageScaffold(
        title = tr(lang, "Конфиденциальность", "Купуялуулук", "Privacy"),
        onBack = { viewModel.navigateBack() }
    ) {
        PrivacySwitchRow(
            title = tr(lang, "Показывать статус \"в сети\"", "\"Тармакта\" статусун көрсөтүү", "Show \"online\" status"),
            subtitle = tr(lang, "Другие пользователи видят, когда вы онлайн", "Башкалар сиздин онлайн экениңизди көрөт", "Let others see when you're online"),
            checked = showOnline,
            onCheckedChange = { pushUpdate(online = it) }
        )
        PrivacySwitchRow(
            title = tr(lang, "Показывать время захода", "Акыркы кирген убакытты көрсөтүү", "Show last seen time"),
            subtitle = tr(lang, "Отображать время последней активности", "Акыркы активдүүлүк убактысы", "Display your last active timestamp"),
            checked = showLastSeen,
            onCheckedChange = { pushUpdate(lastSeen = it) }
        )
        PrivacySwitchRow(
            title = tr(lang, "Разрешить звонки", "Чалууларга уруксат берүү", "Allow calls"),
            subtitle = tr(lang, "Принимать голосовые и видеозвонки", "Үн жана видео чалууларды кабыл алуу", "Receive voice and video calls"),
            checked = allowCalls,
            onCheckedChange = { pushUpdate(calls = it) }
        )
        PrivacySwitchRow(
            title = tr(lang, "Звонки только от контактов", "Байланыштардан гана чалуулар", "Calls from contacts only"),
            subtitle = tr(lang, "Блокировать звонки от незнакомых аккаунтов", "Бейтааныш аккаунттардан чалууларды чектөө", "Block calls from unknown users"),
            checked = callsContactsOnly,
            onCheckedChange = { pushUpdate(contactsOnly = it) }
        )
        PrivacySwitchRow(
            title = tr(lang, "Отчеты о прочтении", "Окулгандыгы тууралуу белги", "Read receipts"),
            subtitle = tr(lang, "Показывать двойную галочку при прочтении", "Билдирүү окулганда кош белги көрсөтүү", "Show double-check when messages are read"),
            checked = readReceipts,
            onCheckedChange = { pushUpdate(receipts = it) }
        )
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = LocalNeonScheme.current
    ListRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        onClick = { onCheckedChange(!checked) },
        rightContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF06070C),
                    checkedTrackColor = scheme.primary,
                    uncheckedThumbColor = scheme.muted,
                    uncheckedTrackColor = scheme.surfaceAlt
                )
            )
        }
    )
}

// ============================================================================
// 4. SECURITY SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun SecuritySettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    PageScaffold(
        title = tr(lang, "Безопасность", "Коопсуздук", "Security"),
        onBack = { viewModel.navigateBack() }
    ) {
        // Change Password Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr(lang, "Смена пароля", "Сырсөздү өзгөртүү", "Change Password"),
                color = scheme.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            NeonInput(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                placeholder = tr(lang, "Текущий пароль", "Учурдагы сырсөз", "Current password"),
                isPassword = true
            )

            NeonInput(
                value = newPassword,
                onValueChange = { newPassword = it },
                placeholder = tr(lang, "Новый пароль (от 6 символов)", "Жаңы сырсөз", "New password (min 6 chars)"),
                isPassword = true
            )

            NeonInput(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = tr(lang, "Повторите новый пароль", "Жаңы сырсөздү кайталаңыз", "Confirm new password"),
                isPassword = true
            )

            PrimaryButton(
                label = tr(lang, "Обновить пароль", "Сырсөздү жаңыртуу", "Update Password"),
                onClick = {
                    when {
                        currentPassword.isBlank() || newPassword.isBlank() -> {
                            viewModel.showToast(tr(lang, "Заполните все поля", "Бардык талааларды толтуруңуз", "Fill all fields"))
                        }
                        newPassword != confirmPassword -> {
                            viewModel.showToast(tr(lang, "Пароли не совпадают", "Сырсөздөр дал келбейт", "Passwords do not match"))
                        }
                        newPassword.length < 6 -> {
                            viewModel.showToast(tr(lang, "Минимум 6 символов", "Кеминде 6 белги", "Minimum 6 characters"))
                        }
                        else -> {
                            viewModel.changePassword(currentPassword, newPassword)
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        }
                    }
                }
            )
        }

        // 2FA Info Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "2FA",
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = tr(lang, "Двухфакторная защита (2FA TOTP)", "Эки баскычтуу коргоо (2FA)", "Two-Factor Authentication (2FA)"),
                    color = scheme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = tr(
                    lang,
                    "Защитите вход одноразовыми кодами Google Authenticator / Authy и подтверждением новых устройств.",
                    "Аккаунтуңузду Google Authenticator коду менен коргоңуз.",
                    "Protect your account with TOTP authenticator codes and device login approval."
                ),
                color = scheme.muted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ============================================================================
// 5. DEVICES SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun DevicesSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    PageScaffold(
        title = tr(lang, "Устройства", "Түзмөктөр", "Devices"),
        onBack = { viewModel.navigateBack() }
    ) {
        // Connect Device by QR Button -> AUTH_QR
        PrimaryButton(
            label = tr(lang, "Подключить устройство по QR", "Түзмөктү QR менен туташтыруу", "Link Device via QR"),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.AUTH_QR) }
        )

        // Terminate Other Sessions Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.danger.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable { viewModel.terminateOtherSessions() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tr(lang, "Завершить другие сеансы", "Башка сеанстарды аяктоо", "Terminate other sessions"),
                color = scheme.danger,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = tr(lang, "Активные сеансы", "Активдүү сеанстар", "Active Sessions"),
            color = scheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (state.activeSessions.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = scheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Android • Neongram Native Kotlin",
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(lang, "Текущий сеанс • В сети", "Учурдагы сеанс • Тармакта", "Current session • Online"),
                        color = scheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            state.activeSessions.forEach { sessionItem ->
                val isCurrent = sessionItem.id == state.currentSessionId
                val title = sessionItem.deviceName?.takeIf { it.isNotBlank() }
                    ?: sessionItem.clientPlatform?.takeIf { it.isNotBlank() }
                    ?: "Neongram Client"
                val osPart = sessionItem.clientOsName?.takeIf { it.isNotBlank() } ?: "Android"
                val ipPart = sessionItem.ipAddress?.takeIf { it.isNotBlank() } ?: "127.0.0.1"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .border(
                            1.dp,
                            if (isCurrent) scheme.primary else scheme.border,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = if (isCurrent) scheme.primary else scheme.muted
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$osPart • IP: $ipPart",
                            color = scheme.muted,
                            fontSize = 12.sp
                        )
                        if (isCurrent) {
                            Text(
                                text = tr(lang, "Это устройство", "Бул түзмөк", "This device"),
                                color = scheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!isCurrent) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Terminate",
                            tint = scheme.danger,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { viewModel.terminateSession(sessionItem.id) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. APPEARANCE SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun AppearanceSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val session = state.session
    val lang = session.language

    PageScaffold(
        title = tr(lang, "Оформление", "Көрүнүш", "Appearance"),
        onBack = { viewModel.navigateBack() }
    ) {
        // 1. System Theme Switch
        PrivacySwitchRow(
            title = tr(lang, "Системная тема", "Системалык тема", "Use System Theme"),
            subtitle = tr(lang, "Автоматически переключать темную и светлую тему", "Автоматтык түрдө алмаштыруу", "Automatically switch dark & light themes"),
            checked = session.useSystemTheme,
            onCheckedChange = { viewModel.updateAppearance(useSystemTheme = it) }
        )

        // 2. 3 Theme Cards (Cyber Dark Animated, Cyber Light Animated, Neon Purple)
        Text(
            text = tr(lang, "Цветовая тема", "Түс темасы", "Color Theme"),
            color = scheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        val themes = listOf(
            Triple("cyber_dark", "Cyber Dark Animated", Color(0xFF2FF2FF)),
            Triple("cyber_light", "Cyber Light Animated", Color(0xFF00B7E8)),
            Triple("neon_purple", "Neon Purple", Color(0xFFD15CFF))
        )

        themes.forEach { (id, label, previewColor) ->
            val isSelected = !session.useSystemTheme && session.themeId == id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surface)
                    .border(
                        1.5.dp,
                        if (isSelected) scheme.primary else scheme.border,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        viewModel.updateAppearance(themeId = id, useSystemTheme = false)
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                    )
                    Text(
                        text = label,
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = scheme.primary
                    )
                }
            }
        }

        // 3. LanguageSwitcher (RU | KY | EN)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = tr(lang, "Язык интерфейса", "Интерфейс тили", "App Language"),
                color = scheme.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            LanguageSwitcher(
                currentLang = session.language,
                onSelectLang = { viewModel.setLanguage(it) }
            )
        }

        // 4. Font Size Selector (Маленький, Стандарт, Крупный)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tr(lang, "Размер шрифта", "Шрифт өлчөмү", "Font Size"),
                color = scheme.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "small" to tr(lang, "Маленький", "Кичине", "Small"),
                    "medium" to tr(lang, "Стандарт", "Стандарт", "Medium"),
                    "large" to tr(lang, "Крупный", "Чоң", "Large")
                ).forEach { (key, title) ->
                    val active = session.fontSize == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) scheme.primary else scheme.surfaceAlt)
                            .clickable { viewModel.updateAppearance(fontSize = key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (active) Color(0xFF06070C) else scheme.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 5. Bubble Color Circles + Bubble Scope (Только мои / Для всех)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr(lang, "Цвет пузырей сообщений", "Билдирүү көбүкчөлөрүнүн түсү", "Message Bubble Color"),
                color = scheme.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            val bubblePresets = listOf(
                "" to tr(lang, "Цвет темы", "Тема түсү", "Theme"),
                "#2FF2FF" to "#2FF2FF",
                "#D15CFF" to "#D15CFF",
                "#4CD97B" to "#4CD97B",
                "#5D8BFF" to "#5D8BFF"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bubblePresets.forEach { (hex, label) ->
                    val isSelected = session.bubbleColorHex.equals(hex, ignoreCase = true)
                    val circleColor = parseHexColorOrNull(hex) ?: scheme.bubbleMe
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            viewModel.updateAppearance(bubbleColorHex = hex)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(circleColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) scheme.primary else scheme.border,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = if (hex.isEmpty()) tr(lang, "Тема", "Тема", "Theme") else hex,
                            color = scheme.muted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "mine" to tr(lang, "Только мои", "Меники гана", "Only mine"),
                    "all" to tr(lang, "Для всех", "Баарына", "For all")
                ).forEach { (scopeKey, scopeLabel) ->
                    val active = session.bubbleScope == scopeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) scheme.primary else scheme.surfaceAlt)
                            .clickable { viewModel.updateAppearance(bubbleScope = scopeKey) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scopeLabel,
                            color = if (active) Color(0xFF06070C) else scheme.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 6. Wallpaper Selector (Градиент, Неоновая сетка, Линии)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tr(lang, "Фон и обои", "Фон жана обои", "Chat Wallpaper"),
                color = scheme.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "gradient" to tr(lang, "Градиент", "Градиент", "Gradient"),
                    "mesh" to tr(lang, "Неоновая сетка", "Неон тор", "Neon Mesh"),
                    "grid" to tr(lang, "Линии", "Сызыктар", "Cyber Grid")
                ).forEach { (wpKey, wpTitle) ->
                    val active = session.wallpaper == wpKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) scheme.primary else scheme.surfaceAlt)
                            .clickable { viewModel.updateAppearance(wallpaper = wpKey) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = wpTitle,
                            color = if (active) Color(0xFF06070C) else scheme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 7. STORAGE SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun StorageSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    var mediaMb by remember { mutableStateOf(42.4f) }
    var audioMb by remember { mutableStateOf(18.6f) }
    var docsMb by remember { mutableStateOf(11.2f) }

    val totalMb = (mediaMb + audioMb + docsMb).coerceAtLeast(0.1f)

    PageScaffold(
        title = tr(lang, "Хранилище и данные", "Сактагыч", "Storage & Data"),
        onBack = { viewModel.navigateBack() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%.1f MB", mediaMb + audioMb + docsMb),
                color = scheme.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )

            // Visual Storage Usage Bar (Медиа #7C5CFF, Аудио #19B6C7, Документы #FF8A4C)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(scheme.surfaceAlt)
            ) {
                Box(
                    modifier = Modifier
                        .weight((mediaMb / totalMb).coerceAtLeast(0.05f))
                        .fillMaxSize()
                        .background(Color(0xFF7C5CFF))
                )
                Box(
                    modifier = Modifier
                        .weight((audioMb / totalMb).coerceAtLeast(0.05f))
                        .fillMaxSize()
                        .background(Color(0xFF19B6C7))
                )
                Box(
                    modifier = Modifier
                        .weight((docsMb / totalMb).coerceAtLeast(0.05f))
                        .fillMaxSize()
                        .background(Color(0xFFFF8A4C))
                )
            }

            StorageLegendRow(
                label = tr(lang, "Медиа", "Медиа", "Media"),
                color = Color(0xFF7C5CFF),
                sizeText = String.format(java.util.Locale.US, "%.1f MB", mediaMb)
            )
            StorageLegendRow(
                label = tr(lang, "Аудио", "Аудио", "Audio"),
                color = Color(0xFF19B6C7),
                sizeText = String.format(java.util.Locale.US, "%.1f MB", audioMb)
            )
            StorageLegendRow(
                label = tr(lang, "Документы", "Документтер", "Documents"),
                color = Color(0xFFFF8A4C),
                sizeText = String.format(java.util.Locale.US, "%.1f MB", docsMb)
            )
        }

        PrimaryButton(
            label = tr(lang, "Очистить кэш медиа", "Медиа кэшти тазалоо", "Clear Media Cache"),
            onClick = {
                mediaMb = 0.0f
                audioMb = 0.0f
                docsMb = 0.0f
                viewModel.showToast(tr(lang, "Кэш успешно очищен", "Кэш тазаланды", "Cache cleared"))
            }
        )
    }
}

@Composable
private fun StorageLegendRow(
    label: String,
    color: Color,
    sizeText: String
) {
    val scheme = LocalNeonScheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = label, color = scheme.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text = sizeText, color = scheme.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================================
// 8. SERVER SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun ServerSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    var serverUrl by remember(state.session.serverUrl) {
        mutableStateOf(state.session.serverUrl.ifBlank { "https://cloud.neongram.space" })
    }

    PageScaffold(
        title = tr(lang, "Сервер", "Сервер", "Server"),
        onBack = { viewModel.navigateBack() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr(lang, "Адрес API сервера", "API сервер дареги", "Backend API Server URL"),
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            NeonInput(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                placeholder = "https://cloud.neongram.space"
            )

            PrimaryButton(
                label = tr(lang, "Сохранить", "Сактоо", "Save"),
                onClick = { viewModel.saveServerUrl(serverUrl.trim()) }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceAlt)
                    .border(1.dp, scheme.primary, RoundedCornerShape(14.dp))
                    .clickable { viewModel.testServerHealth(serverUrl.trim()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(lang, "Проверить связь", "Байланышты текшерүү", "Test Connection"),
                    color = scheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.bgAlt)
                    .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                    .clickable {
                        serverUrl = "https://cloud.neongram.space"
                        viewModel.saveServerUrl("https://cloud.neongram.space")
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(
                        lang,
                        "Основной сервер (cloud.neongram.space)",
                        "Негизги сервер (cloud.neongram.space)",
                        "Main Server (cloud.neongram.space)"
                    ),
                    color = scheme.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ============================================================================
// 9. ABOUT SETTINGS SUB-SCREEN
// ============================================================================

@Composable
fun AboutSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    PageScaffold(
        title = tr(lang, "О приложении", "Тиркеме жөнүндө", "About App"),
        onBack = { viewModel.navigateBack() }
    ) {
        // App Version & Description Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.20f),
                            scheme.accent.copy(alpha = 0.20f),
                            scheme.surface
                        )
                    )
                )
                .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Neongram Messenger",
                color = scheme.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = tr(lang, "Версия: 1.0.0-kotlin", "Версия: 1.0.0-kotlin", "Version: 1.0.0-kotlin"),
                color = scheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr(
                    lang,
                    "Киберпанк-мессенджер нового поколения с защищенными чатами, встроенным ИИ, мини-сервисами, TON кошельком и Neongram Bank.",
                    "Коопсуз чаттар, ИИ жана мини-сервистери бар жаңы муундагы киберпанк-мессенджер.",
                    "Next-gen cyberpunk messenger featuring real-time chats, built-in AI, mini-apps, TON wallet, and Neongram Bank."
                ),
                color = scheme.muted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        // Quick Start Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr(lang, "Быстрый старт", "Тез баштоо", "Quick Start"),
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr(
                    lang,
                    "• Добавляйте друзей через @username или QR-код во вкладке Контакты.\n• Используйте Избранное для личных заметок и файлов.\n• Открывайте Погоду, Крипту, Музыку и Браузер во вкладке Сервисы.",
                    "• Контакты бөлүмүнөн @username же QR аркылуу досторду кошуңуз.\n• Сервистер бөлүмүнөн Аба ырайы, Крипто, Музыка жана Браузерди колдонуңуз.",
                    "• Add friends via @username or QR in Contacts.\n• Use Saved Messages for personal notes.\n• Explore Weather, Crypto, Music & Browser in Services."
                ),
                color = scheme.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        // FAQ Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "FAQ",
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr(
                    lang,
                    "В: Как войти на втором устройстве?\nО: Откройте Настройки → Устройства → Подключить по QR.\n\nВ: Можно ли сменить сервер?\nО: Да, в разделе Настройки → Сервер доступен выбор адреса API.",
                    "С: Экинчи түзмөктөн кантип кирем?\nЖ: Жөндөөлөр → Түзмөктөр → QR менен туташтыруу.",
                    "Q: How to log in on another device?\nA: Open Settings → Devices → Link Device via QR.\n\nQ: Can I switch backend server?\nA: Yes, under Settings → Server."
                ),
                color = scheme.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        // Terms of Use Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tr(lang, "Условия использования", "Колдонуу шарттары", "Terms of Use"),
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr(
                    lang,
                    "Используя Neongram, вы соглашаетесь с политикой конфиденциальности и правилами безопасности платформы cloud.neongram.space.",
                    "Neongram колдонуу менен купуялуулук саясатына макул болосуз.",
                    "By using Neongram, you agree to the privacy policy and security guidelines of cloud.neongram.space."
                ),
                color = scheme.muted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
