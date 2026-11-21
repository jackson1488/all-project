package space.neongram.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.neongram.app.core.ui.AnimatedNeonBackground
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeongramTheme
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.ActiveOverlayScreen
import space.neongram.app.ui.NeongramUiState
import space.neongram.app.ui.NeongramViewModel

@Composable
fun NeongramRootScreen(viewModel: NeongramViewModel) {
    val state by viewModel.uiState.collectAsState()

    NeongramTheme(
        themeId = state.session.themeId,
        useSystemTheme = state.session.useSystemTheme,
        bubbleColorHex = state.session.bubbleColorHex,
        bubbleScope = state.session.bubbleScope,
        fontSize = state.session.fontSize,
        wallpaper = state.session.wallpaper
    ) {
        val scheme = LocalNeonScheme.current

        BackHandler(enabled = state.overlayScreen != ActiveOverlayScreen.NONE && !state.isAppLocked) {
            viewModel.navigateBack()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.bg)
                .systemBarsPadding()
        ) {
            AnimatedNeonBackground()

            when {
                !state.session.onboardingCompleted && !state.session.isAuthenticated -> {
                    OnboardingScreen(
                        lang = state.session.language,
                        onSelectLang = { viewModel.setLanguage(it) },
                        onFinish = { viewModel.completeOnboarding() }
                    )
                }

                !state.session.isAuthenticated -> {
                    if (state.overlayScreen == ActiveOverlayScreen.AUTH_QR) {
                        AuthQrScreen(
                            state = state,
                            onBack = { viewModel.navigateBack() },
                            onRegenerate = { viewModel.generateQrLoginToken() },
                            onApproveToken = { viewModel.handleScannedQrPayload(it) }
                        )
                    } else {
                        AuthScreen(
                            state = state,
                            onLogin = { u, p, url -> viewModel.login(u, p, url) },
                            onRegister = { u, p, cp, url -> viewModel.register(u, p, cp, url) },
                            onConfirm2fa = { code -> viewModel.confirmTwoFactor(code) },
                            onCancel2fa = { viewModel.cancelTwoFactor() },
                            onOpenQrLogin = { viewModel.navigateToOverlay(ActiveOverlayScreen.AUTH_QR) },
                            onSelectLang = { viewModel.setLanguage(it) }
                        )
                    }
                }

                state.session.isAuthenticated && state.isAppLocked -> {
                    PinLockOverlayScreen(
                        state = state,
                        onUnlock = { viewModel.unlockWithPin(it) }
                    )
                }

                else -> {
                    when (state.overlayScreen) {
                        ActiveOverlayScreen.NONE -> MainFiveTabsScaffold(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.AUTH_QR -> AuthQrScreen(
                            state = state,
                            onBack = { viewModel.navigateBack() },
                            onRegenerate = { viewModel.generateQrLoginToken() },
                            onApproveToken = { viewModel.handleScannedQrPayload(it) }
                        )
                        ActiveOverlayScreen.CHAT_ROOM -> ChatRoomScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.GROUP_INFO -> GroupInfoScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.PEER_PROFILE -> PeerProfileScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_ACCOUNT -> AccountSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_ACCOUNT_INFO -> AccountInfoEditScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_PRIVACY -> PrivacySettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_SECURITY -> SecuritySettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_DEVICES -> DevicesSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_APPEARANCE -> AppearanceSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_STORAGE -> StorageSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_ABOUT -> AboutSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        else -> MainFiveTabsScaffold(
                            state = state,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Frosted Liquid Glass Top Toast Banner
            val toastText = state.toastMessage ?: state.errorMessage
            LaunchedEffect(toastText) {
                if (toastText != null) {
                    delay(2200)
                    viewModel.clearToast()
                }
            }

            AnimatedVisibility(
                visible = toastText != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp, start = 16.dp, end = 16.dp)
            ) {
                val isError = state.errorMessage != null
                val toastShape = RoundedCornerShape(22.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 18.dp,
                            shape = toastShape,
                            ambientColor = if (isError) Color(0x66FF4D6D) else Color(0x662FF2FF),
                            spotColor = if (isError) Color(0x80FF4D6D) else Color(0x808B5CF6)
                        )
                        .clip(toastShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xDD171B3A),
                                    Color(0xCC0F1228)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = if (isError) {
                                    listOf(
                                        Color(0x99FF4D6D),
                                        Color.White.copy(alpha = 0.25f)
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = 0.38f),
                                        Color(0xFF2FF2FF).copy(alpha = 0.32f),
                                        Color(0xFFA855F7).copy(alpha = 0.24f)
                                    )
                                }
                            ),
                            shape = toastShape
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = toastText.orEmpty(),
                        color = if (isError) Color(0xFFFF6B8B) else Color(0xFFF3F5FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearToast() }) {
                        Text(
                            text = "OK",
                            color = Color(0xFF2FF2FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

private data class LiquidDockTabItem(
    val index: Int,
    val title: String,
    val icon: ImageVector
)

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet 4-Tab Scaffold (`template_3_ios_liquid_aurora`).
 * Features a Floating Frosted Liquid Glass Dock Capsule overlaying the bottom of the screen
 * (`Color(0x9912162E)` translucent frosted glass + `1.dp` specular border) so the chat list
 * scrolls smoothly behind the floating glass dock.
 */
@Composable
fun MainFiveTabsScaffold(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val lang = state.session.language

    val tabs = remember(lang) {
        listOf(
            LiquidDockTabItem(
                index = 0,
                title = tr(lang, "Чаты", "Чаттар", "Chats"),
                icon = Icons.Default.Forum
            ),
            LiquidDockTabItem(
                index = 1,
                title = tr(lang, "Контакты", "Байланыштар", "Contacts"),
                icon = Icons.Default.People
            ),
            LiquidDockTabItem(
                index = 2,
                title = tr(lang, "Настройки", "Баптоолор", "Settings"),
                icon = Icons.Default.Settings
            ),
            LiquidDockTabItem(
                index = 3,
                title = tr(lang, "Профиль", "Профиль", "Profile"),
                icon = Icons.Default.Person
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Screen content fills the entire height so lists scroll smoothly behind the floating glass dock
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.selectedTabIndex) {
                0 -> ChatsTabScreen(state = state, viewModel = viewModel)
                1 -> ContactsTabScreen(state = state, viewModel = viewModel)
                2 -> SettingsTabScreen(state = state, viewModel = viewModel)
                3, 4 -> ProfileTabScreen(state = state, viewModel = viewModel)
                else -> ChatsTabScreen(state = state, viewModel = viewModel)
            }
        }

        // Floating Frosted Liquid Glass Dock Capsule overlaying bottom
        val dockShape = RoundedCornerShape(30.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp)
                .height(74.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = dockShape,
                    ambientColor = Color(0x6600F0FF),
                    spotColor = Color(0x887C3AED)
                )
                .clip(dockShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x9912162E),
                            Color(0x990D1024)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(900f, 220f)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color(0xFF2FF2FF).copy(alpha = 0.28f),
                            Color(0xFFA855F7).copy(alpha = 0.22f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(900f, 240f)
                    ),
                    shape = dockShape
                )
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = state.selectedTabIndex == tab.index ||
                        (tab.index == 3 && state.selectedTabIndex == 4)
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.04f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tabScale_${tab.index}"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) Color(0xFF2FF2FF) else Color(0xFF9EA9CC),
                        label = "tabIconTint_${tab.index}"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) Color(0xFF2FF2FF) else Color(0xFF9EA9CC),
                        label = "tabLabelColor_${tab.index}"
                    )

                    val pillShape = RoundedCornerShape(23.dp)
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(scale)
                            .clip(pillShape)
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0x3D2FF2FF),
                                                    Color(0x338B5CF6)
                                                ),
                                                start = Offset(0f, 0f),
                                                end = Offset(200f, 160f)
                                            )
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.42f),
                                                    Color(0xFF2FF2FF).copy(alpha = 0.55f),
                                                    Color(0xFFA855F7).copy(alpha = 0.45f)
                                                )
                                            ),
                                            shape = pillShape
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                viewModel.selectTab(tab.index)
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selected) {
                                    // Subtle cyan/violet glow halo behind active icon
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        Color(0x442FF2FF),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                }
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = labelColor,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
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

/**
 * Full-screen Liquid Glass PIN Lock Overlay with a 4-dot glowing cyan indicator
 * and frosted glass circular numpad (0..9, backspace).
 */
@Composable
fun PinLockOverlayScreen(
    state: NeongramUiState,
    onUnlock: (String) -> Boolean
) {
    val lang = state.session.language
    val targetLength = state.session.pinCode.length.coerceIn(4, 8)
    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    fun appendDigit(digit: String) {
        if (enteredPin.length < targetLength) {
            val next = enteredPin + digit
            enteredPin = next
            hasError = false
            if (next.length == targetLength) {
                val ok = onUnlock(next)
                if (!ok) {
                    hasError = true
                    enteredPin = ""
                }
            }
        }
    }

    fun deleteLast() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            hasError = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEE080B1A),
                        Color(0xF50E132B),
                        Color(0xFA060813)
                    )
                )
            )
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Glowing Lock Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 22.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x882FF2FF),
                        spotColor = Color(0x888B5CF6)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0x3D2FF2FF),
                                Color(0x338B5CF6)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color(0xFF2FF2FF).copy(alpha = 0.6f),
                                Color(0xFFA855F7).copy(alpha = 0.45f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "PIN Lock",
                    tint = Color(0xFF2FF2FF),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = tr(lang, "Введите PIN-код", "PIN-кодду киргизиңиз", "Enter PIN Code"),
                color = Color(0xFFF3F5FF),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasError) {
                    tr(lang, "Неверный PIN-код, попробуйте снова", "Туура эмес PIN-код", "Incorrect PIN code, try again")
                } else {
                    tr(lang, "Neongram защищён кодом доступа", "Neongram PIN-код менен корголгон", "Neongram is protected with a passcode")
                },
                color = if (hasError) Color(0xFFFF5C8A) else Color(0xFF9EA9CC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4-dot (or targetLength) glowing cyan indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until targetLength) {
                    val filled = i < enteredPin.length
                    val dotColor by animateColorAsState(
                        targetValue = when {
                            hasError -> Color(0xFFFF4D6D)
                            filled -> Color(0xFF2FF2FF)
                            else -> Color(0x339EA9CC)
                        },
                        label = "pinDot_$i"
                    )
                    val dotScale by animateFloatAsState(
                        targetValue = if (filled) 1.18f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "pinDotScale_$i"
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .scale(dotScale)
                            .then(
                                if (filled) {
                                    Modifier.shadow(
                                        elevation = 12.dp,
                                        shape = CircleShape,
                                        ambientColor = Color(0xAA2FF2FF),
                                        spotColor = Color(0xAA2FF2FF)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(
                                width = 1.dp,
                                color = if (filled) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(38.dp))

            // Frosted Glass Circular Numpad (1..9, 0, backspace)
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "" -> {
                                    Spacer(modifier = Modifier.size(74.dp))
                                }
                                "DEL" -> {
                                    PinNumpadCircleButton(
                                        onClick = { deleteLast() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = Color(0xFFE2E8F0),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                else -> {
                                    PinNumpadCircleButton(
                                        onClick = { appendDigit(key) }
                                    ) {
                                        Text(
                                            text = key,
                                            color = Color(0xFFF8FAFC),
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Bold
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

@Composable
private fun PinNumpadCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = Color(0x332FF2FF),
                spotColor = Color(0x448B5CF6)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0x99192042),
                        Color(0x8010142C)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(140f, 140f)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color(0xFF2FF2FF).copy(alpha = 0.25f),
                        Color(0xFFA855F7).copy(alpha = 0.20f)
                    )
                ),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
