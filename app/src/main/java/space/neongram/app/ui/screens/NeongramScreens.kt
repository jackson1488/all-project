package space.neongram.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                        ActiveOverlayScreen.SERVICE_WEATHER -> WeatherServiceScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SERVICE_CURRENCY -> CurrencyServiceScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SERVICE_MUSIC -> MusicServiceScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SERVICE_GAMES -> GamesServiceScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SERVICE_WALLET -> WalletServiceScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SERVICE_BANK -> BankServiceScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SERVICE_BROWSER -> BrowserServiceScreen(
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
                        ActiveOverlayScreen.SETTINGS_SERVER -> ServerSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                        ActiveOverlayScreen.SETTINGS_ABOUT -> AboutSettingsScreen(
                            state = state,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // 1:1 Top Toast Banner (ToastProvider.jsx)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surface)
                        .border(0.8.dp, scheme.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = toastText.orEmpty(),
                        color = if (state.errorMessage != null) scheme.danger else scheme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearToast() }) {
                        Text(
                            text = "OK",
                            color = scheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainFiveTabsScaffold(
    state: NeongramUiState,
    viewModel: NeongramViewModel
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = scheme.surface,
                contentColor = scheme.text,
                tonalElevation = 0.dp
            ) {
                val tabs = listOf(
                    Triple(0, tr(lang, "Чаты", "Чаттар", "Chats"), Icons.Default.Forum),
                    Triple(1, tr(lang, "Контакты", "Байланыштар", "Contacts"), Icons.Default.People),
                    Triple(2, tr(lang, "Сервисы", "Кызматтар", "Services"), Icons.Default.Apps),
                    Triple(3, tr(lang, "Настройки", "Баптоолор", "Settings"), Icons.Default.Settings),
                    Triple(4, tr(lang, "Профиль", "Профиль", "Profile"), Icons.Default.Person)
                )

                tabs.forEach { (index, title, icon) ->
                    val selected = state.selectedTabIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(index) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = scheme.primary,
                            selectedTextColor = scheme.primary,
                            unselectedIconColor = scheme.muted,
                            unselectedTextColor = scheme.muted,
                            indicatorColor = scheme.primary.copy(alpha = 0.14f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (state.selectedTabIndex) {
                0 -> ChatsTabScreen(state = state, viewModel = viewModel)
                1 -> ContactsTabScreen(state = state, viewModel = viewModel)
                2 -> ServicesTabScreen(state = state, viewModel = viewModel)
                3 -> SettingsTabScreen(state = state, viewModel = viewModel)
                4 -> ProfileTabScreen(state = state, viewModel = viewModel)
            }
        }
    }
}
