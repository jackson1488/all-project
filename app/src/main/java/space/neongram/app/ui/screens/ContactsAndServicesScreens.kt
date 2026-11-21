package space.neongram.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import space.neongram.app.core.model.CryptoCoinRate
import space.neongram.app.core.model.MusicTrackDto
import space.neongram.app.core.model.UserDto
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
import java.util.Locale
import kotlin.math.abs

private fun resolveFullAvatarUrl(rawUrl: String?, serverBaseUrl: String): String? {
    val clean = rawUrl?.takeIf { it.isNotBlank() } ?: return null
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    val base = serverBaseUrl.trimEnd('/')
    return if (clean.startsWith("/")) "$base$clean" else "$base/$clean"
}

/**
 * Deterministic visual QR matrix rendered on a Jetpack Compose Canvas.
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
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(2.dp, scheme.primary, RoundedCornerShape(18.dp))
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

/**
 * 1:1 with `ContactsScreen.jsx`.
 */
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
    var fabExpanded by remember { mutableStateOf(false) }
    var showCreateGroupModal by remember { mutableStateOf(false) }
    var showAddLoginModal by remember { mutableStateOf(false) }
    var showMyQrModal by remember { mutableStateOf(false) }
    var showScanQrModal by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        PageScaffold(
            title = tr(lang, "Контакты", "Байланыштар", "Contacts"),
            scroll = false
        ) {
            // Top "Создать группу" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                    .clickable { showCreateGroupModal = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "Create Group",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = tr(lang, "Создать группу", "Топ түзүү", "Create group"),
                    color = scheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
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
                            text = tr(lang, "Поиск контактов", "Байланыш издөө", "Search contacts"),
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
                        textStyle = TextStyle(color = scheme.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = scheme.muted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                searchQuery = ""
                                viewModel.searchUsers("")
                            }
                    )
                }
            }

            // Contacts LazyColumn
            if (knownContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tr(lang, "Список контактов пуст", "Байланыштар тизмеси бош", "No contacts yet"),
                            color = scheme.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = tr(
                                lang,
                                "Нажмите + чтобы добавить контакт по @username или QR",
                                "+ басып @username же QR менен кошуңуз",
                                "Tap + to add a contact by @username or QR"
                            ),
                            color = scheme.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(knownContacts, key = { it.id }) { contact ->
                        val statusText = if (contact.isOnline) {
                            tr(lang, "в сети", "тармакта", "online")
                        } else {
                            tr(lang, "не в сети", "тармакта эмес", "offline")
                        }
                        val subtitle = "@${contact.username} • $statusText"
                        ListRow(
                            title = contact.effectiveName,
                            subtitle = subtitle,
                            avatarUrl = resolveFullAvatarUrl(contact.avatarUrl, serverBaseUrl),
                            avatarLabel = contact.effectiveName,
                            showChevron = false,
                            onClick = { viewModel.startDirectChat(contact) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }

        // Speed-Dial Menu + Floating Action Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (fabExpanded) {
                SpeedDialMenuItem(
                    label = tr(lang, "Добавить по логину", "Логин менен кошуу", "Add by username"),
                    icon = Icons.Default.PersonAdd,
                    onClick = {
                        fabExpanded = false
                        showAddLoginModal = true
                    }
                )
                SpeedDialMenuItem(
                    label = tr(lang, "Показать мой QR", "Менин QR кодум", "Show my QR"),
                    icon = Icons.Default.QrCode2,
                    onClick = {
                        fabExpanded = false
                        showMyQrModal = true
                    }
                )
                SpeedDialMenuItem(
                    label = tr(lang, "Сканировать QR", "QR сканерлөө", "Scan QR"),
                    icon = Icons.Default.QrCodeScanner,
                    onClick = {
                        fabExpanded = false
                        showScanQrModal = true
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable { fabExpanded = !fabExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Contacts Actions",
                    tint = if (scheme.isDark) Color(0xFF06070C) else Color.White,
                    modifier = Modifier.size(26.dp)
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Создать группу", "Топ түзүү", "Create Group"),
                        color = scheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = scheme.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showCreateGroupModal = false }
                    )
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) scheme.surfaceAlt else scheme.bgAlt)
                                .border(
                                    1.dp,
                                    if (isSelected) scheme.primary else scheme.border,
                                    RoundedCornerShape(12.dp)
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
                                    sizeDp = 34.dp,
                                    cornerRadiusDp = 10.dp
                                )
                                Column {
                                    Text(
                                        text = contact.effectiveName,
                                        color = scheme.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Добавить по логину", "Логин менен кошуу", "Add by username"),
                        color = scheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = scheme.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showAddLoginModal = false }
                    )
                }

                NeonInput(
                    value = loginInput,
                    onValueChange = { loginInput = it },
                    placeholder = "@username"
                )

                PrimaryButton(
                    label = tr(lang, "Отправить", "Жөнөтүү", "Send"),
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

    // 3. My Contact QR Modal
    if (showMyQrModal) {
        val myUsername = state.currentUser?.username?.ifBlank { state.session.username } ?: state.session.username
        val qrPayload = "neongram://contact/$myUsername"
        val clipboard = LocalClipboardManager.current

        Dialog(onDismissRequest = { showMyQrModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr(lang, "Мой QR-код", "Менин QR-кодум", "My Contact QR"),
                    color = scheme.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

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
                    }
                )
            }
        }
    }

    // 4. Scan Contact QR Modal
    if (showScanQrModal) {
        var qrCodeInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showScanQrModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Сканировать QR", "QR сканерлөө", "Scan Contact QR"),
                        color = scheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = scheme.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showScanQrModal = false }
                    )
                }

                Text(
                    text = tr(
                        lang,
                        "Вставьте ссылку neongram://contact/username или логин:",
                        "neongram://contact/username шилтемесин же логин жазыңыз:",
                        "Paste neongram://contact/username link or login:"
                    ),
                    color = scheme.muted,
                    fontSize = 13.sp
                )

                NeonInput(
                    value = qrCodeInput,
                    onValueChange = { qrCodeInput = it },
                    placeholder = "neongram://contact/username"
                )

                PrimaryButton(
                    label = tr(lang, "Добавить контакт", "Байланыш кошуу", "Add contact"),
                    onClick = {
                        val cleaned = qrCodeInput
                            .trim()
                            .removePrefix("neongram://contact/")
                            .removePrefix("@")
                            .trim()
                        if (cleaned.isNotBlank()) {
                            viewModel.addContactByLogin(cleaned)
                            showScanQrModal = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SpeedDialMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class ServiceCardItemData(
    val id: String,
    val title: String,
    val description: String,
    val monogram: String,
    val accentColor: Color,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * 1:1 with `ServicesScreen.jsx` featuring the 8 Service Cards.
 */
@Composable
fun ServicesTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    val cards = listOf(
        ServiceCardItemData(
            id = "weather",
            title = tr(lang, "Погода", "Аба ырайы", "Weather"),
            description = tr(lang, "Прогноз погоды по городам, ветер и влажность", "Шаарлар боюнча аба ырайы", "Live forecast, wind & humidity by city"),
            monogram = "WTH",
            accentColor = scheme.primary,
            icon = Icons.Default.Cloud,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_WEATHER) }
        ),
        ServiceCardItemData(
            id = "ai",
            title = "Neongram AI",
            description = tr(lang, "Умный ассистент в чатах для быстрых ответов", "Чаттардагы акылдуу жардамчы", "Smart chat assistant for quick answers"),
            monogram = "AI",
            accentColor = scheme.accent,
            icon = Icons.Default.AutoAwesome,
            onClick = { viewModel.openAiAssistantChat() }
        ),
        ServiceCardItemData(
            id = "browser",
            title = tr(lang, "Браузер", "Браузер", "Browser"),
            description = tr(lang, "Встроенный NWEB браузер с быстрым поиском", "Ички NWEB браузери", "Built-in NWEB browser with multi-engine search"),
            monogram = "WEB",
            accentColor = Color(0xFF5DA9FF),
            icon = Icons.Default.Language,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_BROWSER) }
        ),
        ServiceCardItemData(
            id = "currency",
            title = tr(lang, "Крипта и валюты", "Крипто жана валюта", "Crypto & Currency"),
            description = tr(lang, "Курсы BTC, ETH, TON, SOL и графики за 24ч", "BTC, ETH, TON, SOL курстары", "Live BTC, ETH, TON, SOL rates & 24h charts"),
            monogram = "CRP",
            accentColor = Color(0xFF4CAF50),
            icon = Icons.Default.CurrencyBitcoin,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_CURRENCY) }
        ),
        ServiceCardItemData(
            id = "music",
            title = tr(lang, "Музыка", "Музыка", "Music"),
            description = tr(lang, "Поиск треков, плейлисты и фоновый плеер", "Тректерди издөө жана плеер", "Search tracks, favorites & mini-player"),
            monogram = "MUS",
            accentColor = Color(0xFF8BC34A),
            icon = Icons.Default.MusicNote,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_MUSIC) }
        ),
        ServiceCardItemData(
            id = "games",
            title = tr(lang, "Игры", "Оюндар", "Games"),
            description = tr(lang, "Шахматы 8x8 и ретро-аркады прямо в мессенджере", "Шахмат 8x8 жана DOOM", "Interactive 8x8 Chess & retro DOOM"),
            monogram = "GMS",
            accentColor = Color(0xFF9C6DFF),
            icon = Icons.Default.SportsEsports,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_GAMES) }
        ),
        ServiceCardItemData(
            id = "wallet",
            title = tr(lang, "TON Кошелек", "TON Капчык", "TON Wallet"),
            description = tr(lang, "Подключение TON Connect, баланс и переводы", "TON Connect жана баланс", "TON Connect sessions, balance & transfers"),
            monogram = "TON",
            accentColor = Color(0xFFFFB300),
            icon = Icons.Default.AccountBalanceWallet,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_WALLET) }
        ),
        ServiceCardItemData(
            id = "bank",
            title = "Neongram Bank",
            description = tr(lang, "Виртуальная карта KGS и мгновенные переводы", "Виртуалдык KGS картасы", "Virtual KGS neon card & instant transfers"),
            monogram = "PAY",
            accentColor = Color(0xFF21B3A1),
            icon = Icons.Default.AccountBalance,
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SERVICE_BANK) }
        )
    )

    PageScaffold(
        title = tr(lang, "Сервисы", "Кызматтар", "Services")
    ) {
        cards.forEach { card ->
            ServiceCardRow(item = card)
        }
    }
}

@Composable
private fun ServiceCardRow(item: ServiceCardItemData) {
    val scheme = LocalNeonScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
            .clickable(onClick = item.onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 56.dp ServiceLogo with gradient, inner 34.dp icon box, and bottom-right monogram badge
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                item.accentColor.copy(alpha = 0.28f),
                                scheme.surfaceAlt
                            )
                        )
                    )
                    .border(1.dp, item.accentColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(item.accentColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.bg)
                    .border(1.dp, item.accentColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.monogram,
                    color = item.accentColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.description,
                color = scheme.muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(scheme.surfaceAlt)
                .border(1.dp, scheme.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.text,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================================
// 1. WEATHER SERVICE SCREEN (`WeatherScreen.jsx`)
// ============================================================================

@Composable
fun WeatherServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val weather = state.weatherData
    var cityQuery by remember { mutableStateOf("") }

    PageScaffold(
        title = tr(lang, "Погода", "Аба ырайы", "Weather"),
        onBack = { viewModel.navigateBack() },
        rightContent = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(999.dp))
                    .clickable {
                        viewModel.loadWeather(weather.cityName, weather.latitude, weather.longitude)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    ) {
        // City Search Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonInput(
                value = cityQuery,
                onValueChange = { cityQuery = it },
                placeholder = tr(lang, "Введите город (например, Bishkek)", "Шаарды жазыңыз", "Search city (e.g. Bishkek)"),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary)
                    .clickable {
                        if (cityQuery.isNotBlank()) {
                            viewModel.searchAndLoadWeatherCity(cityQuery.trim())
                        }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(lang, "Найти", "Издөө", "Find"),
                    color = if (scheme.isDark) Color(0xFF06070C) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // WeatherNowCard with gradient & 54.sp temperature
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.25f),
                            scheme.accent.copy(alpha = 0.22f),
                            scheme.surface
                        )
                    )
                )
                .border(1.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = weather.cityName,
                        color = scheme.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = weatherCodeLabel(weather.weatherCode, lang),
                        color = scheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Text(
                text = String.format(Locale.US, "%.1f°C", weather.tempCurrent),
                color = scheme.text,
                fontSize = 54.sp,
                fontWeight = FontWeight.Black
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WeatherStatPill(
                    label = tr(lang, "Ощущается", "Сезилет", "Feels like"),
                    value = String.format(Locale.US, "%.1f°C", weather.feelsLike)
                )
                WeatherStatPill(
                    label = tr(lang, "Ветер", "Шамал", "Wind"),
                    value = String.format(Locale.US, "%.1f м/с", weather.windSpeed)
                )
                WeatherStatPill(
                    label = tr(lang, "Влажность", "Нымдуулук", "Humidity"),
                    value = "${weather.humidity}%"
                )
            }
        }

        // 24-hour horizontal forecast strip
        Text(
            text = tr(lang, "Почасовой прогноз (24ч)", "Сааттык божомол (24с)", "24-Hour Forecast"),
            color = scheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val hourlyList = weather.hourlyTemps.ifEmpty {
                listOf(
                    "09:00" to weather.tempCurrent,
                    "12:00" to (weather.tempCurrent + 2.0),
                    "15:00" to (weather.tempCurrent + 3.0),
                    "18:00" to (weather.tempCurrent + 1.0),
                    "21:00" to (weather.tempCurrent - 1.5),
                    "00:00" to (weather.tempCurrent - 3.0)
                )
            }
            hourlyList.forEach { (hour, temp) ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surface)
                        .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = hour.takeLast(5),
                        color = scheme.muted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f°", temp),
                        color = scheme.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 7-day forecast list
        Text(
            text = tr(lang, "Прогноз на 7 дней", "7 күндүк божомол", "7-Day Forecast"),
            color = scheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (weather.dailyForecast.isEmpty()) {
                Text(
                    text = tr(lang, "Загрузка прогноза...", "Жүктөлүүдө...", "Loading forecast..."),
                    color = scheme.muted,
                    fontSize = 13.sp
                )
            } else {
                weather.dailyForecast.forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day.date,
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = weatherCodeLabel(day.weatherCode, lang),
                            color = scheme.muted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f° / %.0f°", day.tempMax, day.tempMin),
                            color = scheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherStatPill(label: String, value: String) {
    val scheme = LocalNeonScheme.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.bg.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = scheme.muted, fontSize = 11.sp)
        Text(text = value, color = scheme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun weatherCodeLabel(code: Int, lang: String): String {
    return when (code) {
        0 -> tr(lang, "Ясно", "Ачык", "Clear sky")
        1, 2, 3 -> tr(lang, "Переменная облачность", "Ала булуттуу", "Partly cloudy")
        45, 48 -> tr(lang, "Туман", "Туман", "Fog")
        51, 53, 55, 61, 63, 65 -> tr(lang, "Дождь", "Жамгыр", "Rain")
        71, 73, 75 -> tr(lang, "Снег", "Кар", "Snow")
        95, 96, 99 -> tr(lang, "Гроза", "Чагылган", "Thunderstorm")
        else -> tr(lang, "Облачно", "Булуттуу", "Cloudy")
    }
}

// ============================================================================
// 2. CURRENCY & CRYPTO SERVICE SCREEN (`CurrencyScreen.jsx`)
// ============================================================================

@Composable
fun CurrencyServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    var query by remember { mutableStateOf("") }
    var selectedCoin by remember { mutableStateOf<CryptoCoinRate?>(null) }

    val defaultCoins = remember {
        listOf(
            CryptoCoinRate("bitcoin", "BTC", "Bitcoin", 89420.50, 3.42),
            CryptoCoinRate("ethereum", "ETH", "Ethereum", 3120.80, 1.85),
            CryptoCoinRate("binancecoin", "BNB", "BNB", 645.10, -0.74),
            CryptoCoinRate("solana", "SOL", "Solana", 192.40, 5.18),
            CryptoCoinRate("the-open-network", "TON", "Toncoin", 5.84, 4.12),
            CryptoCoinRate("ripple", "XRP", "XRP", 1.48, -1.20),
            CryptoCoinRate("dogecoin", "DOGE", "Dogecoin", 0.37, 6.40),
            CryptoCoinRate("cardano", "ADA", "Cardano", 0.98, 2.15)
        )
    }

    val allCoins = state.cryptoRates.ifEmpty { defaultCoins }
    val popularSymbols = setOf("BTC", "ETH", "BNB", "SOL", "TON", "XRP", "DOGE", "ADA")
    val filteredCoins = remember(allCoins, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allCoins else allCoins.filter {
            it.symbol.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
    }
    val popularCoins = filteredCoins.filter { it.symbol.uppercase() in popularSymbols }.take(8)

    PageScaffold(
        title = tr(lang, "Крипта и валюты", "Крипто жана валюта", "Crypto & Rates"),
        onBack = { viewModel.navigateBack() },
        rightContent = {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = scheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { viewModel.loadCryptoRates() }
            )
        }
    ) {
        NeonInput(
            value = query,
            onValueChange = { query = it },
            placeholder = tr(lang, "Поиск монеты (BTC, TON, SOL...)", "Монета издөө...", "Search coin (BTC, TON, SOL...)")
        )

        Text(
            text = tr(lang, "Популярные", "Популярдуу", "Popular"),
            color = scheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        // 2-Column Popular Grid
        popularCoins.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { coin ->
                    val isPositive = coin.change24hPercent >= 0.0
                    val changeColor = if (isPositive) Color(0xFF31C06A) else Color(0xFFE74C5B)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surface)
                            .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                            .clickable { selectedCoin = coin }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = coin.symbol,
                                color = scheme.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s%.2f%%",
                                    if (isPositive) "+" else "",
                                    coin.change24hPercent
                                ),
                                color = changeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = coin.name,
                            color = scheme.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatUsdPrice(coin.currentPriceUsd),
                            color = scheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Text(
            text = tr(lang, "Все курсы", "Бардык курстар", "All Rates"),
            color = scheme.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        filteredCoins.forEach { coin ->
            val isPositive = coin.change24hPercent >= 0.0
            val changeColor = if (isPositive) Color(0xFF31C06A) else Color(0xFFE74C5B)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                    .clickable { selectedCoin = coin }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = coin.symbol.take(3),
                            color = scheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column {
                        Text(
                            text = coin.name,
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = coin.symbol,
                            color = scheme.muted,
                            fontSize = 12.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatUsdPrice(coin.currentPriceUsd),
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s%.2f%%",
                            if (isPositive) "+" else "",
                            coin.change24hPercent
                        ),
                        color = changeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Interactive Canvas Price Chart Modal
    selectedCoin?.let { coin ->
        val isPositive = coin.change24hPercent >= 0.0
        val lineColor = if (isPositive) Color(0xFF31C06A) else Color(0xFFE74C5B)
        var timeframe by remember { mutableStateOf("24H") }

        val points = remember(coin, timeframe) {
            val base = coin.currentPriceUsd.toFloat().coerceAtLeast(0.01f)
            val seed = coin.symbol.hashCode() + timeframe.hashCode()
            List(16) { idx ->
                val wave = kotlin.math.sin((idx + seed % 7) * 0.75f) * 0.03f
                val trend = (idx - 8) * (coin.change24hPercent.toFloat() / 100f) * 0.08f
                base * (1f + wave + trend)
            }
        }

        Dialog(onDismissRequest = { selectedCoin = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${coin.name} (${coin.symbol})",
                            color = scheme.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatUsdPrice(coin.currentPriceUsd),
                            color = scheme.primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = scheme.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { selectedCoin = null }
                    )
                }

                // Timeframe selector pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("24H", "7D", "30D", "1Y").forEach { tf ->
                        val active = timeframe == tf
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) scheme.primary else scheme.surfaceAlt)
                                .clickable { timeframe = tf }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tf,
                                color = if (active) Color(0xFF06070C) else scheme.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Price Line Chart Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.bgAlt)
                        .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val minV = points.minOrNull() ?: 0f
                        val maxV = points.maxOrNull() ?: 1f
                        val range = (maxV - minV).coerceAtLeast(0.0001f)
                        val path = Path()
                        points.forEachIndexed { i, v ->
                            val x = (i.toFloat() / (points.size - 1)) * size.width
                            val y = size.height - ((v - minV) / range) * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                PrimaryButton(
                    label = tr(lang, "Закрыть", "Жабуу", "Close"),
                    onClick = { selectedCoin = null }
                )
            }
        }
    }
}

private fun formatUsdPrice(price: Double): String {
    return if (price >= 1.0) {
        String.format(Locale.US, "$%,.2f", price)
    } else {
        String.format(Locale.US, "$%.4f", price)
    }
}

// ============================================================================
// 3. MUSIC SERVICE SCREEN (`MusicScreen.jsx`)
// ============================================================================

@Composable
fun MusicServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("Phonk Neon") }
    var activeTrack by remember { mutableStateOf<MusicTrackDto?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    val localFavorites = remember { mutableStateListOf<MusicTrackDto>() }

    val fallbackTracks = remember {
        listOf(
            MusicTrackDto("neon_1", "CYBERPUNK OVERDRIVE", "Neongram Synthwave", null, "3:24"),
            MusicTrackDto("neon_2", "BISHKEK NIGHT DRIFT", "Kyrgyz Phonk Collective", null, "2:48"),
            MusicTrackDto("neon_3", "NEON HORIZON 2099", "Astra Pulse", null, "4:05"),
            MusicTrackDto("neon_4", "MATRIX PROTOCOL", "Darkwave Core", null, "3:12")
        )
    }

    val tracksToShow = if (selectedTab == 0) {
        state.musicTracks.ifEmpty { fallbackTracks }
    } else {
        (state.musicFavorites + localFavorites).distinctBy { it.videoId }.ifEmpty { fallbackTracks.take(2) }
    }

    PageScaffold(
        title = tr(lang, "Музыка", "Музыка", "Music"),
        onBack = { viewModel.navigateBack() }
    ) {
        // 2 Tabs: Поиск / Библиотека
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
                tr(lang, "Поиск", "Издөө", "Search"),
                tr(lang, "Библиотека", "Китепкана", "Library")
            ).forEachIndexed { idx, title ->
                val active = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) scheme.primary else Color.Transparent)
                        .clickable { selectedTab = idx },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (active) Color(0xFF06070C) else scheme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selectedTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonInput(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = tr(lang, "Поиск треков...", "Трек издөө...", "Search tracks..."),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primary)
                        .clickable { viewModel.loadMusic(searchQuery) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF06070C)
                    )
                }
            }
        }

        // Track Cards
        tracksToShow.forEach { track ->
            val isCurrent = activeTrack?.videoId == track.videoId
            val isFav = localFavorites.any { it.videoId == track.videoId } ||
                state.musicFavorites.any { it.videoId == track.videoId }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isCurrent) scheme.surfaceAlt else scheme.surface)
                    .border(
                        1.dp,
                        if (isCurrent) scheme.primary else scheme.border,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        if (isCurrent) {
                            isPlaying = !isPlaying
                        } else {
                            activeTrack = track
                            isPlaying = true
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 58.dp thumbnail
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(scheme.accent, scheme.primary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!track.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.title,
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.channelTitle.ifBlank { "Neongram Audio" },
                        color = scheme.muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!track.durationText.isNullOrBlank()) {
                        Text(
                            text = track.durationText,
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Icon(
                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) scheme.danger else scheme.muted,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            if (localFavorites.any { it.videoId == track.videoId }) {
                                localFavorites.removeAll { it.videoId == track.videoId }
                            } else {
                                localFavorites.add(track)
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF06070C),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Mini-Player Bar
        activeTrack?.let { current ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(scheme.surfaceAlt, scheme.surface)
                        )
                    )
                    .border(1.dp, scheme.primary, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr(lang, "Сейчас играет", "Азыр ойноп жатат", "Now Playing"),
                        color = scheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = current.title,
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.channelTitle,
                        color = scheme.muted,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .clickable { isPlaying = !isPlaying },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Play",
                            tint = Color(0xFF06070C)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(scheme.surface)
                            .border(1.dp, scheme.border, CircleShape)
                            .clickable {
                                isPlaying = false
                                activeTrack = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = scheme.muted
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. GAMES SERVICE SCREEN (`GamesScreen.jsx`)
// ============================================================================

@Composable
fun GamesServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    var showChessModal by remember { mutableStateOf(false) }
    var showDoomModal by remember { mutableStateOf(false) }

    PageScaffold(
        title = tr(lang, "Игры", "Оюндар", "Games"),
        onBack = { viewModel.navigateBack() }
    ) {
        // 1. Шахматы Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF9C6DFF).copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "♞", fontSize = 26.sp, color = Color(0xFF9C6DFF))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr(lang, "Шахматы", "Шахмат", "Chess 8x8"),
                        color = scheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            lang,
                            "Интерактивная доска 8x8 для партии с другом или тренировок",
                            "Интерактивдүү 8x8 шахмат тактасы",
                            "Interactive 8x8 chessboard right inside Neongram"
                        ),
                        color = scheme.muted,
                        fontSize = 13.sp
                    )
                }
            }
            PrimaryButton(
                label = tr(lang, "Играть в Шахматы", "Шахмат ойноо", "Play Chess"),
                onClick = { showChessModal = true }
            )
        }

        // 2. DOOM Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.danger.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "DOOM",
                        tint = scheme.danger,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOOM",
                        color = scheme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = tr(
                            lang,
                            "Классический ретро-шутер в киберпанк оболочке Neongram",
                            "Классикалык ретро-шутер",
                            "Classic retro shooter inside Neongram arcade"
                        ),
                        color = scheme.muted,
                        fontSize = 13.sp
                    )
                }
            }
            PrimaryButton(
                label = tr(lang, "Запустить DOOM", "DOOM иштетүү", "Launch DOOM"),
                onClick = { showDoomModal = true }
            )
        }
    }

    // Playable Interactive 8x8 Chessboard Modal
    if (showChessModal) {
        val initialBoard = remember {
            listOf(
                listOf("♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"),
                listOf("♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟"),
                listOf("", "", "", "", "", "", "", ""),
                listOf("", "", "", "", "", "", "", ""),
                listOf("", "", "", "", "", "", "", ""),
                listOf("", "", "", "", "", "", "", ""),
                listOf("♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙"),
                listOf("♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖")
            )
        }
        var board by remember { mutableStateOf(initialBoard) }
        var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var whiteTurn by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { showChessModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (whiteTurn) {
                            tr(lang, "Шахматы • Ход белых", "Шахмат • Актардын жүрүшү", "Chess • White's turn")
                        } else {
                            tr(lang, "Шахматы • Ход черных", "Шахмат • Каралардын жүрүшү", "Chess • Black's turn")
                        },
                        color = scheme.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = scheme.muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showChessModal = false }
                    )
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, scheme.primary, RoundedCornerShape(12.dp))
                ) {
                    for (r in 0 until 8) {
                        Row {
                            for (c in 0 until 8) {
                                val isDarkSquare = (r + c) % 2 == 1
                                val isSelected = selectedCell == (r to c)
                                val bg = when {
                                    isSelected -> scheme.primary.copy(alpha = 0.45f)
                                    isDarkSquare -> scheme.surfaceAlt
                                    else -> scheme.bgAlt
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(bg)
                                        .clickable {
                                            val currentSel = selectedCell
                                            val pieceAtTarget = board[r][c]
                                            if (currentSel == null) {
                                                if (pieceAtTarget.isNotEmpty()) {
                                                    selectedCell = r to c
                                                }
                                            } else if (currentSel == (r to c)) {
                                                selectedCell = null
                                            } else {
                                                val movingPiece = board[currentSel.first][currentSel.second]
                                                val mutable = board.map { it.toMutableList() }.toMutableList()
                                                mutable[currentSel.first][currentSel.second] = ""
                                                mutable[r][c] = movingPiece
                                                board = mutable
                                                selectedCell = null
                                                whiteTurn = !whiteTurn
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = board[r][c],
                                        fontSize = 20.sp,
                                        color = scheme.text
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrimaryButton(
                        label = tr(lang, "Сбросить партию", "Кайра баштоо", "Reset Board"),
                        onClick = {
                            board = initialBoard
                            selectedCell = null
                            whiteTurn = true
                        }
                    )
                }
            }
        }
    }

    if (showDoomModal) {
        Dialog(onDismissRequest = { showDoomModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.danger, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "NEON DOOM ENGINE",
                    color = scheme.danger,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090408))
                        .border(1.dp, scheme.danger.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WAD READY • 60 FPS\nTAP FIRE TO START",
                        color = scheme.danger,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                PrimaryButton(
                    label = tr(lang, "Закрыть", "Жабуу", "Close"),
                    onClick = { showDoomModal = false }
                )
            }
        }
    }
}

// ============================================================================
// 5. TON WALLET SERVICE SCREEN (`WalletScreen.jsx`)
// ============================================================================

@Composable
fun WalletServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    var showConnectModal by remember { mutableStateOf(false) }
    var customAddressInput by remember { mutableStateOf("") }
    var activeBalanceTon by remember { mutableStateOf("0.00") }
    var localWalletAddress by remember {
        mutableStateOf(
            state.walletSessions.firstOrNull()?.walletAddress ?: "UQNeongramTonWallet777CyberSpace"
        )
    }

    PageScaffold(
        title = tr(lang, "TON Кошелек", "TON Капчык", "TON Wallet"),
        onBack = { viewModel.navigateBack() }
    ) {
        // TON Balance Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0098EA).copy(alpha = 0.30f),
                            scheme.surface
                        )
                    )
                )
                .border(1.dp, Color(0xFF0098EA).copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "TON MAINNET",
                color = Color(0xFF0098EA),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Text(
                text = "$activeBalanceTon TON",
                color = scheme.text,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = localWalletAddress,
                color = scheme.muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 4 Action Circles: Отправить, Получить, Подписать, Отключить
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WalletActionCircle(
                    label = tr(lang, "Отправить", "Жөнөтүү", "Send"),
                    icon = Icons.AutoMirrored.Filled.CallMade,
                    onClick = { viewModel.showToast(tr(lang, "Отправка TON", "TON жөнөтүү", "Send TON")) }
                )
                WalletActionCircle(
                    label = tr(lang, "Получить", "Алуу", "Receive"),
                    icon = Icons.AutoMirrored.Filled.CallReceived,
                    onClick = { showConnectModal = true }
                )
                WalletActionCircle(
                    label = tr(lang, "Подписать", "Кол коюу", "Sign"),
                    icon = Icons.Default.Edit,
                    onClick = { viewModel.showToast(tr(lang, "Payload подписан", "Кол коюлду", "Payload signed")) }
                )
                WalletActionCircle(
                    label = tr(lang, "Отключить", "Өчүрүү", "Disconnect"),
                    icon = Icons.Default.LinkOff,
                    onClick = {
                        localWalletAddress = "Не подключен"
                        viewModel.showToast(tr(lang, "Кошелек отключен", "Капчык өчүрүлдү", "Wallet disconnected"))
                    }
                )
            }
        }

        PrimaryButton(
            label = tr(lang, "Подключить TON Кошелек", "TON Капчыкты туташтыруу", "Connect TON Wallet"),
            onClick = { showConnectModal = true }
        )

        // Linked Wallets Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tr(lang, "Привязанные кошельки", "Байланган капчыктар", "Linked Wallets"),
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (state.walletSessions.isEmpty()) {
                Text(
                    text = tr(
                        lang,
                        "Активных сессий TON Connect пока нет",
                        "Активдүү TON Connect сессиялары жок",
                        "No active TON Connect sessions"
                    ),
                    color = scheme.muted,
                    fontSize = 13.sp
                )
            } else {
                state.walletSessions.forEach { ws ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceAlt)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ws.walletName ?: ws.walletAppName ?: "TON Wallet",
                                color = scheme.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ws.walletAddress,
                                color = scheme.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = ws.network.uppercase(),
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showConnectModal) {
        Dialog(onDismissRequest = { showConnectModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "TON Connect 2.0",
                    color = scheme.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = tr(
                        lang,
                        "Введите адрес вашего кошелька Tonkeeper / MyTonWallet:",
                        "Tonkeeper / MyTonWallet дарегин жазыңыз:",
                        "Enter your Tonkeeper / MyTonWallet address:"
                    ),
                    color = scheme.muted,
                    fontSize = 13.sp
                )
                NeonInput(
                    value = customAddressInput,
                    onValueChange = { customAddressInput = it },
                    placeholder = "UQ..."
                )
                PrimaryButton(
                    label = tr(lang, "Подключить", "Туташтыруу", "Connect"),
                    onClick = {
                        if (customAddressInput.isNotBlank()) {
                            localWalletAddress = customAddressInput.trim()
                        }
                        showConnectModal = false
                        viewModel.showToast(tr(lang, "TON кошелек подключен", "TON капчык туташты", "TON Wallet connected"))
                    }
                )
            }
        }
    }
}

@Composable
private fun WalletActionCircle(
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
                .size(46.dp)
                .clip(CircleShape)
                .background(scheme.surfaceAlt)
                .border(1.dp, scheme.border, CircleShape),
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
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================================
// 6. NEONGRAM BANK SERVICE SCREEN (`Bank` Mini-App)
// ============================================================================

private data class BankTransactionEntry(
    val id: String,
    val recipient: String,
    val amountKgs: Double,
    val timestamp: String,
    val isIncoming: Boolean
)

@Composable
fun BankServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    var balanceKgs by remember { mutableStateOf(25400.0) }
    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    val transactions = remember {
        mutableStateListOf(
            BankTransactionEntry("tx_1", "@neo_merchant", 1250.0, "Сегодня, 14:20", false),
            BankTransactionEntry("tx_2", "Пополнение Neongram Pay", 10000.0, "Вчера, 19:05", true),
            BankTransactionEntry("tx_3", "@aibek_kg", 600.0, "18 Ноя, 11:30", false)
        )
    }

    PageScaffold(
        title = "Neongram Bank",
        onBack = { viewModel.navigateBack() }
    ) {
        // Virtual Neon Card (KGS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF21B3A1),
                            scheme.accent,
                            scheme.surfaceAlt
                        )
                    )
                )
                .border(1.dp, scheme.primary.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEONGRAM PAY • VIRTUAL",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "KGS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = String.format(Locale.US, "%,.2f KGS", balanceKgs),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "9417  8800  4210  ${state.session.userId.takeLast(4).padStart(4, '7')}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = (state.currentUser?.effectiveName ?: state.session.displayName).ifBlank { "NEONGRAM USER" }.uppercase(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "12/29",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Transfer Form
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
                text = tr(lang, "Перевод по @username или номеру карты", "@username же карта номерине которуу", "Transfer by @username or card number"),
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            NeonInput(
                value = recipientInput,
                onValueChange = { recipientInput = it },
                placeholder = tr(lang, "@username или 16 цифр карты", "@username же карта номери", "@username or 16-digit card")
            )
            NeonInput(
                value = amountInput,
                onValueChange = { amountInput = it },
                placeholder = tr(lang, "Сумма (KGS)", "Сумма (KGS)", "Amount (KGS)")
            )
            PrimaryButton(
                label = tr(lang, "Перевести", "Которуу", "Send Transfer"),
                onClick = {
                    val amount = amountInput.trim().toDoubleOrNull()
                    if (recipientInput.isBlank() || amount == null || amount <= 0.0) {
                        viewModel.showToast(tr(lang, "Проверьте получателя и сумму", "Алуучуну жана сумманы текшериңиз", "Check recipient and amount"))
                    } else if (amount > balanceKgs) {
                        viewModel.showToast(tr(lang, "Недостаточно средств", "Каражат жетишсиз", "Insufficient balance"))
                    } else {
                        balanceKgs -= amount
                        transactions.add(
                            0,
                            BankTransactionEntry(
                                id = "tx_${System.currentTimeMillis()}",
                                recipient = recipientInput.trim(),
                                amountKgs = amount,
                                timestamp = tr(lang, "Только что", "Жаңы эле", "Just now"),
                                isIncoming = false
                            )
                        )
                        recipientInput = ""
                        amountInput = ""
                        viewModel.showToast(tr(lang, "Перевод выполнен", "Которуу аткарылды", "Transfer completed"))
                    }
                }
            )
        }

        // Transaction History
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tr(lang, "История операций", "Операциялар тарыхы", "Transaction History"),
                color = scheme.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            transactions.forEach { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceAlt)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tx.recipient,
                            color = scheme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = tx.timestamp,
                            color = scheme.muted,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s%,.0f KGS",
                            if (tx.isIncoming) "+" else "-",
                            tx.amountKgs
                        ),
                        color = if (tx.isIncoming) Color(0xFF31C06A) else scheme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. BROWSER SERVICE SCREEN (`BrowserScreen.jsx`)
// ============================================================================

private data class SearchEngineItem(
    val id: String,
    val name: String,
    val searchPrefix: String
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserServiceScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    val engines = remember {
        listOf(
            SearchEngineItem("google", "Google", "https://www.google.com/search?q="),
            SearchEngineItem("ddg", "DuckDuckGo", "https://duckduckgo.com/?q="),
            SearchEngineItem("bing", "Bing", "https://www.bing.com/search?q="),
            SearchEngineItem("yandex", "Yandex", "https://yandex.ru/search/?text="),
            SearchEngineItem("brave", "Brave", "https://search.brave.com/search?q=")
        )
    }

    var selectedEngine by remember { mutableStateOf(engines.first()) }
    var addressInput by remember { mutableStateOf("neongram://home") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var tabCount by remember { mutableIntStateOf(1) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun openQueryOrUrl(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "neongram://home") {
            currentUrl = null
            addressInput = "neongram://home"
            return
        }
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> selectedEngine.searchPrefix + android.net.Uri.encode(trimmed)
        }
        addressInput = target
        currentUrl = target
    }

    PageScaffold(
        title = tr(lang, "Браузер", "Браузер", "Browser"),
        onBack = { viewModel.navigateBack() },
        scroll = false
    ) {
        // Top Address Bar Pill (`neongram://home`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    singleLine = true,
                    textStyle = TextStyle(color = scheme.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(scheme.primary)
                    .clickable { openQueryOrUrl(addressInput) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "GO",
                    color = Color(0xFF06070C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Main Browser Content Area (NWEB Hero Home or Android WebView)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (currentUrl == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // NWEB Hero Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF5DA9FF).copy(alpha = 0.25f),
                                        scheme.accent.copy(alpha = 0.22f),
                                        scheme.surface
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF5DA9FF).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "NWEB • CYBER BROWSER",
                            color = Color(0xFF5DA9FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = tr(
                                lang,
                                "Приватный поиск внутри Neongram",
                                "Neongram ичиндеги купуя издөө",
                                "Private Web & Search inside Neongram"
                            ),
                            color = scheme.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Search Engine Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            engines.forEach { engine ->
                                val active = selectedEngine.id == engine.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) scheme.primary else scheme.surfaceAlt)
                                        .border(1.dp, if (active) scheme.primary else scheme.border, RoundedCornerShape(10.dp))
                                        .clickable { selectedEngine = engine }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = engine.name,
                                        color = if (active) Color(0xFF06070C) else scheme.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Quick Links (YouTube, GitHub)
                    Text(
                        text = tr(lang, "Быстрые ссылки", "Тез шилтемелер", "Quick Links"),
                        color = scheme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickLinkCard(
                            title = "YouTube",
                            subtitle = "youtube.com",
                            accentColor = Color(0xFFFF4D6D),
                            modifier = Modifier.weight(1f),
                            onClick = { openQueryOrUrl("https://m.youtube.com") }
                        )
                        QuickLinkCard(
                            title = "GitHub",
                            subtitle = "github.com",
                            accentColor = Color(0xFF5DA9FF),
                            modifier = Modifier.weight(1f),
                            onClick = { openQueryOrUrl("https://github.com") }
                        )
                    }
                }
            } else {
                val urlToLoad = currentUrl!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, scheme.border, RoundedCornerShape(18.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (!url.isNullOrBlank()) {
                                            addressInput = url
                                        }
                                    }
                                }
                                loadUrl(urlToLoad)
                                webViewRef = this
                            }
                        },
                        update = { view ->
                            if (view.url != urlToLoad) {
                                view.loadUrl(urlToLoad)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 6-Button Bottom Navigation Bar (Back, Forward, Refresh, Tabs, New Tab, Menu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserNavButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        currentUrl = null
                        addressInput = "neongram://home"
                    }
                }
            )
            BrowserNavButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoForward()) {
                        wv.goForward()
                    }
                }
            )
            BrowserNavButton(
                icon = Icons.Default.Refresh,
                onClick = { webViewRef?.reload() }
            )
            // Tabs button with count badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceAlt)
                    .clickable {
                        currentUrl = null
                        addressInput = "neongram://home"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FilterNone,
                    contentDescription = "Tabs",
                    tint = scheme.text,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tabCount.toString(),
                    color = scheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            BrowserNavButton(
                icon = Icons.Default.Add,
                onClick = {
                    tabCount += 1
                    currentUrl = null
                    addressInput = "neongram://home"
                }
            )
            BrowserNavButton(
                icon = Icons.Default.MoreVert,
                onClick = {
                    currentUrl = null
                    addressInput = "neongram://home"
                }
            )
        }
    }
}

@Composable
private fun QuickLinkCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = scheme.muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BrowserNavButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = LocalNeonScheme.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceAlt)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.text,
            modifier = Modifier.size(18.dp)
        )
    }
}
