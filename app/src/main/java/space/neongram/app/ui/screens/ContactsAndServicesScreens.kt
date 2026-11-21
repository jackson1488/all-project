package space.neongram.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import space.neongram.app.core.model.UserDto
import space.neongram.app.core.ui.AnimatedNeonBackground
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeonAvatar
import space.neongram.app.core.ui.NeonInput
import space.neongram.app.core.ui.PrimaryButton
import space.neongram.app.core.ui.RealQrScannerBox
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel
import kotlin.math.abs

// ============================================================================
// 2026 iOS 18 LIQUID GLASS & AURORA VIOLET PALETTE HELPERS
// ============================================================================

private val LiquidCyan = Color(0xFF00F0FF)
private val AuroraViolet = Color(0xFF8B5CF6)
private val AuroraMagenta = Color(0xFFEC4899)
private val OnlineEmerald = Color(0xFF10B981)

private fun resolveFullAvatarUrl(rawUrl: String?, serverBaseUrl: String): String? {
    val clean = rawUrl?.takeIf { it.isNotBlank() } ?: return null
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    val base = serverBaseUrl.trimEnd('/')
    return if (clean.startsWith("/")) "$base$clean" else "$base/$clean"
}

/**
 * Deterministic visual QR matrix rendered on a Jetpack Compose Canvas
 * with a Liquid Glass specular border.
 */
@Composable
fun VisualQrCodeBox(
    payload: String,
    modifier: Modifier = Modifier
) {
    val scheme = LocalNeonScheme.current
    val gridSize = 21
    val matrix = remember(payload) {
        val hash = payload.hashCode()
        Array(gridSize) { r ->
            BooleanArray(gridSize) { c ->
                val inTopLeft = r < 7 && c < 7
                val inTopRight = r < 7 && c >= gridSize - 7
                val inBottomLeft = r >= gridSize - 7 && c < 7
                when {
                    inTopLeft -> {
                        val lr = r
                        val lc = c
                        lr == 0 || lr == 6 || lc == 0 || lc == 6 || (lr in 2..4 && lc in 2..4)
                    }
                    inTopRight -> {
                        val lr = r
                        val lc = c - (gridSize - 7)
                        lr == 0 || lr == 6 || lc == 0 || lc == 6 || (lr in 2..4 && lc in 2..4)
                    }
                    inBottomLeft -> {
                        val lr = r - (gridSize - 7)
                        val lc = c
                        lr == 0 || lr == 6 || lc == 0 || lc == 6 || (lr in 2..4 && lc in 2..4)
                    }
                    r == 7 || c == 7 -> false
                    else -> {
                        val charVal = if (payload.isNotEmpty()) {
                            payload[(r * gridSize + c) % payload.length].code
                        } else {
                            42
                        }
                        ((r * 31 + c * 17 + charVal + abs(hash)) % 3) != 0
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(196.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(scheme.primary, scheme.accent)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / gridSize
            val cellH = size.height / gridSize
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = Color(0xFF0A0B10),
                            topLeft = Offset(c * cellW, r * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. CONTACTS TAB SCREEN — 2026 iOS 18 LIQUID GLASS & AURORA VIOLET
// ============================================================================

@Composable
fun ContactsTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val currentUserId = state.session.userId
    val serverBaseUrl = state.session.serverUrl

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showCreateGroupModal by remember { mutableStateOf(false) }
    var showAddLoginModal by remember { mutableStateOf(false) }
    var showMyQrModal by remember { mutableStateOf(false) }
    var showScanQrModal by remember { mutableStateOf(false) }
    var contactQrModalTab by remember { mutableStateOf("my_qr") }

    val knownContacts = remember(state.chats, state.searchResults, currentUserId, searchQuery) {
        val map = linkedMapOf<String, UserDto>()
        state.chats.forEach { chat ->
            if (!chat.isAiChat && !chat.isSystemChat && !chat.isSavedMessages) {
                chat.members.forEach { member ->
                    val u = member.user
                    if (u != null && u.id.isNotBlank() && u.id != currentUserId) {
                        map[u.id] = u
                    }
                }
            }
        }
        state.searchResults.forEach { u ->
            if (u.id.isNotBlank() && u.id != currentUserId) {
                map[u.id] = u
            }
        }
        val list = map.values.toList()
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            list
        } else {
            list.filter {
                it.effectiveName.lowercase().contains(q) ||
                    it.username.lowercase().contains(q)
            }
        }
    }

    val glassCardBrush = Brush.linearGradient(
        colors = if (scheme.isDark) {
            listOf(
                scheme.surface.copy(alpha = 0.78f),
                scheme.surfaceAlt.copy(alpha = 0.52f)
            )
        } else {
            listOf(
                scheme.surface.copy(alpha = 0.92f),
                scheme.surfaceAlt.copy(alpha = 0.76f)
            )
        }
    )

    val specularBorderBrush = Brush.linearGradient(
        colors = listOf(
            scheme.primary.copy(alpha = 0.55f),
            Color.White.copy(alpha = if (scheme.isDark) 0.16f else 0.65f),
            scheme.accent.copy(alpha = 0.50f)
        )
    )

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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Compact Telegram-style Header: "Контакты" (26.sp, ExtraBold) + Right compact glass pill buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr(lang, "Контакты", "Байланыштар", "Contacts"),
                        color = scheme.text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tr(
                            lang,
                            "${knownContacts.size} в вашем списке",
                            "${knownContacts.size} байланыш",
                            "${knownContacts.size} people connected"
                        ),
                        color = scheme.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Small Search button (38.dp circle)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(glassCardBrush)
                            .border(
                                width = 1.dp,
                                brush = specularBorderBrush,
                                shape = CircleShape
                            )
                            .clickable { showSearchBar = !showSearchBar },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Contacts",
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // "Создать группу" glass pill (RoundedCornerShape(20.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        scheme.primary.copy(alpha = 0.20f),
                                        scheme.accent.copy(alpha = 0.24f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        scheme.primary.copy(alpha = 0.75f),
                                        scheme.accent.copy(alpha = 0.65f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { showCreateGroupModal = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Create Group",
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tr(lang, "Создать группу", "Топ түзүү", "Create group"),
                            color = scheme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Small QR button (38.dp circle, QrCodeScanner) opening Contact QR Modal directly
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        scheme.primary.copy(alpha = 0.22f),
                                        scheme.accent.copy(alpha = 0.26f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = specularBorderBrush,
                                shape = CircleShape
                            )
                            .clickable {
                                contactQrModalTab = "my_qr"
                                showMyQrModal = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Contact QR",
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = showSearchBar) {
                Column {
                    // Frosted Glass Capsule Search Bar (RoundedCornerShape(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(glassCardBrush)
                            .border(
                                width = 1.dp,
                                brush = specularBorderBrush,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = tr(
                                        lang,
                                        "Поиск контактов или @username...",
                                        "Байланыш же @username издөө...",
                                        "Search contacts or @username..."
                                    ),
                                    color = scheme.muted,
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.searchUsers(it)
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = scheme.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(scheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(scheme.surfaceAlt.copy(alpha = 0.8f))
                                    .clickable {
                                        searchQuery = ""
                                        viewModel.searchUsers("")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = scheme.muted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Contacts List rendered as Frosted Liquid Glass Cards (RoundedCornerShape(26.dp))
            if (knownContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(glassCardBrush)
                            .border(1.dp, specularBorderBrush, RoundedCornerShape(28.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            scheme.primary.copy(alpha = 0.28f),
                                            scheme.accent.copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(1.dp, specularBorderBrush, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = tr(lang, "Список контактов пуст", "Байланыштар тизмеси бош", "No contacts yet"),
                            color = scheme.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr(
                                lang,
                                "Нажмите + чтобы добавить контакт по @username или QR-коду",
                                "+ басып @username же QR-код менен кошуңуз",
                                "Tap + to add a contact by @username or QR code"
                            ),
                            color = scheme.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(knownContacts, key = { it.id }) { contact ->
                        LiquidGlassContactCard(
                            contact = contact,
                            serverBaseUrl = serverBaseUrl,
                            lang = lang,
                            onClick = { viewModel.startDirectChat(contact) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(110.dp))
                    }
                }
            }
        }

        // Speed-Dial Glass Menu + Floating Liquid Glass FAB (60.dp circle with cyan-violet gradient & specular border)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidSpeedDialMenuItem(
                        label = tr(lang, "Добавить по логину", "Логин менен кошуу", "Add by username"),
                        icon = Icons.Default.PersonAdd,
                        onClick = {
                            fabExpanded = false
                            showAddLoginModal = true
                        }
                    )
                    LiquidSpeedDialMenuItem(
                        label = tr(lang, "Показать мой QR", "Менин QR кодум", "Show my QR"),
                        icon = Icons.Default.QrCode2,
                        onClick = {
                            fabExpanded = false
                            contactQrModalTab = "my_qr"
                            showMyQrModal = true
                        }
                    )
                    LiquidSpeedDialMenuItem(
                        label = tr(lang, "Сканировать QR", "QR сканерлөө", "Scan QR"),
                        icon = Icons.Default.QrCodeScanner,
                        onClick = {
                            fabExpanded = false
                            contactQrModalTab = "scan_qr"
                            showScanQrModal = true
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(scheme.primary, scheme.accent, AuroraMagenta)
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                scheme.primary.copy(alpha = 0.6f),
                                AuroraViolet.copy(alpha = 0.8f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable { fabExpanded = !fabExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Contacts Actions",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // 1. Create Group Modal
    if (showCreateGroupModal) {
        var groupTitle by remember { mutableStateOf("") }
        val selectedIds = remember { mutableStateListOf<String>() }

        Dialog(onDismissRequest = { showCreateGroupModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scheme.surface.copy(alpha = 0.96f),
                                scheme.bgAlt.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(1.2.dp, specularBorderBrush, RoundedCornerShape(28.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Создать группу", "Топ түзүү", "Create Group"),
                        color = scheme.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceAlt)
                            .clickable { showCreateGroupModal = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                NeonInput(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    placeholder = tr(lang, "Название группы", "Топтун аталышы", "Group title")
                )

                Text(
                    text = tr(lang, "Выберите участников:", "Катышуучуларды тандаңыз:", "Select members:"),
                    color = scheme.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(knownContacts, key = { it.id }) { contact ->
                        val isSelected = selectedIds.contains(contact.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        scheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        scheme.surfaceAlt.copy(alpha = 0.65f)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) scheme.primary else scheme.border,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    if (isSelected) selectedIds.remove(contact.id) else selectedIds.add(contact.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NeonAvatar(
                                    avatarUrl = resolveFullAvatarUrl(contact.avatarUrl, serverBaseUrl),
                                    label = contact.effectiveName,
                                    sizeDp = 36.dp,
                                    cornerRadiusDp = 18.dp
                                )
                                Column {
                                    Text(
                                        text = contact.effectiveName,
                                        color = scheme.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "@${contact.username}",
                                        color = scheme.muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                PrimaryButton(
                    label = tr(lang, "Создать группу", "Топ түзүү", "Create group"),
                    onClick = {
                        viewModel.createGroup(groupTitle, selectedIds.toList())
                        showCreateGroupModal = false
                    }
                )
            }
        }
    }

    // 2. Add by Login Modal
    if (showAddLoginModal) {
        var loginInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showAddLoginModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scheme.surface.copy(alpha = 0.96f),
                                scheme.bgAlt.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(1.2.dp, specularBorderBrush, RoundedCornerShape(28.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Добавить по логину", "Логин менен кошуу", "Add by username"),
                        color = scheme.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceAlt)
                            .clickable { showAddLoginModal = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                NeonInput(
                    value = loginInput,
                    onValueChange = { loginInput = it },
                    placeholder = "@username"
                )

                PrimaryButton(
                    label = tr(lang, "Открыть чат", "Чатты ачуу", "Open chat"),
                    onClick = {
                        val cleaned = loginInput.trim().removePrefix("@")
                        if (cleaned.isNotBlank()) {
                            viewModel.addContactByLogin(cleaned)
                            showAddLoginModal = false
                        }
                    }
                )
            }
        }
    }

    // 3. Unified Contact QR Modal with 2-tab glass pill switcher ("Мой QR-код" / "Сканировать QR")
    if (showMyQrModal || showScanQrModal) {
        val myUsername = state.currentUser?.username?.ifBlank { state.session.username } ?: state.session.username
        val qrPayload = "neongram://user/$myUsername"
        val clipboard = LocalClipboardManager.current
        var qrCodeInput by remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = {
                showMyQrModal = false
                showScanQrModal = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scheme.surface.copy(alpha = 0.96f),
                                scheme.bgAlt.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .border(1.2.dp, specularBorderBrush, RoundedCornerShape(28.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (contactQrModalTab == "my_qr") {
                            tr(lang, "Мой QR-код", "Менин QR-кодум", "My QR Code")
                        } else {
                            tr(lang, "Сканировать QR", "QR сканерлөө", "Scan QR")
                        },
                        color = scheme.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceAlt)
                            .clickable {
                                showMyQrModal = false
                                showScanQrModal = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = scheme.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2-Tab Glass Pill Switcher: "Мой QR-код" | "Сканировать QR"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceAlt.copy(alpha = 0.72f))
                        .border(1.dp, specularBorderBrush, RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "my_qr" to tr(lang, "Мой QR-код", "Менин QR-кодум", "Мой QR-код"),
                        "scan_qr" to tr(lang, "Сканировать QR", "QR сканерлөө", "Сканировать QR")
                    ).forEach { (tabKey, tabLabel) ->
                        val isSelected = contactQrModalTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                scheme.primary.copy(alpha = 0.30f),
                                                scheme.accent.copy(alpha = 0.36f)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Color.Transparent)
                                        )
                                    }
                                )
                                .clickable { contactQrModalTab = tabKey }
                                .padding(vertical = 9.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSelected) scheme.text else scheme.muted,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (contactQrModalTab == "my_qr") {
                    VisualQrCodeBox(payload = qrPayload)

                    Text(
                        text = qrPayload,
                        color = scheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    PrimaryButton(
                        label = tr(lang, "Скопировать ссылку", "Шилтемени көчүрүү", "Copy link"),
                        onClick = {
                            clipboard.setText(AnnotatedString(qrPayload))
                            viewModel.showToast(tr(lang, "Ссылка скопирована", "Шилтеме көчүрүлдү", "Link copied"))
                            showMyQrModal = false
                            showScanQrModal = false
                        }
                    )
                } else {
                    // Embed real camera QR scanner
                    RealQrScannerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        onQrCodeScanned = { scannedRaw ->
                            viewModel.handleScannedQrPayload(scannedRaw)
                            showMyQrModal = false
                            showScanQrModal = false
                        }
                    )

                    NeonInput(
                        value = qrCodeInput,
                        onValueChange = { qrCodeInput = it },
                        placeholder = "neongram://user/username"
                    )

                    PrimaryButton(
                        label = tr(lang, "Добавить контакт", "Байланыш кошуу", "Add contact"),
                        onClick = {
                            val raw = qrCodeInput.trim()
                            if (raw.isNotBlank()) {
                                viewModel.handleScannedQrPayload(raw)
                                showMyQrModal = false
                                showScanQrModal = false
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Frosted Liquid Glass Contact Card (`RoundedCornerShape(26.dp)`, `1.dp` specular gradient border,
 * circular `52.dp` avatar with glowing cyan/violet ring, bold name, `@username`, and online status dot).
 */
@Composable
private fun LiquidGlassContactCard(
    contact: UserDto,
    serverBaseUrl: String,
    lang: String,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    val avatarUrl = resolveFullAvatarUrl(contact.avatarUrl, serverBaseUrl)
    val initial = contact.effectiveName.trim().take(1).uppercase().ifBlank { "N" }

    val cardBrush = Brush.linearGradient(
        colors = if (scheme.isDark) {
            listOf(
                scheme.surface.copy(alpha = 0.80f),
                scheme.surfaceAlt.copy(alpha = 0.55f)
            )
        } else {
            listOf(
                scheme.surface.copy(alpha = 0.94f),
                scheme.surfaceAlt.copy(alpha = 0.78f)
            )
        }
    )

    val cardBorderBrush = Brush.linearGradient(
        colors = listOf(
            scheme.primary.copy(alpha = if (contact.isOnline) 0.65f else 0.35f),
            Color.White.copy(alpha = if (scheme.isDark) 0.14f else 0.60f),
            scheme.accent.copy(alpha = 0.45f)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(cardBrush)
            .border(
                width = 1.dp,
                brush = cardBorderBrush,
                shape = RoundedCornerShape(26.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Circular 52.dp Avatar with glowing cyan/violet ring + online status dot
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.25f),
                                scheme.accent.copy(alpha = 0.30f)
                            )
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(scheme.primary, scheme.accent, AuroraMagenta)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = scheme.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = contact.effectiveName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }

            // Online Status Dot
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(scheme.bg)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            if (contact.isOnline) OnlineEmerald else scheme.muted.copy(alpha = 0.55f)
                        )
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = contact.effectiveName,
                color = scheme.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "@${contact.username}",
                    color = scheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "•",
                    color = scheme.muted,
                    fontSize = 12.sp
                )
                Text(
                    text = if (contact.isOnline) {
                        tr(lang, "в сети", "тармакта", "online")
                    } else {
                        tr(lang, "не в сети", "тармакта эмес", "offline")
                    },
                    color = if (contact.isOnline) OnlineEmerald else scheme.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Frosted Glass Action Pill
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.16f),
                            scheme.accent.copy(alpha = 0.22f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.55f),
                            scheme.accent.copy(alpha = 0.55f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Message",
                tint = scheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LiquidSpeedDialMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        scheme.surface.copy(alpha = 0.94f),
                        scheme.surfaceAlt.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.65f),
                        scheme.accent.copy(alpha = 0.55f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = scheme.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================================
// 2. SERVICES TAB SCREEN — EXCLUSIVE NEONGRAM AI HUB (LIQUID GLASS 2026)
// ============================================================================

private data class AiModelOption(
    val id: String,
    val titleRu: String,
    val titleKy: String,
    val titleEn: String,
    val badge: String,
    val accentColor: Color
)

private data class AiPersonaOption(
    val id: String,
    val titleRu: String,
    val titleKy: String,
    val titleEn: String,
    val subtitleRu: String,
    val subtitleKy: String,
    val subtitleEn: String,
    val icon: ImageVector,
    val accentColor: Color
)

private data class QuickAiPromptChip(
    val titleRu: String,
    val titleKy: String,
    val titleEn: String,
    val promptRu: String,
    val promptKy: String,
    val promptEn: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Shared reactive holder so `ServicesTabScreen` works seamlessly with `state.aiModel` / `state.aiMode`
 * and `viewModel.setAiModel(...)` / `viewModel.setAiMode(...)`.
 */
private object NeongramAiHubPreferences {
    var aiModel by mutableStateOf("fast")
    var aiMode by mutableStateOf("normal")
}

val NeongramUiState.aiModel: String
    get() = NeongramAiHubPreferences.aiModel

val NeongramUiState.aiMode: String
    get() = NeongramAiHubPreferences.aiMode

fun NeongramViewModel.setAiModel(modelId: String) {
    NeongramAiHubPreferences.aiModel = modelId
}

fun NeongramViewModel.setAiMode(modeId: String) {
    NeongramAiHubPreferences.aiMode = modeId
}

/**
 * Opens the Neongram AI assistant chat and optionally dispatches an immediate prompt.
 */
private fun NeongramViewModel.askNeongramAi(state: NeongramUiState, rawPrompt: String) {
    val cleanPrompt = rawPrompt.trim()
    val aiChat = state.chats.firstOrNull { it.isAiChat }
    openAiAssistantChat()
    if (cleanPrompt.isNotBlank() && aiChat != null) {
        sendMessage(cleanPrompt)
    }
}

@Composable
fun ServicesTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    var promptInput by remember { mutableStateOf("") }

    val selectedModel = state.aiModel
    val selectedMode = state.aiMode

    val aiModels = remember {
        listOf(
            AiModelOption(
                id = "fast",
                titleRu = "⚡ Быстрая (Fast)",
                titleKy = "⚡ Ыкчам (Fast)",
                titleEn = "⚡ Fast (Neon-Flash)",
                badge = "0.2s • Instant",
                accentColor = LiquidCyan
            ),
            AiModelOption(
                id = "thinking",
                titleRu = "🧠 Думающая (Thinking)",
                titleKy = "🧠 Ойлонуучу (Thinking)",
                titleEn = "🧠 Thinking (DeepReason)",
                badge = "Chain-of-Thought",
                accentColor = AuroraViolet
            ),
            AiModelOption(
                id = "pro",
                titleRu = "💎 Лучшая (Pro)",
                titleKy = "💎 Мыкты (Pro)",
                titleEn = "💎 Best (Neon-Pro Ultra)",
                badge = "Max Intelligence",
                accentColor = AuroraMagenta
            )
        )
    }

    val aiPersonas = remember {
        listOf(
            AiPersonaOption(
                id = "normal",
                titleRu = "✨ Обычный режим",
                titleKy = "✨ Кадимки режим",
                titleEn = "✨ Standard Mode",
                subtitleRu = "Универсальный нейро-ассистент на каждый день",
                subtitleKy = "Күнүмдүк универсалдуу нейро-жардамчы",
                subtitleEn = "Versatile everyday neural assistant",
                icon = Icons.Default.AutoAwesome,
                accentColor = LiquidCyan
            ),
            AiPersonaOption(
                id = "study",
                titleRu = "🎓 Учебный режим",
                titleKy = "🎓 Окуу режими",
                titleEn = "🎓 Study & Tutor",
                subtitleRu = "Пошаговые разборы, формулы и подготовка",
                subtitleKy = "Кадамдык түшүндүрмөлөр жана даярдык",
                subtitleEn = "Step-by-step explanations & exam prep",
                icon = Icons.Default.School,
                accentColor = Color(0xFF38BDF8)
            ),
            AiPersonaOption(
                id = "psychologist",
                titleRu = "💜 Психолог",
                titleKy = "💜 Психолог",
                titleEn = "💜 Empathy Coach",
                subtitleRu = "Бережная поддержка, рефлексия и баланс",
                subtitleKy = "Камкор колдоо жана ички баланс",
                subtitleEn = "Mindful support, reflection & calm",
                icon = Icons.Default.Favorite,
                accentColor = AuroraMagenta
            ),
            AiPersonaOption(
                id = "coding",
                titleRu = "💻 Кодинг и Архитектура",
                titleKy = "💻 Кодинг жана Архитектура",
                titleEn = "💻 Coding & Architecture",
                subtitleRu = "Kotlin, Compose, алгоритмы и ревью кода",
                subtitleKy = "Kotlin, Compose, алгоритмдер жана код",
                subtitleEn = "Kotlin, Compose, system design & debugging",
                icon = Icons.Default.Code,
                accentColor = AuroraViolet
            )
        )
    }

    val quickPrompts = remember {
        listOf(
            QuickAiPromptChip(
                titleRu = "Напиши код на Kotlin...",
                titleKy = "Kotlin тилинде код жаз...",
                titleEn = "Write Kotlin code...",
                promptRu = "Напиши чистый современный код на Kotlin Jetpack Compose с анимацией Liquid Glass и объясни архитектуру.",
                promptKy = "Kotlin Jetpack Compose аркылуу Liquid Glass анимациясы бар таза код жазып, архитектурасын түшүндүр.",
                promptEn = "Write clean modern Kotlin Jetpack Compose code with a Liquid Glass animation and explain the architecture.",
                icon = Icons.Default.Code,
                accentColor = LiquidCyan
            ),
            QuickAiPromptChip(
                titleRu = "Объясни сложную тему...",
                titleKy = "Татаал теманы түшүндүр...",
                titleEn = "Explain a complex topic...",
                promptRu = "Объясни простыми словами и с яркими аналогиями, как работают трансформеры и квантовые вычисления.",
                promptKy = "Трансформерлер жана кванттык эсептөөлөр кантип иштээрин жөнөкөй сөз менен түшүндүрүп бер.",
                promptEn = "Explain how neural transformers and quantum computing work using simple, vivid analogies.",
                icon = Icons.Default.School,
                accentColor = AuroraViolet
            ),
            QuickAiPromptChip(
                titleRu = "Придумай идею проекта...",
                titleKy = "Долбоор идеясын ойлоп тап...",
                titleEn = "Brainstorm a project idea...",
                promptRu = "Придумай 3 уникальные идеи AI-стартапа на 2026 год со стеком технологий и планом запуска MVP.",
                promptKy = "2026-жылга карата 3 уникалдуу AI-стартап идеясын технологиялык стеги менен ойлоп тап.",
                promptEn = "Brainstorm 3 unique 2026 AI startup ideas with tech stack and MVP launch roadmap.",
                icon = Icons.Default.Lightbulb,
                accentColor = AuroraMagenta
            ),
            QuickAiPromptChip(
                titleRu = "Переведи и улучши текст...",
                titleKy = "Текстти которуп жакшырт...",
                titleEn = "Translate & polish text...",
                promptRu = "Помоги перевести и стилистически улучшить деловое сообщение на английский, русский и кыргызский языки.",
                promptKy = "Ишкердик билдирүүнү англис, орус жана кыргыз тилдерине которуп, стилин жакшыртууга жардам бер.",
                promptEn = "Help me translate and stylistically polish a professional message into English, Russian, and Kyrgyz.",
                icon = Icons.Default.Translate,
                accentColor = OnlineEmerald
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "AiOrbPulse")
    val orbPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloRotation"
    )

    val glassSurfaceBrush = Brush.linearGradient(
        colors = if (scheme.isDark) {
            listOf(
                scheme.surface.copy(alpha = 0.82f),
                scheme.surfaceAlt.copy(alpha = 0.56f)
            )
        } else {
            listOf(
                scheme.surface.copy(alpha = 0.94f),
                scheme.surfaceAlt.copy(alpha = 0.80f)
            )
        }
    )

    val specularCardBorder = Brush.linearGradient(
        colors = listOf(
            scheme.primary.copy(alpha = 0.55f),
            Color.White.copy(alpha = if (scheme.isDark) 0.16f else 0.65f),
            scheme.accent.copy(alpha = 0.55f)
        )
    )

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
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Liquid Glass Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Neongram AI",
                        color = scheme.text,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = tr(
                            lang,
                            "Эксклюзивный нейро-хаб • Liquid Intelligence",
                            "Эксклюзивдүү нейро-хаб • Liquid Intelligence",
                            "Exclusive Neural Hub • Liquid Intelligence"
                        ),
                        color = scheme.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Live Neural Status Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    scheme.primary.copy(alpha = 0.18f),
                                    scheme.accent.copy(alpha = 0.24f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(scheme.primary, scheme.accent)
                            ),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OnlineEmerald)
                    )
                    Text(
                        text = "AI ONLINE",
                        color = scheme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            // ====================================================================
            // 1. FLOATING AI ORB HERO CARD (RoundedCornerShape(32.dp), 1.2.dp cyan-to-violet specular border)
            // ====================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (scheme.isDark) {
                                listOf(
                                    Color(0xFF171C36).copy(alpha = 0.90f),
                                    Color(0xFF1E153A).copy(alpha = 0.84f),
                                    Color(0xFF11162B).copy(alpha = 0.92f)
                                )
                            } else {
                                listOf(
                                    Color(0xFFFFFFFF).copy(alpha = 0.95f),
                                    Color(0xFFF0F4FF).copy(alpha = 0.92f),
                                    Color(0xFFF5EEFF).copy(alpha = 0.90f)
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                LiquidCyan,
                                Color.White.copy(alpha = 0.75f),
                                AuroraViolet,
                                AuroraMagenta
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Animated Glowing AI Core Orb (76.dp circle with radial cyan/violet/magenta glow + AutoAwesome sparkles icon)
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsing aura
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(orbPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        LiquidCyan.copy(alpha = 0.42f),
                                        AuroraViolet.copy(alpha = 0.28f),
                                        AuroraMagenta.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Core 76.dp AI Orb
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        LiquidCyan,
                                        AuroraViolet,
                                        AuroraMagenta
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.9f),
                                        LiquidCyan,
                                        AuroraViolet,
                                        AuroraMagenta,
                                        Color.White.copy(alpha = 0.9f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Neongram AI Orb",
                            tint = Color.White,
                            modifier = Modifier
                                .size(36.dp)
                                .rotate(haloRotation * 0.05f)
                        )
                    }
                }

                // Title "Neongram AI" (28.sp, ExtraBold) + Subtitle
                Text(
                    text = "Neongram AI",
                    color = scheme.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.3).sp
                )

                Text(
                    text = tr(
                        lang,
                        "Ваш персональный нейро-ассистент в реальном времени",
                        "Сиздин реалдуу убакыттагы жеке нейро-жардамчыңыз",
                        "Your personal real-time neural assistant"
                    ),
                    color = scheme.muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // Active Model + Persona Status Badges
                val currentModelObj = aiModels.firstOrNull { it.id == selectedModel } ?: aiModels.first()
                val currentPersonaObj = aiPersonas.firstOrNull { it.id == selectedMode } ?: aiPersonas.first()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(currentModelObj.accentColor.copy(alpha = 0.16f))
                            .border(1.dp, currentModelObj.accentColor.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tr(lang, currentModelObj.titleRu, currentModelObj.titleKy, currentModelObj.titleEn),
                            color = scheme.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(currentPersonaObj.accentColor.copy(alpha = 0.16f))
                            .border(1.dp, currentPersonaObj.accentColor.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tr(lang, currentPersonaObj.titleRu, currentPersonaObj.titleKy, currentPersonaObj.titleEn),
                            color = scheme.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Direct Prompt Capsule Input (NeonInput)
                NeonInput(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = tr(
                        lang,
                        "Задайте любой вопрос или задачу для AI...",
                        "AI үчүн каалаган суроо же тапшырма жазыңыз...",
                        "Ask anything or describe a task for AI..."
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // "Спросить Neongram AI" Gradient Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    LiquidCyan,
                                    AuroraViolet,
                                    AuroraMagenta
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.20f)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable {
                            val promptToSend = promptInput
                            promptInput = ""
                            viewModel.askNeongramAi(state, promptToSend)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = tr(
                                lang,
                                "Спросить Neongram AI",
                                "Neongram AI'ден суроо",
                                "Ask Neongram AI"
                            ),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            // ====================================================================
            // 2. AI MODEL SELECTOR GLASS CARD (RoundedCornerShape(26.dp))
            // ====================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(glassSurfaceBrush)
                    .border(1.dp, specularCardBorder, RoundedCornerShape(26.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Нейросетевая модель", "Нейротармак модели", "Neural Model"),
                        color = scheme.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "2026 Engine",
                        color = scheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    aiModels.forEach { model ->
                        val isSelected = selectedModel == model.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                model.accentColor.copy(alpha = 0.24f),
                                                scheme.accent.copy(alpha = 0.18f)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                scheme.surfaceAlt.copy(alpha = 0.55f),
                                                scheme.surface.copy(alpha = 0.40f)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.4.dp else 1.dp,
                                    brush = if (isSelected) {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                model.accentColor,
                                                Color.White.copy(alpha = 0.65f),
                                                scheme.accent
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                scheme.border,
                                                scheme.border.copy(alpha = 0.5f)
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    viewModel.setAiModel(model.id)
                                    val title = tr(lang, model.titleRu, model.titleKy, model.titleEn)
                                    viewModel.showToast(
                                        tr(
                                            lang,
                                            "Модель: $title",
                                            "Модель: $title",
                                            "Model: $title"
                                        )
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tr(lang, model.titleRu, model.titleKy, model.titleEn),
                                    color = scheme.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = model.badge,
                                    color = if (isSelected) model.accentColor else scheme.muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) model.accentColor else scheme.surface
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.White.copy(alpha = 0.8f) else scheme.border,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF06070C),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====================================================================
            // 3. AI MODE / PERSONA SELECTOR GLASS CARD (RoundedCornerShape(26.dp)) — 2x2 Grid
            // ====================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(glassSurfaceBrush)
                    .border(1.dp, specularCardBorder, RoundedCornerShape(26.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Режим и Персона AI", "AI Режими жана Персона", "AI Mode & Persona"),
                        color = scheme.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "2×2 Persona Grid",
                        color = AuroraViolet,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2x2 Grid of Frosted Glass Persona Cards
                val rows = aiPersonas.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { persona ->
                                val isSelected = selectedMode == persona.id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 124.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        persona.accentColor.copy(alpha = 0.25f),
                                                        scheme.accent.copy(alpha = 0.16f)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        scheme.surfaceAlt.copy(alpha = 0.58f),
                                                        scheme.surface.copy(alpha = 0.42f)
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            brush = if (isSelected) {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        persona.accentColor,
                                                        Color.White.copy(alpha = 0.7f),
                                                        scheme.accent
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        scheme.border,
                                                        scheme.border.copy(alpha = 0.5f)
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(22.dp)
                                        )
                                        .clickable {
                                            viewModel.setAiMode(persona.id)
                                            val title = tr(lang, persona.titleRu, persona.titleKy, persona.titleEn)
                                            viewModel.showToast(
                                                tr(
                                                    lang,
                                                    "Режим: $title",
                                                    "Режим: $title",
                                                    "Mode: $title"
                                                )
                                            )
                                        }
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(persona.accentColor.copy(alpha = 0.22f))
                                                .border(
                                                    1.dp,
                                                    persona.accentColor.copy(alpha = 0.5f),
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = persona.icon,
                                                contentDescription = null,
                                                tint = persona.accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(persona.accentColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF06070C),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = tr(lang, persona.titleRu, persona.titleKy, persona.titleEn),
                                            color = scheme.text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tr(lang, persona.subtitleRu, persona.subtitleKy, persona.subtitleEn),
                                            color = scheme.muted,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ====================================================================
            // 4. QUICK AI PROMPTS GLASS CHIPS
            // ====================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr(
                        lang,
                        "Быстрые сценарии Neongram AI",
                        "Neongram AI ыкчам сценарийлери",
                        "Quick AI Prompts"
                    ),
                    color = scheme.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                quickPrompts.forEach { chip ->
                    val title = tr(lang, chip.titleRu, chip.titleKy, chip.titleEn)
                    val fullPrompt = tr(lang, chip.promptRu, chip.promptKy, chip.promptEn)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(glassSurfaceBrush)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        chip.accentColor.copy(alpha = 0.65f),
                                        Color.White.copy(alpha = if (scheme.isDark) 0.15f else 0.60f),
                                        scheme.accent.copy(alpha = 0.45f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                viewModel.askNeongramAi(state, fullPrompt)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            chip.accentColor.copy(alpha = 0.26f),
                                            scheme.accent.copy(alpha = 0.14f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = chip.accentColor.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(15.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = chip.icon,
                                contentDescription = title,
                                tint = chip.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = title,
                                color = scheme.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = fullPrompt,
                                color = scheme.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(chip.accentColor.copy(alpha = 0.16f))
                                .border(1.dp, chip.accentColor.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = chip.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
