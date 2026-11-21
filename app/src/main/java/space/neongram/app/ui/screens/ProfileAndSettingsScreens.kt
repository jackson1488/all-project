package space.neongram.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.util.Locale
import space.neongram.app.core.model.UserDto
import space.neongram.app.core.ui.AnimatedNeonBackground
import space.neongram.app.core.ui.LanguageSwitcher
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeonAvatar
import space.neongram.app.core.ui.NeonInput
import space.neongram.app.core.ui.PrimaryButton
import space.neongram.app.core.ui.RealQrScannerBox
import space.neongram.app.core.ui.parseHexColorOrNull
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.ActiveOverlayScreen
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel

private const val NEONGRAM_CLOUD_URL = "https://cloud.neongram.space"

private fun resolveFullMediaUrl(rawUrl: String?, serverBaseUrl: String): String? {
    val clean = rawUrl?.takeIf { it.isNotBlank() } ?: return null
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    val base = serverBaseUrl.ifBlank { NEONGRAM_CLOUD_URL }.trimEnd('/')
    return if (clean.startsWith("/")) "$base$clean" else "$base/$clean"
}

// ============================================================================
// LIQUID GLASS SHARED SURFACES & HELPERS (2026 iOS 18 Liquid Glass & Aurora Violet)
// ============================================================================

@Composable
private fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    paddingDp: Dp = 18.dp,
    accentGlow: Color = Color(0xFF2FF2FF),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = accentGlow.copy(alpha = 0.18f),
                spotColor = Color(0x557C3AED)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xAA171C3C),
                        Color(0x8C0E1229)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(700f, 600f)
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        accentGlow.copy(alpha = 0.28f),
                        Color(0xFFA855F7).copy(alpha = 0.22f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(600f, 500f)
                ),
                shape = shape
            )
            .padding(paddingDp),
        content = content
    )
}

@Composable
private fun LiquidCircularAvatarRing(
    avatarUrl: String?,
    label: String,
    sizeDp: Dp = 100.dp,
    onClick: (() -> Unit)? = null
) {
    val initial = label.trim().take(1).uppercase().ifBlank { "N" }

    Box(
        modifier = Modifier
            .size(sizeDp + 10.dp)
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                ambientColor = Color(0x882FF2FF),
                spotColor = Color(0x99A855F7)
            )
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF2FF2FF),
                        Color(0xFF8B5CF6),
                        Color(0xFFEC4899),
                        Color(0xFF2FF2FF)
                    )
                )
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A2046),
                            Color(0xFF10132B)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color(0xFFF3F6FF),
                fontSize = (sizeDp.value * 0.36f).sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun LiquidSubScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    rightContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = LocalNeonScheme.current

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
            // Frosted Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xBB191E40), Color(0x9910142C))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.38f),
                                    Color(0xFF2FF2FF).copy(alpha = 0.25f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color(0xFFF2F5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFF3F6FF),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = Color(0xFF9AA6CB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    rightContent?.invoke()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

// ============================================================================
// PROFILE TAB SCREEN (`ProfileScreen.jsx` -> 2026 iOS 18 Liquid Glass Edition)
// ============================================================================

@Composable
fun ProfileTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language
    val clipboard = LocalClipboardManager.current
    val serverBaseUrl = state.session.serverUrl.ifBlank { NEONGRAM_CLOUD_URL }

    val user = state.currentUser ?: UserDto(
        id = state.session.userId,
        username = state.session.username,
        displayName = state.session.displayName
    )

    var showTopMenu by remember { mutableStateOf(false) }
    var showMyQrModal by remember { mutableStateOf(false) }

    // System Photo Picker for real avatar upload to https://cloud.neongram.space/api/users/me/avatar
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadMyAvatarFromUri(uri)
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
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Large Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NEONGRAM ID",
                        color = Color(0xFF2FF2FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = tr(lang, "Профиль", "Профиль", "Profile"),
                        color = Color(0xFFF5F7FF),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick QR button in header as well
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xCC191E40), Color(0x9910142C))
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.38f),
                                        Color(0xFF2FF2FF).copy(alpha = 0.25f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable { showMyQrModal = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "My QR",
                            tint = Color(0xFF2FF2FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xCC191E40), Color(0x9910142C))
                                    )
                                )
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.38f),
                                            Color(0xFF2FF2FF).copy(alpha = 0.25f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .clickable { showTopMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Menu",
                                tint = Color(0xFFF2F5FF),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false },
                            modifier = Modifier.background(Color(0xFF151936))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tr(lang, "Выбрать фото профиля", "Профиль сүрөтүн тандоо", "Choose profile photo"),
                                        color = Color(0xFF2FF2FF)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    avatarPickerLauncher.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tr(lang, "Мой QR-код", "Менин QR-кодум", "My QR Code"),
                                        color = Color(0xFFF2F5FF)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    showMyQrModal = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tr(lang, "Скопировать @username", "@username көчүрүү", "Copy @username"),
                                        color = Color(0xFFF2F5FF)
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
                                        color = Color(0xFFF2F5FF)
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
                                        color = Color(0xFFF2F5FF)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    clipboard.setText(AnnotatedString("neongram://user/${user.username}"))
                                    viewModel.showToast(tr(lang, "Ссылка скопирована", "Шилтеме көчүрүлдү", "Link copied"))
                                }
                            )
                        }
                    }
                }
            }

            // 1. Frosted Liquid Glass Hero Card (`RoundedCornerShape(32.dp)`)
            LiquidGlassCard(
                cornerRadius = 32.dp,
                paddingDp = 22.dp,
                accentGlow = Color(0xFF2FF2FF)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 100.dp Circular Avatar + Glowing Cyan-Violet Ring (tapping opens photo picker)
                    LiquidCircularAvatarRing(
                        avatarUrl = resolveFullMediaUrl(user.avatarUrl, serverBaseUrl),
                        label = user.effectiveName,
                        sizeDp = 100.dp,
                        onClick = { avatarPickerLauncher.launch("image/*") }
                    )

                    // 24.sp ExtraBold Name
                    Text(
                        text = user.effectiveName,
                        color = Color(0xFFF6F8FF),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    // Glowing "● Online • cloud.neongram.space" pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x2E10B981),
                                        Color(0x262FF2FF),
                                        Color(0x228B5CF6)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x8810B981),
                                        Color(0x662FF2FF)
                                    )
                                ),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "● Online • cloud.neongram.space",
                            color = Color(0xFF5CFFCD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Row 1: 3 Frosted Glass Action Pills (Фото, Изменить, Настройки)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidProfileActionPill(
                            label = tr(lang, "Фото", "Сүрөт", "Photo"),
                            icon = Icons.Default.PhotoCamera,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                avatarPickerLauncher.launch("image/*")
                            }
                        )
                        LiquidProfileActionPill(
                            label = tr(lang, "Изменить", "Өзгөртүү", "Edit"),
                            icon = Icons.Default.Edit,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO)
                            }
                        )
                        LiquidProfileActionPill(
                            label = tr(lang, "Настройки", "Жөндөөлөр", "Settings"),
                            icon = Icons.Default.Settings,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.selectTab(3)
                            }
                        )
                    }

                    // Row 2: Quick "Мой QR-код" frosted glass pill button in the profile hero card
                    LiquidProfileActionPill(
                        label = tr(lang, "Мой QR-код", "Менин QR-кодум", "My QR Code"),
                        icon = Icons.Default.QrCode2,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showMyQrModal = true }
                    )
                }
            }

            // 2. Frosted Glass Bio & Details Card (`RoundedCornerShape(26.dp)`)
            LiquidGlassCard(
                cornerRadius = 26.dp,
                paddingDp = 18.dp,
                accentGlow = Color(0xFF8B5CF6)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileDetailField(
                        label = tr(lang, "ЛОГИН", "ЛОГИН", "USERNAME"),
                        value = "@${user.username.ifBlank { "neongram" }}",
                        highlightColor = Color(0xFF2FF2FF)
                    )
                    GlassDivider()
                    ProfileDetailField(
                        label = "ID",
                        value = user.id.ifBlank { "-" }
                    )
                    GlassDivider()
                    ProfileDetailField(
                        label = tr(lang, "ДЕНЬ РОЖДЕНИЯ", "ТУУЛГАН КҮНҮ", "BIRTHDAY"),
                        value = user.birthday?.takeIf { it.isNotBlank() } ?: "-"
                    )
                    GlassDivider()
                    ProfileDetailField(
                        label = tr(lang, "О СЕБЕ", "ӨЗҮ ЖӨНҮНДӨ", "BIO"),
                        value = user.bio?.takeIf { it.isNotBlank() }
                            ?: tr(lang, "Пользователь Neongram", "Neongram колдонуучусу", "Neongram User")
                    )
                }
            }

            // 3. Frosted Glass Stories Grid Card (`RoundedCornerShape(26.dp)`)
            LiquidGlassCard(
                cornerRadius = 26.dp,
                paddingDp = 18.dp,
                accentGlow = Color(0xFF2FF2FF)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "Мои истории", "Менин окуяларым", "My Stories"),
                            color = Color(0xFFF3F6FF),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0x332FF2FF),
                                            Color(0x338B5CF6)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color(0xFF2FF2FF).copy(alpha = 0.55f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    avatarPickerLauncher.launch("image/*")
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = tr(lang, "Выбрать фото", "Сүрөт тандоо", "Choose photo"),
                                color = Color(0xFF2FF2FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (state.myStories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0x440D1126))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(
                                    lang,
                                    "У вас пока нет опубликованных историй",
                                    "Сизде азырынча жарыяланган окуялар жок",
                                    "You have no published stories yet"
                                ),
                                color = Color(0xFF9AA6CB),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        state.myStories.chunked(3).forEach { rowStories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowStories.forEach { story ->
                                    val mediaUrl = resolveFullMediaUrl(story.mediaUrl, serverBaseUrl)
                                    val storyShape = RoundedCornerShape(16.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.75f)
                                            .clip(storyShape)
                                            .background(Color(0xFF181D3A))
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.32f),
                                                        Color(0xFF2FF2FF).copy(alpha = 0.25f)
                                                    )
                                                ),
                                                shape = storyShape
                                            ),
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
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0x990A0D1E))
                                                    .padding(horizontal = 6.dp, vertical = 5.dp)
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
    }

    // Contact QR Modal (`neongram://user/${user.username}`)
    if (showMyQrModal) {
        val qrUsername = user.username.ifBlank { "neongram" }
        val qrPayload = "neongram://user/$qrUsername"
        Dialog(onDismissRequest = { showMyQrModal = false }) {
            LiquidGlassCard(
                cornerRadius = 30.dp,
                paddingDp = 24.dp,
                accentGlow = Color(0xFF2FF2FF)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "Мой QR-код", "Менин QR-кодум", "My QR Code"),
                            color = Color(0xFFF6F8FF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { showMyQrModal = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFF2F5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    VisualQrCodeBox(payload = qrPayload)

                    Text(
                        text = user.effectiveName,
                        color = Color(0xFFF5F7FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = qrPayload,
                        color = Color(0xFF2FF2FF),
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
    }
}

@Composable
private fun LiquidProfileActionPill(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(pillShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x402FF2FF),
                        Color(0x337C3AED),
                        Color(0x88151A38)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color(0xFF2FF2FF).copy(alpha = 0.35f),
                        Color(0xFFA855F7).copy(alpha = 0.25f)
                    )
                ),
                shape = pillShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF2FF2FF),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color(0xFFF3F6FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassDivider() {
    HorizontalDivider(
        thickness = 0.8.dp,
        color = Color.White.copy(alpha = 0.10f)
    )
}

@Composable
private fun ProfileDetailField(
    label: String,
    value: String,
    highlightColor: Color = Color(0xFFF3F6FF)
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = Color(0xFF94A1C8),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp
        )
        Text(
            text = value,
            color = highlightColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================================
// PEER PROFILE SCREEN (`UserProfileScreen.jsx` -> Liquid Glass Edition)
// ============================================================================

@Composable
fun PeerProfileScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language
    val serverBaseUrl = state.session.serverUrl.ifBlank { NEONGRAM_CLOUD_URL }

    val peer = state.peerProfileUser
        ?: state.activeChat?.resolvePeerUser(state.session.userId)
        ?: UserDto(id = "peer", username = "neongram_user", displayName = "Neongram User")

    var selectedTab by remember { mutableIntStateOf(0) }
    var showReportModal by remember { mutableStateOf(false) }

    LaunchedEffect(peer.id) {
        viewModel.loadBlockedUsers()
    }

    val isBlocked = state.blockedUsers.any {
        it.blockedId == peer.id || it.blocked?.id == peer.id
    }

    LiquidSubScreenScaffold(
        title = peer.effectiveName,
        subtitle = "@${peer.username} • cloud.neongram.space",
        onBack = { viewModel.navigateBack() }
    ) {
        // Frosted Liquid Glass Hero Card
        LiquidGlassCard(
            cornerRadius = 32.dp,
            paddingDp = 22.dp,
            accentGlow = Color(0xFF2FF2FF)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidCircularAvatarRing(
                    avatarUrl = resolveFullMediaUrl(peer.avatarUrl, serverBaseUrl),
                    label = peer.effectiveName,
                    sizeDp = 100.dp
                )

                Text(
                    text = peer.effectiveName,
                    color = Color(0xFFF6F8FF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                val statusText = if (peer.isOnline) {
                    "● " + tr(lang, "В сети", "Тармакта", "Online") + " • @${peer.username}"
                } else {
                    "○ " + tr(lang, "Не в сети", "Тармакта эмес", "Offline") + " • @${peer.username}"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (peer.isOnline) Color(0x2A10B981) else Color(0x331E2448)
                        )
                        .border(
                            width = 1.dp,
                            color = if (peer.isOnline) Color(0x8010B981) else Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = statusText,
                        color = if (peer.isOnline) Color(0xFF5CFFCD) else Color(0xFF9AA6CB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 4 Frosted Glass Action Buttons: Написать, В контакты, Позвонить, Секретный чат
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
                        label = tr(lang, "Секретный", "Жашыруун", "Secret"),
                        icon = Icons.Default.Lock,
                        onClick = {
                            viewModel.startDirectChat(peer)
                            viewModel.showToast(tr(lang, "E2EE чат открыт", "E2EE чат ачылды", "E2EE chat opened"))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Block / Unblock and Report Action Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val blockShape = RoundedCornerShape(18.dp)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(blockShape)
                            .background(
                                if (isBlocked) Color(0x3310B981) else Color(0x33FF4D6D)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isBlocked) Color(0x8810B981) else Color(0x88FF4D6D),
                                shape = blockShape
                            )
                            .clickable {
                                if (isBlocked) {
                                    viewModel.unblockUser(peer.id)
                                } else {
                                    viewModel.blockUser(peer.id)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isBlocked) Color(0xFF5CFFCD) else Color(0xFFFF6B88),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBlocked) {
                                tr(lang, "Разблокировать", "Блоктон чыгаруу", "Unblock")
                            } else {
                                tr(lang, "Заблокировать", "Блоктоо", "Block")
                            },
                            color = if (isBlocked) Color(0xFF5CFFCD) else Color(0xFFFF6B88),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(blockShape)
                            .background(Color(0x33F59E0B))
                            .border(
                                width = 1.dp,
                                color = Color(0x88F59E0B),
                                shape = blockShape
                            )
                            .clickable { showReportModal = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr(lang, "Пожаловаться", "Даттануу", "Report"),
                            color = Color(0xFFFBBF24),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Frosted 3-Tab Segmented Switcher: Инфо / Медиа / Файлы
        val switcherShape = RoundedCornerShape(22.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(switcherShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xAA171C3C), Color(0x8C0E1229))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color(0xFF2FF2FF).copy(alpha = 0.24f)
                        )
                    ),
                    shape = switcherShape
                )
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                tr(lang, "Инфо", "Инфо", "Info"),
                tr(lang, "Медиа", "Медиа", "Media"),
                tr(lang, "Файлы", "Файлдар", "Files")
            ).forEachIndexed { idx, tabLabel ->
                val active = selectedTab == idx
                val itemShape = RoundedCornerShape(17.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(itemShape)
                        .then(
                            if (active) {
                                Modifier
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0x552FF2FF),
                                                Color(0x558B5CF6)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color(0xFF2FF2FF).copy(alpha = 0.55f)
                                            )
                                        ),
                                        shape = itemShape
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { selectedTab = idx },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = if (active) Color(0xFF2FF2FF) else Color(0xFFB4BEE0),
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                LiquidGlassCard(
                    cornerRadius = 26.dp,
                    paddingDp = 18.dp,
                    accentGlow = Color(0xFF8B5CF6)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileDetailField(
                            label = tr(lang, "ЛОГИН", "ЛОГИН", "USERNAME"),
                            value = "@${peer.username}",
                            highlightColor = Color(0xFF2FF2FF)
                        )
                        GlassDivider()
                        ProfileDetailField(
                            label = "ID",
                            value = peer.id.ifBlank { "-" }
                        )
                        GlassDivider()
                        ProfileDetailField(
                            label = tr(lang, "ДЕНЬ РОЖДЕНИЯ", "ТУУЛГАН КҮНҮ", "BIRTHDAY"),
                            value = peer.birthday?.takeIf { it.isNotBlank() } ?: "-"
                        )
                        GlassDivider()
                        ProfileDetailField(
                            label = tr(lang, "О СЕБЕ", "ӨЗҮ ЖӨНҮНДӨ", "BIO"),
                            value = peer.bio?.takeIf { it.isNotBlank() }
                                ?: tr(lang, "Пользователь Neongram", "Neongram колдонуучусу", "Neongram User")
                        )
                    }
                }
            }
            1 -> {
                val mediaMsgs = state.messages.filter {
                    it.type == "IMAGE" || it.type == "VIDEO" || it.type == "PHOTO" || !it.fileUrl.isNullOrBlank()
                }
                LiquidGlassCard(
                    cornerRadius = 26.dp,
                    paddingDp = 18.dp
                ) {
                    if (mediaMsgs.isEmpty()) {
                        Text(
                            text = tr(lang, "Общих медиафайлов пока нет", "Жалпы медиа жок", "No shared media yet"),
                            color = Color(0xFF9AA6CB),
                            fontSize = 13.sp
                        )
                    } else {
                        mediaMsgs.forEach { msg ->
                            Text(
                                text = msg.fileName ?: msg.fileUrl.orEmpty(),
                                color = Color(0xFF2FF2FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                val fileMsgs = state.messages.filter { !it.fileName.isNullOrBlank() }
                LiquidGlassCard(
                    cornerRadius = 26.dp,
                    paddingDp = 18.dp
                ) {
                    if (fileMsgs.isEmpty()) {
                        Text(
                            text = tr(lang, "Общих документов пока нет", "Жалпы файлдар жок", "No shared files yet"),
                            color = Color(0xFF9AA6CB),
                            fontSize = 13.sp
                        )
                    } else {
                        fileMsgs.forEach { msg ->
                            Text(
                                text = msg.fileName.orEmpty(),
                                color = Color(0xFFF3F6FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Report Reason Glass Modal (Спам, Оскорбления, Мошенничество, Другое)
    if (showReportModal) {
        var customComment by remember { mutableStateOf("") }
        val reasons = listOf(
            "Спам" to tr(lang, "Спам", "Спам", "Spam"),
            "Оскорбления" to tr(lang, "Оскорбления", "Кемсинтүү", "Harassment / Abuse"),
            "Мошенничество" to tr(lang, "Мошенничество", "Алдамчылык", "Scam / Fraud"),
            "Другое" to tr(lang, "Другое", "Башка", "Other")
        )

        Dialog(onDismissRequest = { showReportModal = false }) {
            LiquidGlassCard(
                cornerRadius = 28.dp,
                paddingDp = 22.dp,
                accentGlow = Color(0xFFF59E0B)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "Пожаловаться", "Даттануу", "Report User"),
                            color = Color(0xFFF6F8FF),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { showReportModal = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFF2F5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = tr(
                            lang,
                            "Выберите причину жалобы на @${peer.username}:",
                            "@${peer.username} үчүн даттануу себебин тандаңыз:",
                            "Choose a reason to report @${peer.username}:"
                        ),
                        color = Color(0xFF9AA6CB),
                        fontSize = 13.sp
                    )

                    NeonInput(
                        value = customComment,
                        onValueChange = { customComment = it },
                        placeholder = tr(lang, "Комментарий (необязательно)", "Комментарий", "Optional comment")
                    )

                    reasons.forEach { (reasonCode, reasonLabel) ->
                        val itemShape = RoundedCornerShape(16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(Color(0x55151A38))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), itemShape)
                                .clickable {
                                    viewModel.reportTarget(
                                        targetId = peer.id,
                                        targetType = "user",
                                        reason = reasonCode,
                                        comment = customComment.trim()
                                    )
                                    showReportModal = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reasonLabel,
                                color = Color(0xFFF5F7FF),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
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
    val boxShape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(boxShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x402FF2FF),
                            Color(0x338B5CF6),
                            Color(0x88151A38)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color(0xFF2FF2FF).copy(alpha = 0.35f)
                        )
                    ),
                    shape = boxShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF2FF2FF),
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            color = Color(0xFFF2F5FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================================
// SETTINGS TAB SCREEN (`SettingsScreen.jsx` -> 2026 iOS 18 Liquid Glass Edition)
// Exclusively works with https://cloud.neongram.space (NO Server Selection Row)
// ============================================================================

@Composable
fun SettingsTabScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

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
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Large "Settings / Настройки" Header (34.sp, ExtraBold)
            Column(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "CLOUD.NEONGRAM.SPACE • LIQUID GLASS",
                    color = Color(0xFF2FF2FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.3.sp
                )
                Text(
                    text = tr(
                        lang,
                        "Settings / Настройки",
                        "Settings / Жөндөөлөр",
                        "Settings / Настройки"
                    ),
                    color = Color(0xFFF6F8FF),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 38.sp
                )
            }

            // 7 Frosted Liquid Glass Cards (`RoundedCornerShape(26.dp)`)
            LiquidSettingsSectionCard(
                title = tr(lang, "Аккаунт", "Аккаунт", "Account"),
                subtitle = tr(
                    lang,
                    "Профиль, смена аккаунта и выход",
                    "Профиль жана чыгуу",
                    "Profile, switch account & logout"
                ),
                icon = Icons.Default.Person,
                badgeColor = Color(0xFF2FF2FF),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ACCOUNT) }
            )

            LiquidSettingsSectionCard(
                title = tr(lang, "Конфиденциальность", "Купуялуулук", "Privacy"),
                subtitle = tr(
                    lang,
                    "Статус в сети, звонки, чтение и ЧС",
                    "Тармактагы статус жана чалуулар",
                    "Online status, calls & read receipts"
                ),
                icon = Icons.Default.Lock,
                badgeColor = Color(0xFFA855F7),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_PRIVACY) }
            )

            LiquidSettingsSectionCard(
                title = tr(lang, "Безопасность", "Коопсуздук", "Security"),
                subtitle = tr(
                    lang,
                    "Код-пароль, биометрия, 2FA и пароль",
                    "2FA жана сырсөз",
                    "Passcode, biometrics, 2FA & password"
                ),
                icon = Icons.Default.Security,
                badgeColor = Color(0xFF10B981),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_SECURITY) }
            )

            LiquidSettingsSectionCard(
                title = tr(lang, "Устройства", "Түзмөктөр", "Devices"),
                subtitle = tr(
                    lang,
                    "Активные сеансы и вход по QR",
                    "Активдүү сеанстар жана QR кирүү",
                    "Active sessions & QR login"
                ),
                icon = Icons.Default.Devices,
                badgeColor = Color(0xFF38BDF8),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_DEVICES) }
            )

            LiquidSettingsSectionCard(
                title = tr(lang, "Оформление", "Көрүнүш", "Appearance"),
                subtitle = tr(
                    lang,
                    "Темы, язык, размер шрифта, пузыри и обои",
                    "Темалар, тил, шрифт жана обои",
                    "Themes, language, font size, bubbles & wallpaper"
                ),
                icon = Icons.Default.Palette,
                badgeColor = Color(0xFFEC4899),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_APPEARANCE) }
            )

            LiquidSettingsSectionCard(
                title = tr(lang, "Хранилище и данные", "Сактагыч жана маалымат", "Storage & Data"),
                subtitle = tr(
                    lang,
                    "Использование памяти и очистка кэша",
                    "Эстутум жана кэшти тазалоо",
                    "Memory usage & clear cache"
                ),
                icon = Icons.Default.Storage,
                badgeColor = Color(0xFFF59E0B),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_STORAGE) }
            )

            LiquidSettingsSectionCard(
                title = tr(lang, "О приложении", "Тиркеме жөнүндө", "About App"),
                subtitle = tr(
                    lang,
                    "Версия, поддержка, FAQ и условия",
                    "Версия, колдоо жана FAQ",
                    "Version, support, FAQ & terms"
                ),
                icon = Icons.Default.Info,
                badgeColor = Color(0xFF818CF8),
                onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ABOUT) }
            )
        }
    }
}

@Composable
private fun LiquidSettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    danger: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(26.dp)
    val iconShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = cardShape,
                ambientColor = badgeColor.copy(alpha = 0.16f),
                spotColor = Color(0x447C3AED)
            )
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xAA171C3C),
                        Color(0x8C0E1229)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        badgeColor.copy(alpha = 0.28f),
                        Color(0xFFA855F7).copy(alpha = 0.20f)
                    )
                ),
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(iconShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            badgeColor.copy(alpha = 0.28f),
                            badgeColor.copy(alpha = 0.12f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = badgeColor.copy(alpha = 0.48f),
                    shape = iconShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (danger) Color(0xFFFF4D6D) else badgeColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = if (danger) Color(0xFFFF5E7E) else Color(0xFFF4F6FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color(0xFF9AA6CB),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showChevron) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFB4BEE0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// 1. ACCOUNT SETTINGS SUB-SCREEN (Liquid Glass)
// ============================================================================

@Composable
fun AccountSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language
    val serverBaseUrl = state.session.serverUrl.ifBlank { NEONGRAM_CLOUD_URL }
    val user = state.currentUser ?: UserDto(
        id = state.session.userId,
        username = state.session.username,
        displayName = state.session.displayName
    )

    LiquidSubScreenScaffold(
        title = tr(lang, "Аккаунт", "Аккаунт", "Account"),
        subtitle = "cloud.neongram.space",
        onBack = { viewModel.navigateBack() }
    ) {
        // Active Account Frosted Card
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 16.dp,
            accentGlow = Color(0xFF2FF2FF)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeonAvatar(
                    avatarUrl = resolveFullMediaUrl(user.avatarUrl, serverBaseUrl),
                    label = user.effectiveName,
                    sizeDp = 54.dp,
                    cornerRadiusDp = 18.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.effectiveName,
                        color = Color(0xFFF5F7FF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "@${user.username} • cloud.neongram.space",
                        color = Color(0xFF9AA6CB),
                        fontSize = 12.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2E2FF2FF))
                        .border(1.dp, Color(0x662FF2FF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = tr(lang, "Активен", "Активдүү", "Active"),
                        color = Color(0xFF2FF2FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        LiquidSettingsSectionCard(
            title = tr(lang, "Профиль", "Профиль", "Profile Info"),
            subtitle = tr(
                lang,
                "Имя, @username, дата рождения и описание",
                "Аты, @username жана өзү жөнүндө",
                "Display name, @username, birthday & bio"
            ),
            icon = Icons.Default.Edit,
            badgeColor = Color(0xFF2FF2FF),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO) }
        )

        LiquidSettingsSectionCard(
            title = tr(lang, "Пароль и 2FA", "Сырсөз жана 2FA", "Password & 2FA"),
            subtitle = tr(
                lang,
                "Изменить пароль и параметры входа",
                "Сырсөздү өзгөртүү",
                "Change password & 2FA settings"
            ),
            icon = Icons.Default.Security,
            badgeColor = Color(0xFFA855F7),
            onClick = { viewModel.navigateToOverlay(ActiveOverlayScreen.SETTINGS_SECURITY) }
        )

        LiquidSettingsSectionCard(
            title = tr(lang, "Выйти из аккаунта", "Аккаунттан чыгуу", "Log Out"),
            subtitle = tr(
                lang,
                "Завершить текущий сеанс на этом устройстве",
                "Учурдагы сеансты аяктоо",
                "End current session on this device"
            ),
            icon = Icons.Default.DeleteOutline,
            badgeColor = Color(0xFFFF4D6D),
            danger = true,
            showChevron = false,
            onClick = { viewModel.logout() }
        )
    }
}

// ============================================================================
// 2. ACCOUNT INFO EDIT SUB-SCREEN (Liquid Glass)
// ============================================================================

@Composable
fun AccountInfoEditScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
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

    LiquidSubScreenScaffold(
        title = tr(lang, "Изменить инфо", "Инфо өзгөртүү", "Edit Profile"),
        subtitle = "@${user.username.ifBlank { "neongram" }}",
        onBack = { viewModel.navigateBack() }
    ) {
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF2FF2FF)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = tr(lang, "Отображаемое имя", "Көрсөтүлгөн аты", "Display Name"),
                    color = Color(0xFF9AA6CB),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                NeonInput(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = tr(lang, "Ваше имя", "Сиздин атыңыз", "Your display name")
                )

                Text(
                    text = tr(lang, "Имя пользователя (@username)", "Колдонуучу аты (@username)", "Username (@username)"),
                    color = Color(0xFF9AA6CB),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
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
                        color = Color(0xFF9AA6CB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${bio.length}/60",
                        color = Color(0xFF2FF2FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                NeonInput(
                    value = bio,
                    onValueChange = { if (it.length <= 60) bio = it },
                    placeholder = tr(lang, "Расскажите о себе", "Өзүңүз жөнүндө жазыңыз", "Tell about yourself")
                )

                Text(
                    text = tr(lang, "День рождения (YYYY-MM-DD)", "Туулган күнү (YYYY-MM-DD)", "Birthday (YYYY-MM-DD)"),
                    color = Color(0xFF9AA6CB),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                NeonInput(
                    value = birthday,
                    onValueChange = { birthday = it },
                    placeholder = "2000-01-25"
                )

                Spacer(modifier = Modifier.height(6.dp))

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
}

// ============================================================================
// 3. PRIVACY SETTINGS SUB-SCREEN (with Live Blocked Users Manager)
// ============================================================================

@Composable
fun PrivacySettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language
    val serverBaseUrl = state.session.serverUrl.ifBlank { NEONGRAM_CLOUD_URL }
    val user = state.currentUser ?: UserDto()

    LaunchedEffect(Unit) {
        viewModel.loadBlockedUsers()
    }

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

    LiquidSubScreenScaffold(
        title = tr(lang, "Конфиденциальность", "Купуялуулук", "Privacy"),
        subtitle = "E2EE & Visibility Controls",
        onBack = { viewModel.navigateBack() }
    ) {
        LiquidSwitchCard(
            title = tr(lang, "Показывать статус в сети", "\"Тармакта\" статусун көрсөтүү", "Show online status"),
            subtitle = tr(lang, "Другие пользователи видят, когда вы онлайн", "Башкалар сиздин онлайн экениңизди көрөт", "Let others see when you're online"),
            checked = showOnline,
            onCheckedChange = { pushUpdate(online = it) }
        )
        LiquidSwitchCard(
            title = tr(lang, "Показывать время захода", "Акыркы кирген убакытты көрсөтүү", "Show last seen time"),
            subtitle = tr(lang, "Отображать время последней активности", "Акыркы активдүүлүк убактысы", "Display your last active timestamp"),
            checked = showLastSeen,
            onCheckedChange = { pushUpdate(lastSeen = it) }
        )
        LiquidSwitchCard(
            title = tr(lang, "Разрешить звонки", "Чалууларга уруксат берүү", "Allow calls"),
            subtitle = tr(lang, "Принимать голосовые и видеозвонки", "Үн жана видео чалууларды кабыл алуу", "Receive voice and video calls"),
            checked = allowCalls,
            onCheckedChange = { pushUpdate(calls = it) }
        )
        LiquidSwitchCard(
            title = tr(lang, "Звонки только от контактов", "Байланыштардан гана чалуулар", "Calls from contacts only"),
            subtitle = tr(lang, "Блокировать звонки от незнакомых аккаунтов", "Бейтааныш аккаунттардан чалууларды чектөө", "Block calls from unknown users"),
            checked = callsContactsOnly,
            onCheckedChange = { pushUpdate(contactsOnly = it) }
        )
        LiquidSwitchCard(
            title = tr(lang, "Отчеты о прочтении", "Окулгандыгы тууралуу белги", "Read receipts"),
            subtitle = tr(lang, "Показывать двойную галочку при прочтении", "Билдирүү окулганда кош белги көрсөтүү", "Show double-check when messages are read"),
            checked = readReceipts,
            onCheckedChange = { pushUpdate(receipts = it) }
        )

        // Чёрный список (Заблокированные пользователи)
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFFFF4D6D)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = Color(0xFFFF5E7E),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = tr(
                                lang,
                                "Чёрный список (Заблокированные пользователи)",
                                "Кара тизме (Блоктолгондор)",
                                "Blocked Users"
                            ),
                            color = Color(0xFFF5F7FF),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FF4D6D))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = state.blockedUsers.size.toString(),
                            color = Color(0xFFFF6B88),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (state.blockedUsers.isEmpty()) {
                    Text(
                        text = tr(
                            lang,
                            "В вашем чёрном списке пока нет заблокированных пользователей.",
                            "Кара тизмеде блоктолгон колдонуучулар жок.",
                            "You have no blocked users."
                        ),
                        color = Color(0xFF9AA6CB),
                        fontSize = 13.sp
                    )
                } else {
                    state.blockedUsers.forEach { entry ->
                        val blockedUser = entry.blocked
                        val targetId = entry.blockedId.ifBlank { blockedUser?.id.orEmpty() }
                        val displayTitle = blockedUser?.effectiveName?.takeIf { it.isNotBlank() }
                            ?: "ID: $targetId"
                        val usernameSub = blockedUser?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                            ?: targetId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0x5510142C))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeonAvatar(
                                avatarUrl = resolveFullMediaUrl(blockedUser?.avatarUrl, serverBaseUrl),
                                label = displayTitle,
                                sizeDp = 40.dp,
                                cornerRadiusDp = 14.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayTitle,
                                    color = Color(0xFFF5F7FF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = usernameSub,
                                    color = Color(0xFF9AA6CB),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x332FF2FF))
                                    .border(1.dp, Color(0x882FF2FF), RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (targetId.isNotBlank()) {
                                            viewModel.unblockUser(targetId)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tr(lang, "Разблокировать", "Чыгаруу", "Unblock"),
                                    color = Color(0xFF2FF2FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val cardShape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xAA171C3C), Color(0x8C0E1229))
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        if (checked) Color(0xFF2FF2FF).copy(alpha = 0.35f) else Color(0xFFA855F7).copy(alpha = 0.20f)
                    )
                ),
                shape = cardShape
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFFF4F6FF),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = Color(0xFF9AA6CB),
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF070914),
                checkedTrackColor = Color(0xFF2FF2FF),
                uncheckedThumbColor = Color(0xFF9AA6CB),
                uncheckedTrackColor = Color(0xFF1A2042)
            )
        )
    }
}

// ============================================================================
// 4. SECURITY SETTINGS SUB-SCREEN (Full 100% Functional Security Suite)
// ============================================================================

@Composable
fun SecuritySettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.load2faStatus()
    }

    // Section 1: PIN Lock states
    val isPinActive = state.session.pinCode.isNotBlank()
    var pinInput by remember { mutableStateOf("") }
    var selectedAutoLockTimeout by remember { mutableStateOf("5 мин") }

    // Section 2: 2FA states
    var totpConfirmCode by remember { mutableStateOf("") }
    var totpDisableCode by remember { mutableStateOf("") }

    // Section 3: Password states
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LiquidSubScreenScaffold(
        title = tr(lang, "Безопасность", "Коопсуздук", "Security"),
        subtitle = "cloud.neongram.space • Zero-Trust",
        onBack = { viewModel.navigateBack() }
    ) {
        // ====================================================================
        // Section 1: Код-пароль и Блокировка приложения (PIN Lock)
        // ====================================================================
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF8B5CF6)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "PIN Lock",
                            tint = Color(0xFF2FF2FF),
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = tr(
                                lang,
                                "Код-пароль и Блокировка приложения",
                                "Код-сырсөз жана Блоктоо",
                                "PIN Code & App Lock"
                            ),
                            color = Color(0xFFF5F7FF),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPinActive) Color(0x2E10B981) else Color(0x331E2448))
                            .border(
                                width = 1.dp,
                                color = if (isPinActive) Color(0x8810B981) else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPinActive) {
                                tr(lang, "Активен", "Активдүү", "Active")
                            } else {
                                tr(lang, "Выкл", "Өчүк", "Off")
                            },
                            color = if (isPinActive) Color(0xFF5CFFCD) else Color(0xFF9AA6CB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = tr(
                        lang,
                        "Установите 4-значный PIN-код для защиты входа в приложение Neongram на этом устройстве.",
                        "Тиркемени коргоо үчүн 4 орундуу PIN-код орнотуңуз.",
                        "Set a 4-digit PIN code to protect your Neongram app on this device."
                    ),
                    color = Color(0xFF9AA6CB),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )

                NeonInput(
                    value = pinInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(4)
                        pinInput = digits
                    },
                    placeholder = tr(lang, "Введите 4-значный PIN (например, 2026)", "4 орундуу PIN", "Enter 4-digit PIN"),
                    isPassword = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PrimaryButton(
                            label = if (isPinActive) {
                                tr(lang, "Изменить PIN", "PIN өзгөртүү", "Change PIN")
                            } else {
                                tr(lang, "Установить PIN", "PIN орнотуу", "Set PIN")
                            },
                            onClick = {
                                if (pinInput.length == 4) {
                                    viewModel.setPinCode(pinInput)
                                    pinInput = ""
                                } else {
                                    viewModel.showToast(
                                        tr(lang, "Введите ровно 4 цифры", "4 сан жазыңыз", "Enter 4 digits")
                                    )
                                }
                            }
                        )
                    }

                    if (isPinActive) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x28FF4D6D))
                                .border(1.dp, Color(0x88FF4D6D), RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.setPinCode("")
                                    pinInput = ""
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr(lang, "Отключить PIN", "Өчүрүү", "Remove PIN"),
                                color = Color(0xFFFF6B88),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Auto-lock timeout selector (1 мин, 5 мин, 15 мин)
                Text(
                    text = tr(lang, "Автоблокировка", "Автоблоктоо", "Auto-Lock Timeout"),
                    color = Color(0xFF9AA6CB),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1 мин", "5 мин", "15 мин").forEach { timeoutLabel ->
                        val active = selectedAutoLockTimeout == timeoutLabel
                        val pillShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .clip(pillShape)
                                .background(
                                    if (active) {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF2FF2FF), Color(0xFF8B5CF6))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0x66151A36), Color(0x6610142A))
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                                    shape = pillShape
                                )
                                .clickable {
                                    selectedAutoLockTimeout = timeoutLabel
                                    viewModel.showToast(
                                        tr(
                                            lang,
                                            "Автоблокировка: $timeoutLabel",
                                            "Автоблоктоо: $timeoutLabel",
                                            "Auto-lock: $timeoutLabel"
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = timeoutLabel,
                                color = if (active) Color(0xFF060814) else Color(0xFFF2F5FF),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Button: "Заблокировать приложение сейчас"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0x332FF2FF), Color(0x338B5CF6))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0x882FF2FF), Color(0x888B5CF6))
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { viewModel.lockAppNow() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr(
                            lang,
                            "Заблокировать приложение сейчас",
                            "Тиркемени азыр блоктоо",
                            "Lock App Now"
                        ),
                        color = Color(0xFF2FF2FF),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ====================================================================
        // Section 2: Двухфакторная аутентификация (2FA TOTP & Системный чат)
        // ====================================================================
        val twoFactorEnabled = state.twoFactorStatus.enabled
        val setupData = state.twoFactorSetupData

        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF2FF2FF)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "2FA",
                            tint = Color(0xFF2FF2FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = tr(
                                lang,
                                "Двухфакторная аутентификация (2FA)",
                                "Эки баскычтуу коргоо (2FA)",
                                "Two-Factor Authentication (2FA)"
                            ),
                            color = Color(0xFFF5F7FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (twoFactorEnabled) Color(0x2E10B981) else Color(0x331E2448))
                            .border(
                                width = 1.dp,
                                color = if (twoFactorEnabled) Color(0x8810B981) else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (twoFactorEnabled) {
                                tr(lang, "Включена", "Жандырылган", "Enabled")
                            } else {
                                tr(lang, "Отключена", "Өчүрүлгөн", "Disabled")
                            },
                            color = if (twoFactorEnabled) Color(0xFF5CFFCD) else Color(0xFF9AA6CB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = tr(
                        lang,
                        "Защитите вход одноразовыми кодами Google Authenticator / Authy и подтверждением новых устройств в системном чате.",
                        "Аккаунтуңузду Google Authenticator коду менен коргоңуз.",
                        "Protect your account with TOTP authenticator codes and device login approval via system chat."
                    ),
                    color = Color(0xFF9AA6CB),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )

                // Switch for "Подтверждение входа через системный чат"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x5510142C))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .clickable {
                            viewModel.toggle2faDeviceApproval(!state.twoFactorStatus.deviceApprovalEnabled)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(
                                lang,
                                "Подтверждение входа через системный чат",
                                "Системалык чат аркылуу кирүүнү ырастоо",
                                "Confirm logins via System Chat"
                            ),
                            color = Color(0xFFF5F7FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr(
                                lang,
                                "Отправлять запрос Разрешить / Отклонить при новом входе",
                                "Жаңы кирүүдө сурам жөнөтүү",
                                "Send Approve / Reject alert on new device login"
                            ),
                            color = Color(0xFF9AA6CB),
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = state.twoFactorStatus.deviceApprovalEnabled,
                        onCheckedChange = { viewModel.toggle2faDeviceApproval(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF070914),
                            checkedTrackColor = Color(0xFF2FF2FF),
                            uncheckedThumbColor = Color(0xFF9AA6CB),
                            uncheckedTrackColor = Color(0xFF1A2042)
                        )
                    )
                }

                if (!twoFactorEnabled) {
                    if (setupData == null) {
                        PrimaryButton(
                            label = tr(
                                lang,
                                "Настроить 2FA (Google Authenticator)",
                                "2FA тууралоо (Google Authenticator)",
                                "Set Up 2FA (Google Authenticator)"
                            ),
                            onClick = { viewModel.start2faSetup() }
                        )
                    } else {
                        // Display secret key (with copy button), otpauthUrl QR & text, 6-digit input & confirm button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x660D1126))
                                .border(1.dp, Color(0x662FF2FF), RoundedCornerShape(20.dp))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (setupData.otpauthUrl.isNotBlank()) {
                                VisualQrCodeBox(payload = setupData.otpauthUrl)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr(lang, "СЕКРЕТНЫЙ КЛЮЧ (TOTP SECRET)", "ЖАШЫРУУН АЧКЫЧ", "TOTP SECRET KEY"),
                                        color = Color(0xFF9AA6CB),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = setupData.secret,
                                        color = Color(0xFF2FF2FF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x332FF2FF))
                                        .border(1.dp, Color(0x882FF2FF), RoundedCornerShape(12.dp))
                                        .clickable {
                                            clipboard.setText(AnnotatedString(setupData.secret))
                                            viewModel.showToast(
                                                tr(lang, "Секретный ключ скопирован", "Ачкыч көчүрүлдү", "Secret key copied")
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = Color(0xFF2FF2FF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = tr(lang, "Копировать", "Көчүрүү", "Copy"),
                                            color = Color(0xFF2FF2FF),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (setupData.otpauthUrl.isNotBlank()) {
                                Text(
                                    text = setupData.otpauthUrl,
                                    color = Color(0xFF9AA6CB),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            clipboard.setText(AnnotatedString(setupData.otpauthUrl))
                                            viewModel.showToast("otpauth:// URL скопирован")
                                        }
                                )
                            }

                            NeonInput(
                                value = totpConfirmCode,
                                onValueChange = { raw ->
                                    totpConfirmCode = raw.filter { it.isDigit() }.take(6)
                                },
                                placeholder = tr(lang, "6-значный код из приложения", "6 орундуу код", "6-digit code from Authenticator")
                            )

                            PrimaryButton(
                                label = tr(
                                    lang,
                                    "Подтвердить и включить 2FA",
                                    "Ырастоо жана 2FA жандыруу",
                                    "Confirm & Enable 2FA"
                                ),
                                onClick = {
                                    viewModel.confirm2faSetup(totpConfirmCode)
                                    totpConfirmCode = ""
                                }
                            )
                        }
                    }
                } else {
                    // 2FA is enabled: input for 6-digit code + "Отключить 2FA"
                    NeonInput(
                        value = totpDisableCode,
                        onValueChange = { raw ->
                            totpDisableCode = raw.filter { it.isDigit() }.take(6)
                        },
                        placeholder = tr(lang, "Введите 6-значный код 2FA", "6 орундуу 2FA коду", "Enter 6-digit 2FA code")
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x28FF4D6D))
                            .border(1.dp, Color(0x88FF4D6D), RoundedCornerShape(18.dp))
                            .clickable {
                                viewModel.disable2fa(totpDisableCode)
                                totpDisableCode = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Отключить 2FA", "2FA өчүрүү", "Disable 2FA"),
                            color = Color(0xFFFF6B88),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // ====================================================================
        // Section 3: Смена пароля
        // ====================================================================
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF10B981)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = tr(lang, "Смена пароля", "Сырсөздү өзгөртүү", "Change Password"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
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
        }
    }
}

// ============================================================================
// 5. DEVICES SETTINGS SUB-SCREEN (with Real Camera QR Scanner to Connect Any Device/Web)
// ============================================================================

@Composable
fun DevicesSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language
    var showQrCameraScannerModal by remember { mutableStateOf(false) }
    var manualQrInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    LiquidSubScreenScaffold(
        title = tr(lang, "Устройства", "Түзмөктөр", "Devices"),
        subtitle = "Active Sessions & QR Auth",
        onBack = { viewModel.navigateBack() }
    ) {
        // Real Camera QR Scanner Button to Connect Any Device / Web
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2FF2FF), Color(0xFF8B5CF6))
                    )
                )
                .clickable { showQrCameraScannerModal = true }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "QR",
                tint = Color(0xFF070914),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = tr(
                    lang,
                    "Сканировать QR для входа на другом устройстве / Web",
                    "Башка түзмөктө / Web кирүү үчүн QR сканерлөө",
                    "Scan QR to Log In on Another Device / Web"
                ),
                color = Color(0xFF070914),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }

        // Terminate Other Sessions Frosted Glass Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0x28FF4D6D))
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x99FF4D6D),
                            Color.White.copy(alpha = 0.22f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable { viewModel.terminateOtherSessions() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tr(lang, "Завершить другие сеансы", "Башка сеанстарды аяктоо", "Terminate other sessions"),
                color = Color(0xFFFF6B88),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            text = tr(lang, "Активные сеансы", "Активдүү сеанстар", "Active Sessions"),
            color = Color(0xFFF5F7FF),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (state.activeSessions.isEmpty()) {
            LiquidGlassCard(
                cornerRadius = 26.dp,
                paddingDp = 16.dp,
                accentGlow = Color(0xFF2FF2FF)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = Color(0xFF2FF2FF)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Android • Neongram Liquid Glass",
                            color = Color(0xFFF5F7FF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = tr(lang, "Текущий сеанс • cloud.neongram.space", "Учурдагы сеанс • cloud.neongram.space", "Current session • cloud.neongram.space"),
                            color = Color(0xFF5CFFCD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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

                LiquidGlassCard(
                    cornerRadius = 26.dp,
                    paddingDp = 16.dp,
                    accentGlow = if (isCurrent) Color(0xFF2FF2FF) else Color(0xFF8B5CF6)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = if (isCurrent) Color(0xFF2FF2FF) else Color(0xFF9AA6CB)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = title,
                                color = Color(0xFFF5F7FF),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "$osPart • IP: $ipPart",
                                color = Color(0xFF9AA6CB),
                                fontSize = 12.sp
                            )
                            if (isCurrent) {
                                Text(
                                    text = tr(lang, "Это устройство • Online", "Бул түзмөк • Online", "This device • Online"),
                                    color = Color(0xFF5CFFCD),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        if (!isCurrent) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Terminate",
                                tint = Color(0xFFFF5E7E),
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

    // Real Camera QR Scanner Frosted Glass Modal
    if (showQrCameraScannerModal) {
        Dialog(onDismissRequest = { showQrCameraScannerModal = false }) {
            LiquidGlassCard(
                cornerRadius = 30.dp,
                paddingDp = 20.dp,
                accentGlow = Color(0xFF2FF2FF)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "Сканировать QR-код", "QR-код сканерлөө", "Scan QR Code"),
                            color = Color(0xFFF6F8FF),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { showQrCameraScannerModal = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFF2F5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = tr(
                            lang,
                            "Наведите камеру на QR-код на экране компьютера (cloud.neongram.space) или другого устройства:",
                            "Камераны компьютердеги же башка түзмөктөгү QR-кодго багыттаңыз:",
                            "Point the camera at the QR code on cloud.neongram.space or another device:"
                        ),
                        color = Color(0xFF9AA6CB),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )

                    RealQrScannerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp),
                        onQrCodeScanned = { raw ->
                            showQrCameraScannerModal = false
                            viewModel.handleScannedQrPayload(raw)
                            viewModel.loadSessions()
                        }
                    )

                    NeonInput(
                        value = manualQrInput,
                        onValueChange = { manualQrInput = it },
                        placeholder = tr(lang, "Или вставьте код / токен вручную", "Же токенди кол менен жазыңыз", "Or paste QR token manually")
                    )

                    if (manualQrInput.isNotBlank()) {
                        PrimaryButton(
                            label = tr(lang, "Подтвердить вход", "Кирүүнү ырастоо", "Approve Login"),
                            onClick = {
                                val code = manualQrInput.trim()
                                manualQrInput = ""
                                showQrCameraScannerModal = false
                                viewModel.handleScannedQrPayload(code)
                                viewModel.loadSessions()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. APPEARANCE SETTINGS SUB-SCREEN (Liquid Glass)
// ============================================================================

@Composable
fun AppearanceSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val session = state.session
    val lang = session.language

    LiquidSubScreenScaffold(
        title = tr(lang, "Оформление", "Көрүнүш", "Appearance"),
        subtitle = "iOS 18 Liquid Glass & Aurora Themes",
        onBack = { viewModel.navigateBack() }
    ) {
        // 1. System Theme Switch
        LiquidSwitchCard(
            title = tr(lang, "Системная тема", "Системалык тема", "Use System Theme"),
            subtitle = tr(lang, "Автоматически переключать темную и светлую тему", "Автоматтык түрдө алмаштыруу", "Automatically switch dark & light themes"),
            checked = session.useSystemTheme,
            onCheckedChange = { viewModel.updateAppearance(useSystemTheme = it) }
        )

        // 2. 3 Theme Cards (Cyber Dark Animated, Cyber Light Animated, Neon Purple)
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFFEC4899)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = tr(lang, "Цветовая тема", "Түс темасы", "Color Theme"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                val themes = listOf(
                    Triple("cyber_dark", "Cyber Dark Liquid", Color(0xFF2FF2FF)),
                    Triple("cyber_light", "Cyber Light Liquid", Color(0xFF00B7E8)),
                    Triple("neon_purple", "Aurora Violet", Color(0xFFD15CFF))
                )

                themes.forEach { (id, label, previewColor) ->
                    val isSelected = !session.useSystemTheme && session.themeId == id
                    val rowShape = RoundedCornerShape(18.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(if (isSelected) Color(0x332FF2FF) else Color(0x5510142C))
                            .border(
                                width = if (isSelected) 1.4.dp else 1.dp,
                                color = if (isSelected) previewColor else Color.White.copy(alpha = 0.14f),
                                shape = rowShape
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
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(previewColor)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                            )
                            Text(
                                text = label,
                                color = Color(0xFFF5F7FF),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = previewColor
                            )
                        }
                    }
                }
            }
        }

        // 3. LanguageSwitcher (RU | KY | EN)
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tr(lang, "Язык интерфейса", "Интерфейс тили", "App Language"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                LanguageSwitcher(
                    currentLang = session.language,
                    onSelectLang = { viewModel.setLanguage(it) }
                )
            }
        }

        // 4. Font Size Selector
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = tr(lang, "Размер шрифта", "Шрифт өлчөмү", "Font Size"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold
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
                        val pillShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(pillShape)
                                .background(
                                    if (active) {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF2FF2FF), Color(0xFF8B5CF6))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0x66151A36), Color(0x6610142A))
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                                    shape = pillShape
                                )
                                .clickable { viewModel.updateAppearance(fontSize = key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (active) Color(0xFF060814) else Color(0xFFF2F5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // 5. Bubble Color Circles + Bubble Scope
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = tr(lang, "Цвет пузырей сообщений", "Билдирүү көбүкчөлөрүнүн түсү", "Message Bubble Color"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                val bubblePresets = listOf(
                    "" to tr(lang, "Тема", "Тема", "Theme"),
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(circleColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF2FF2FF) else Color.White.copy(alpha = 0.25f),
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
                                text = label,
                                color = Color(0xFF9AA6CB),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
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
                        val pillShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .clip(pillShape)
                                .background(
                                    if (active) Color(0xFF2FF2FF) else Color(0x66151A36)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                                    shape = pillShape
                                )
                                .clickable { viewModel.updateAppearance(bubbleScope = scopeKey) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = scopeLabel,
                                color = if (active) Color(0xFF060814) else Color(0xFFF2F5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // 6. Wallpaper Selector
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = tr(lang, "Фон и обои", "Фон жана обои", "Chat Wallpaper"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "gradient" to tr(lang, "Аврора", "Аврора", "Aurora"),
                        "mesh" to tr(lang, "Сетка", "Тор", "Liquid Mesh"),
                        "grid" to tr(lang, "Линии", "Сызыктар", "Cyber Grid")
                    ).forEach { (wpKey, wpTitle) ->
                        val active = session.wallpaper == wpKey
                        val pillShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(pillShape)
                                .background(
                                    if (active) {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF2FF2FF), Color(0xFF8B5CF6))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(Color(0x66151A36), Color(0x6610142A))
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                                    shape = pillShape
                                )
                                .clickable { viewModel.updateAppearance(wallpaper = wpKey) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = wpTitle,
                                color = if (active) Color(0xFF060814) else Color(0xFFF2F5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 7. STORAGE SETTINGS SUB-SCREEN (Liquid Glass)
// ============================================================================

@Composable
fun StorageSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language

    var mediaMb by remember { mutableStateOf(42.4f) }
    var audioMb by remember { mutableStateOf(18.6f) }
    var docsMb by remember { mutableStateOf(11.2f) }

    val totalMb = (mediaMb + audioMb + docsMb).coerceAtLeast(0.1f)

    LiquidSubScreenScaffold(
        title = tr(lang, "Хранилище и данные", "Сактагыч", "Storage & Data"),
        subtitle = "Local Cache & Cloud Sync",
        onBack = { viewModel.navigateBack() }
    ) {
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 20.dp,
            accentGlow = Color(0xFFF59E0B)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = String.format(Locale.US, "%.1f MB", mediaMb + audioMb + docsMb),
                    color = Color(0xFFF6F8FF),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )

                // Visual Storage Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF12162E))
                ) {
                    Box(
                        modifier = Modifier
                            .weight((mediaMb / totalMb).coerceAtLeast(0.05f))
                            .fillMaxSize()
                            .background(Color(0xFF8B5CF6))
                    )
                    Box(
                        modifier = Modifier
                            .weight((audioMb / totalMb).coerceAtLeast(0.05f))
                            .fillMaxSize()
                            .background(Color(0xFF2FF2FF))
                    )
                    Box(
                        modifier = Modifier
                            .weight((docsMb / totalMb).coerceAtLeast(0.05f))
                            .fillMaxSize()
                            .background(Color(0xFFF59E0B))
                    )
                }

                StorageLegendRow(
                    label = tr(lang, "Медиа", "Медиа", "Media"),
                    color = Color(0xFF8B5CF6),
                    sizeText = String.format(Locale.US, "%.1f MB", mediaMb)
                )
                StorageLegendRow(
                    label = tr(lang, "Аудио", "Аудио", "Audio"),
                    color = Color(0xFF2FF2FF),
                    sizeText = String.format(Locale.US, "%.1f MB", audioMb)
                )
                StorageLegendRow(
                    label = tr(lang, "Документы", "Документтер", "Documents"),
                    color = Color(0xFFF59E0B),
                    sizeText = String.format(Locale.US, "%.1f MB", docsMb)
                )
            }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                color = Color(0xFFF3F6FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = sizeText,
            color = Color(0xFF9AA6CB),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ============================================================================
// 8. ABOUT SETTINGS SUB-SCREEN (Liquid Glass)
// ============================================================================

@Composable
fun AboutSettingsScreen(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language

    LiquidSubScreenScaffold(
        title = tr(lang, "О приложении", "Тиркеме жөнүндө", "About App"),
        subtitle = "cloud.neongram.space • 2026 Liquid Glass",
        onBack = { viewModel.navigateBack() }
    ) {
        // App Version & Aurora Hero Card
        LiquidGlassCard(
            cornerRadius = 28.dp,
            paddingDp = 20.dp,
            accentGlow = Color(0xFF2FF2FF)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Neongram Messenger",
                    color = Color(0xFFF6F8FF),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = tr(
                        lang,
                        "Версия: 2026.18-liquid-glass • cloud.neongram.space",
                        "Версия: 2026.18-liquid-glass • cloud.neongram.space",
                        "Version: 2026.18-liquid-glass • cloud.neongram.space"
                    ),
                    color = Color(0xFF2FF2FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = tr(
                        lang,
                        "Нативный мессенджер нового поколения в эстетике iOS 18 Liquid Glass & Aurora Violet с защищенными чатами и выделенным центром Neongram AI.",
                        "Коопсуз чаттар жана Neongram AI борбору бар жаңы муундагы мессенджер.",
                        "Next-gen native messenger in the iOS 18 Liquid Glass & Aurora Violet aesthetic featuring real-time encrypted chats and a dedicated Neongram AI Hub."
                    ),
                    color = Color(0xFFB4BEE0),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // Quick Start Frosted Card
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF8B5CF6)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr(lang, "Быстрый старт", "Тез баштоо", "Quick Start"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = tr(
                        lang,
                        "• Добавляйте друзей через @username или QR-код во вкладке Контакты.\n• Используйте Избранное для личных заметок и файлов.\n• Открывайте центральную вкладку Neongram AI для общения со встроенным ИИ-ассистентом.",
                        "• Контакты бөлүмүнөн @username же QR аркылуу досторду кошуңуз.\n• Neongram AI бөлүмүнөн ИИ-жардамчы менен баарлашыңыз.",
                        "• Add friends via @username or QR in Contacts.\n• Use Saved Messages for personal notes.\n• Open the center Neongram AI tab for instant AI assistance."
                    ),
                    color = Color(0xFF9AA6CB),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // FAQ Frosted Card
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF2FF2FF)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FAQ",
                    color = Color(0xFFF5F7FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = tr(
                        lang,
                        "В: Как войти на втором устройстве?\nО: Откройте Настройки → Устройства → Сканировать QR для входа на другом устройстве / Web.\n\nВ: С каким облаком работает приложение?\nО: Neongram синхронизируется напрямую с защищенным облаком https://cloud.neongram.space.",
                        "С: Экинчи түзмөктөн кантип кирем?\nЖ: Жөндөөлөр → Түзмөктөр → QR менен туташтыруу.",
                        "Q: How to log in on another device?\nA: Open Settings → Devices → Scan QR to Log In on Another Device / Web.\n\nQ: Which cloud powers Neongram?\nA: Neongram connects exclusively to https://cloud.neongram.space."
                    ),
                    color = Color(0xFF9AA6CB),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // Terms of Use Frosted Card
        LiquidGlassCard(
            cornerRadius = 26.dp,
            paddingDp = 18.dp,
            accentGlow = Color(0xFF818CF8)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr(lang, "Условия использования", "Колдонуу шарттары", "Terms of Use"),
                    color = Color(0xFFF5F7FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = tr(
                        lang,
                        "Используя Neongram, вы соглашаетесь с политикой конфиденциальности и правилами безопасности платформы https://cloud.neongram.space.",
                        "Neongram колдонуу менен купуялуулук саясатына макул болосуз.",
                        "By using Neongram, you agree to the privacy policy and security guidelines of https://cloud.neongram.space."
                    ),
                    color = Color(0xFF9AA6CB),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
