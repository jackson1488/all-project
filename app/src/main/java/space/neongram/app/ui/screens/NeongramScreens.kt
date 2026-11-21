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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
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
import androidx.compose.runtime.remember
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

        BackHandler(enabled = state.overlayScreen != ActiveOverlayScreen.NONE) {
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
                            onApproveToken = { viewModel.approveQrToken(it) }
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
                            onApproveToken = { viewModel.approveQrToken(it) }
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
    val icon: ImageVector,
    val isAiCenter: Boolean = false
)

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet 5-Tab Scaffold (`template_3_ios_liquid_aurora`).
 * Features a Floating Frosted Liquid Glass Dock Capsule at the bottom (`paddingHorizontal = 16.dp`,
 * `paddingBottom = 14.dp`, `RoundedCornerShape(30.dp)`, `height = 74.dp`) with specular diagonal
 * refraction border and glowing cyan/violet active pill indicator.
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
                title = "Neongram AI",
                icon = Icons.Default.AutoAwesome,
                isAiCenter = true
            ),
            LiquidDockTabItem(
                index = 3,
                title = tr(lang, "Настройки", "Баптоолор", "Settings"),
                icon = Icons.Default.Settings
            ),
            LiquidDockTabItem(
                index = 4,
                title = tr(lang, "Профиль", "Профиль", "Profile"),
                icon = Icons.Default.Person
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Screen content with bottom padding for the floating dock capsule
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp)
        ) {
            when (state.selectedTabIndex) {
                0 -> ChatsTabScreen(state = state, viewModel = viewModel)
                1 -> ContactsTabScreen(state = state, viewModel = viewModel)
                2 -> ServicesTabScreen(state = state, viewModel = viewModel)
                3 -> SettingsTabScreen(state = state, viewModel = viewModel)
                4 -> ProfileTabScreen(state = state, viewModel = viewModel)
            }
        }

        // Floating Frosted Liquid Glass Dock Capsule
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
                            Color(0xCC141834),
                            Color(0xB30E1126)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(900f, 220f)
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color(0xFF2FF2FF).copy(alpha = 0.25f),
                            Color(0xFFA855F7).copy(alpha = 0.20f)
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = state.selectedTabIndex == tab.index
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.04f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "tabScale_${tab.index}"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = when {
                            selected -> Color(0xFF2FF2FF)
                            tab.isAiCenter -> Color(0xFFD8B4FE)
                            else -> Color(0xFF9EA9CC)
                        },
                        label = "tabIconTint_${tab.index}"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = when {
                            selected -> Color(0xFF2FF2FF)
                            tab.isAiCenter -> Color(0xFFE9D5FF)
                            else -> Color(0xFF9EA9CC)
                        },
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
                                    modifier = Modifier.size(if (tab.isAiCenter) 23.dp else 22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = labelColor,
                                fontSize = 10.sp,
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
