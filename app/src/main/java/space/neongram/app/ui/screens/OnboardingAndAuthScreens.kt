package space.neongram.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import space.neongram.app.core.ui.AnimatedNeonBackground
import space.neongram.app.core.ui.LanguageSwitcher
import space.neongram.app.core.ui.LocalNeonScheme
import space.neongram.app.core.ui.NeonInput
import space.neongram.app.core.ui.PageScaffold
import space.neongram.app.core.ui.PrimaryButton
import space.neongram.app.core.ui.tr
import space.neongram.app.ui.NeongramUiState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class OnboardingSlideData(
    val key: String,
    val title: String,
    val desc: String,
    val gradientColors: List<Color>
)

private val OnboardingGradientSets = listOf(
    listOf(Color(0xFF0A0320), Color(0xFF1A0845), Color(0xFF2E0B6E)),
    listOf(Color(0xFF05021A), Color(0xFF0E0538), Color(0xFF220B5C)),
    listOf(Color(0xFF080220), Color(0xFF14064A), Color(0xFF2A0B72)),
    listOf(Color(0xFF06021A), Color(0xFF100640), Color(0xFF200960)),
    listOf(Color(0xFF0B0325), Color(0xFF1C084E), Color(0xFF320E7A))
)

/**
 * 1:1 with `OnboardingScreen.jsx`:
 * 5 slides (`Security First`, `Instant Chats`, `HD Calls`, `Neongram AI`, `Welcome`),
 * per-slide neon purple gradients (`#0A0320` -> `#2E0B6E`, etc.), glowing ring (`190.dp`),
 * custom drawn neon icon (`Shield`, `Chat`, `Call`, `AI` with orbiting dots, `Rocket`),
 * animated dots row (`26.dp` active pill dot vs `8.dp` inactive dot, `#C084FC`),
 * `PrimaryButton` (`Далее` / `Начать`), and `Skip` button (`Пропустить`).
 */
@Composable
fun OnboardingScreen(
    lang: String,
    onSelectLang: (String) -> Unit,
    onFinish: () -> Unit
) {
    var activeSlide by remember { mutableIntStateOf(0) }
    val neonColor = Color(0xFFC084FC)

    val slides = remember(lang) {
        listOf(
            OnboardingSlideData(
                key = "security",
                title = tr(lang, "Безопасность", "Коопсуздук", "Security First"),
                desc = tr(
                    lang,
                    "Сквозное шифрование для всех ваших сообщений. Ваши данные — только ваши.",
                    "Бардык билдирүүлөрүңүз үчүн учтан-учка шифрлөө. Маалыматтарыңыз — сиздики гана.",
                    "End-to-end encryption for all your messages. Your data belongs only to you."
                ),
                gradientColors = OnboardingGradientSets[0]
            ),
            OnboardingSlideData(
                key = "chat",
                title = tr(lang, "Мгновенные чаты", "Ыкчам чаттар", "Instant Chats"),
                desc = tr(
                    lang,
                    "Быстрые сообщения, фото, видео, голосовые и файлы — всё в одном месте.",
                    "Тез билдирүүлөр, сүрөттөр, видеолор, үн жана файлдар — баарысы бир жерде.",
                    "Fast messages, photos, videos, voice notes and files — all in one place."
                ),
                gradientColors = OnboardingGradientSets[1]
            ),
            OnboardingSlideData(
                key = "calls",
                title = tr(lang, "HD Звонки", "HD Чалуулар", "HD Calls"),
                desc = tr(
                    lang,
                    "Кристально чистые аудио и видеозвонки. Оставайтесь на связи в любой точке мира.",
                    "Тунук аудио жана видео чалуулар. Дүйнөнүн каалаган жеринде байланышта болуңуз.",
                    "Crystal clear audio and video calls. Stay connected anywhere in the world."
                ),
                gradientColors = OnboardingGradientSets[2]
            ),
            OnboardingSlideData(
                key = "ai",
                title = tr(lang, "Neongram AI", "Neongram AI", "Neongram AI"),
                desc = tr(
                    lang,
                    "Встроенный AI-ассистент поможет с вопросами и задачами прямо в чате.",
                    "Камтылган AI-жардамчы суроолор жана тапшырмалар менен чатта жардам берет.",
                    "Built-in AI assistant to help with questions and tasks right in your chat."
                ),
                gradientColors = OnboardingGradientSets[3]
            ),
            OnboardingSlideData(
                key = "start",
                title = tr(lang, "Добро пожаловать", "Кош келиңиз", "Welcome"),
                desc = tr(
                    lang,
                    "Всё готово. Создайте аккаунт и начните общение в Neongram.",
                    "Баары даяр. Аккаунт түзүп, Neongram менен баарлашууну баштаңыз.",
                    "All set. Create your account and start chatting on Neongram."
                ),
                gradientColors = OnboardingGradientSets[4]
            )
        )
    }

    val currentSlide = slides[activeSlide.coerceIn(0, slides.lastIndex)]
    val isLast = activeSlide == slides.lastIndex

    val infiniteTransition = rememberInfiniteTransition(label = "OnboardingPulse")
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = currentSlide.gradientColors,
                    start = Offset(100f, 0f),
                    end = Offset(900f, 1800f)
                )
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Logo + LanguageSwitcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Neongram",
                    color = Color(0xFFF1E7FF),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                LanguageSwitcher(
                    currentLang = lang,
                    onSelectLang = onSelectLang
                )
            }

            // Center Slide Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = activeSlide,
                    transitionSpec = {
                        (fadeIn(tween(320)) + scaleIn(initialScale = 0.88f, animationSpec = tween(320)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180)))
                    },
                    label = "OnboardingSlideTransition"
                ) { slideIndex ->
                    val slide = slides[slideIndex]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(bottom = 18.dp)
                        ) {
                            // 190.dp Glow Ring behind icon
                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x10C084FC))
                                    .border(1.5.dp, Color(0x26C084FC), CircleShape)
                            )

                            // Custom drawn/vector neon icon
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .scale(iconPulse),
                                contentAlignment = Alignment.Center
                            ) {
                                OnboardingSlideIcon(
                                    slideIndex = slideIndex,
                                    color = neonColor
                                )
                            }
                        }

                        Text(
                            text = slide.title,
                            color = Color(0xFFF1E7FF),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = slide.desc,
                            color = Color(0xFFF1E7FF).copy(alpha = 0.65f),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            // Dots Indicator Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.forEachIndexed { index, _ ->
                    val isActive = index == activeSlide
                    val dotWidth by animateDpAsState(
                        targetValue = if (isActive) 26.dp else 8.dp,
                        animationSpec = tween(250),
                        label = "dotWidth_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .width(dotWidth)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isActive) neonColor else Color.White.copy(alpha = 0.18f)
                            )
                            .clickable { activeSlide = index }
                    )
                }
            }

            // Footer: PrimaryButton + Skip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PrimaryButton(
                    label = if (isLast) {
                        tr(lang, "Начать", "Баштоо", "Get Started")
                    } else {
                        tr(lang, "Далее", "Кийинки", "Next")
                    },
                    onClick = {
                        if (!isLast) {
                            activeSlide += 1
                        } else {
                            onFinish()
                        }
                    }
                )

                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onFinish)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Пропустить", "Өткөрүп жиберүү", "Skip"),
                            color = Color(0xFFF1E7FF).copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(34.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingSlideIcon(
    slideIndex: Int,
    color: Color
) {
    when (slideIndex) {
        0 -> {
            // 1:1 IconShield: 64x76 rounded top shield with keyhole circle + vertical stem
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(76.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 32.dp,
                            topEnd = 32.dp,
                            bottomStart = 6.dp,
                            bottomEnd = 6.dp
                        )
                    )
                    .border(
                        width = 2.5.dp,
                        color = color,
                        shape = RoundedCornerShape(
                            topStart = 32.dp,
                            topEnd = 32.dp,
                            bottomStart = 6.dp,
                            bottomEnd = 6.dp
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(2.5.dp, color, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
        }
        1 -> {
            // 1:1 IconChat: 72x52 chat bubble with 3 horizontal message bars + bottom-left tail
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(52.dp)
                        .border(2.5.dp, color, RoundedCornerShape(18.dp))
                        .padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.9f))
                        )
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.6f))
                        )
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.4f))
                        )
                    }
                }
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 18.dp)
                        .size(14.dp)
                ) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width * 0.35f, size.height)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }
        2 -> {
            // 1:1 IconCall: 62x62 circle + rotated handset shape + concentric ring waves
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                listOf(80.dp, 94.dp, 108.dp).forEachIndexed { idx, ringSize ->
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .border(
                                width = 1.dp,
                                color = color.copy(alpha = (0.22f - idx * 0.06f).coerceAtLeast(0.05f)),
                                shape = CircleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .border(2.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(135f)
                            .border(
                                width = 2.5.dp,
                                color = color,
                                shape = RoundedCornerShape(
                                    topStart = 14.dp,
                                    topEnd = 6.dp,
                                    bottomEnd = 14.dp,
                                    bottomStart = 6.dp
                                )
                            )
                    )
                }
            }
        }
        3 -> {
            // 1:1 IconAI: 58x58 circle with "AI" bold text + 6 orbiting dots at radius 44.dp
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .border(2.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        color = color,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
                listOf(0, 60, 120, 180, 240, 300).forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val offsetX = (cos(rad) * 44.0).dp
                    val offsetY = (sin(rad) * 44.0).dp
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.55f))
                    )
                }
            }
        }
        else -> {
            // 1:1 IconRocket: 38x64 rocket body + porthole dot + side fins + 3 exhaust flames
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                // Left fin
                Box(
                    modifier = Modifier
                        .offset(x = (-24).dp, y = 14.dp)
                        .width(14.dp)
                        .height(20.dp)
                        .rotate(15f)
                        .border(2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                )
                // Right fin
                Box(
                    modifier = Modifier
                        .offset(x = 24.dp, y = 14.dp)
                        .width(14.dp)
                        .height(20.dp)
                        .rotate(-15f)
                        .border(2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                )
                // Rocket hull
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(64.dp)
                        .border(
                            width = 2.5.dp,
                            color = color,
                            shape = RoundedCornerShape(
                                topStart = 19.dp,
                                topEnd = 19.dp,
                                bottomStart = 6.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .padding(top = 14.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
                // Exhaust flames
                listOf(-8, 0, 8).forEachIndexed { idx, xOff ->
                    val flameHeight = (16 - abs(xOff)).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(x = xOff.dp, y = (-6).dp)
                            .width(5.dp)
                            .height(flameHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = 0.38f + idx * 0.1f))
                    )
                }
            }
        }
    }
}

private data class ServerOptionItem(
    val id: String,
    val name: String,
    val url: String,
    val isOnline: Boolean = true
)

/**
 * 1:1 with `AuthScreen.jsx`:
 * Header with "Neongram" (30.sp bold) + `LanguageSwitcher`, Card (`borderRadius = 20.dp`, `surface` bg, `border`),
 * Form Title (`Войти в аккаунт` / `Создать аккаунт`), Subtitle (`Neongram работает как быстрый личный мессенджер.`),
 * Server Selector Row (`Выбор сервера: Основной сервер / Автономный сервер` opening Bottom Sheet / Modal Dialog
 * to pick `https://cloud.neongram.space` or custom URL with green status dot `#10B981`),
 * `NeonInput` for Username, Password (with eye toggle), Confirm Password (in register mode),
 * inline error text in `danger`, `PrimaryButton` (`Войти` / `Зарегистрироваться`),
 * `Войти через QR` link, `Нет аккаунта? Зарегистрироваться` / `Уже есть аккаунт? Войти` link,
 * `Помощь` modal/toast, and 2FA Challenge card when `state.twoFactorChallengeId != null`.
 */
@Composable
fun AuthScreen(
    state: NeongramUiState,
    onLogin: (String, String, String) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onConfirm2fa: (String) -> Unit,
    onCancel2fa: () -> Unit,
    onOpenQrLogin: () -> Unit,
    onSelectLang: (String) -> Unit
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language.ifBlank { "ru" }

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var twoFactorCode by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    var selectedServerUrl by remember(state.session.serverUrl) {
        mutableStateOf(state.session.serverUrl.ifBlank { "https://cloud.neongram.space" })
    }
    var customServerUrl by remember { mutableStateOf(selectedServerUrl) }
    var showServerModal by remember { mutableStateOf(false) }
    var showHelpModal by remember { mutableStateOf(false) }

    val serverOptions = remember(lang) {
        listOf(
            ServerOptionItem(
                id = "remote",
                name = tr(lang, "Автономный сервер", "Автономдуу сервер", "Autonomous Server"),
                url = "https://cloud.neongram.space",
                isOnline = true
            ),
            ServerOptionItem(
                id = "main",
                name = tr(lang, "Основной сервер", "Негизги сервер", "Main Server"),
                url = "https://neongram.space",
                isOnline = true
            )
        )
    }

    val currentServerLabel = remember(selectedServerUrl, serverOptions, lang) {
        serverOptions.firstOrNull {
            it.url.equals(selectedServerUrl.trimEnd('/'), ignoreCase = true)
        }?.name ?: tr(lang, "Свой сервер", "Өз сервери", "Custom Server")
    }

    val effectiveError = localError ?: state.errorMessage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.bg)
    ) {
        AnimatedNeonBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Header with "Neongram" (30.sp bold) + LanguageSwitcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Neongram",
                    color = scheme.text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                LanguageSwitcher(
                    currentLang = lang,
                    onSelectLang = onSelectLang
                )
            }

            // Auth Card (marginTop = 24.dp, marginHorizontal = 16.dp, borderRadius = 20.dp)
            Column(
                modifier = Modifier
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.dp, scheme.border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.twoFactorChallengeId != null) {
                    // 2FA Challenge Card
                    Text(
                        text = tr(lang, "Двухфакторная проверка", "Эки факторлуу текшерүү", "Two-factor verification"),
                        color = scheme.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            lang,
                            "Введите 6-значный код из приложения-аутентификатора или код, отправленный на ваше устройство.",
                            "Аутентификатор колдонмосундагы же түзмөгүңүзгө жөнөтүлгөн 6 орундуу кодду жазыңыз.",
                            "Enter the 6-digit code from your authenticator app or the code sent to your device."
                        ),
                        color = scheme.muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    NeonInput(
                        value = twoFactorCode,
                        onValueChange = { raw ->
                            twoFactorCode = raw.filter { it.isDigit() }.take(6)
                            localError = null
                        },
                        placeholder = "000000",
                        keyboardType = KeyboardType.Number
                    )

                    if (!effectiveError.isNullOrBlank()) {
                        Text(
                            text = effectiveError,
                            color = scheme.danger,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PrimaryButton(
                        label = if (state.isLoading) {
                            tr(lang, "Загрузка...", "Жүктөлүүдө...", "Loading...")
                        } else {
                            tr(lang, "Подтвердить вход", "Кирүүнү ырастоо", "Confirm sign in")
                        },
                        enabled = !state.isLoading,
                        onClick = {
                            if (twoFactorCode.length != 6) {
                                localError = tr(lang, "Введите 6-значный код", "6 орундуу кодду жазыңыз", "Enter the 6-digit code")
                            } else {
                                onConfirm2fa(twoFactorCode)
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                twoFactorCode = ""
                                localError = null
                                onCancel2fa()
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Назад к паролю", "Сырсөзгө кайтуу", "Back to password"),
                            color = scheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Login / Register Card
                    Text(
                        text = if (isRegisterMode) {
                            tr(lang, "Создать аккаунт", "Аккаунт түзүү", "Create account")
                        } else {
                            tr(lang, "Войти в аккаунт", "Аккаунтка кирүү", "Login to account")
                        },
                        color = scheme.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = tr(
                            lang,
                            "Neongram работает как быстрый личный мессенджер.",
                            "Neongram тез жеке мессенжер катары иштейт.",
                            "Neongram is built as a fast personal messenger."
                        ),
                        color = scheme.muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Server Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.bg)
                            .border(1.dp, scheme.border, RoundedCornerShape(12.dp))
                            .clickable {
                                customServerUrl = selectedServerUrl
                                showServerModal = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${tr(lang, "Выбор сервера", "Серверди тандоо", "Server")}: $currentServerLabel",
                                color = scheme.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = scheme.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    NeonInput(
                        value = username,
                        onValueChange = {
                            username = it
                            localError = null
                        },
                        placeholder = tr(lang, "Логин", "Логин", "Username")
                    )

                    NeonInput(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                        },
                        placeholder = tr(lang, "Пароль", "Сырсөз", "Password"),
                        isPassword = true
                    )

                    if (isRegisterMode) {
                        NeonInput(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                localError = null
                            },
                            placeholder = tr(lang, "Подтвердите пароль", "Сырсөздү ырастаңыз", "Confirm password"),
                            isPassword = true
                        )
                    }

                    if (!effectiveError.isNullOrBlank()) {
                        Text(
                            text = effectiveError,
                            color = scheme.danger,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PrimaryButton(
                        label = if (state.isLoading) {
                            tr(lang, "Загрузка...", "Жүктөлүүдө...", "Loading...")
                        } else if (isRegisterMode) {
                            tr(lang, "Зарегистрироваться", "Катталуу", "Register")
                        } else {
                            tr(lang, "Войти", "Кирүү", "Login")
                        },
                        enabled = !state.isLoading,
                        modifier = Modifier.padding(top = 6.dp),
                        onClick = {
                            val cleanUser = username.trim()
                            val cleanPass = password.trim()
                            val cleanConfirm = confirmPassword.trim()
                            if (cleanUser.isEmpty() || cleanPass.isEmpty() || (isRegisterMode && cleanConfirm.isEmpty())) {
                                localError = tr(lang, "Заполните все поля", "Бардык талааларды толтуруңуз", "Fill all fields")
                                return@PrimaryButton
                            }
                            if (isRegisterMode && cleanPass != cleanConfirm) {
                                localError = tr(lang, "Пароли не совпадают", "Сырсөздөр дал келбейт", "Passwords do not match")
                                return@PrimaryButton
                            }
                            localError = null
                            if (isRegisterMode) {
                                onRegister(cleanUser, cleanPass, cleanConfirm, selectedServerUrl)
                            } else {
                                onLogin(cleanUser, cleanPass, selectedServerUrl)
                            }
                        }
                    )

                    // Войти через QR link
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenQrLogin)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Войти через QR", "QR аркылуу кирүү", "Log In via QR"),
                            color = scheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Switch Login / Register link
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isRegisterMode = !isRegisterMode
                                localError = null
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRegisterMode) {
                                tr(lang, "Уже есть аккаунт? Войти", "Аккаунт барбы? Кирүү", "Already have an account? Sign in")
                            } else {
                                tr(lang, "Нет аккаунта? Зарегистрироваться", "Аккаунт жокпу? Катталуу", "No account? Register")
                            },
                            color = scheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Помощь link
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showHelpModal = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Помощь", "Жардам", "Help"),
                            color = scheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Server Selection Modal Dialog (1:1 with AuthScreen.jsx bottomSheet)
    if (showServerModal) {
        Dialog(
            onDismissRequest = { showServerModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showServerModal = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(scheme.surface)
                        .clickable(enabled = false) {}
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF6B7280).copy(alpha = 0.5f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tr(lang, "Выбор сервера", "Серверди тандоо", "Select Server"),
                        color = scheme.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    serverOptions.forEach { option ->
                        val isSelected = selectedServerUrl.trimEnd('/').equals(option.url.trimEnd('/'), ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) scheme.bg else scheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) scheme.primary else scheme.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    selectedServerUrl = option.url
                                    customServerUrl = option.url
                                    showServerModal = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (option.isOnline) Color(0xFF10B981) else Color(0xFF6B7280))
                                )
                                Column {
                                    Text(
                                        text = option.name,
                                        color = scheme.text,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = option.url.removePrefix("https://").removePrefix("http://"),
                                        color = scheme.muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = tr(lang, "Или укажите свой URL сервера:", "Же өз сервер URL жазыңыз:", "Or enter custom server URL:"),
                        color = scheme.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    NeonInput(
                        value = customServerUrl,
                        onValueChange = { customServerUrl = it },
                        placeholder = "https://cloud.neongram.space"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PrimaryButton(
                                label = tr(lang, "Применить URL", "Колдонуу", "Apply URL"),
                                onClick = {
                                    val clean = customServerUrl.trim().trimEnd('/')
                                    if (clean.isNotBlank()) {
                                        selectedServerUrl = if (clean.startsWith("http://") || clean.startsWith("https://")) {
                                            clean
                                        } else {
                                            "https://$clean"
                                        }
                                    }
                                    showServerModal = false
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showServerModal = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Закрыть", "Жабуу", "Close"),
                            color = scheme.muted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Help FAQ Modal (1:1 with translations.js `help.*`)
    if (showHelpModal) {
        Dialog(onDismissRequest = { showHelpModal = false }) {
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
                    text = tr(lang, "Помощь • FAQ", "Жардам • FAQ", "Help • FAQ"),
                    color = scheme.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceAlt)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr(lang, "Как подключить другой сервер?", "Башка серверди кантип туташтырам?", "How to connect another server?"),
                        color = scheme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            lang,
                            "Нажмите «Выбор сервера» на экране входа или откройте Настройки -> Сервер и укажите URL.",
                            "Кирүү экранынан «Серверди тандоо» басыңыз же Баптоолор -> Серверге өтүп URL жазыңыз.",
                            "Tap 'Server' on the login screen or open Settings -> Server and enter the URL."
                        ),
                        color = scheme.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceAlt)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr(lang, "Как войти по QR-коду?", "QR-код аркылуу кантип кирем?", "How to sign in via QR code?"),
                        color = scheme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            lang,
                            "Откройте «Войти через QR» и отсканируйте код с уже авторизованного устройства Neongram.",
                            "«QR аркылуу кирүү» ачып, мурунтан кирген Neongram түзмөгүнөн кодду скандаңыз.",
                            "Open 'Log In via QR' and scan the code from an already authorized Neongram device."
                        ),
                        color = scheme.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                PrimaryButton(
                    label = tr(lang, "Закрыть", "Жабуу", "Close"),
                    onClick = { showHelpModal = false }
                )
            }
        }
    }
}

/**
 * 1:1 with `AuthQrScreen.jsx`:
 * Wrapped in `PageScaffold(title = tr(lang, "Вход по QR", "QR аркылуу кирүү", "QR Sign-In"), onBack = onBack)`,
 * 2-tab mode switcher (`Показать QR` / `Сканировать QR`),
 * in `show` mode draws a visual QR matrix Canvas for `neongram://login/${state.qrAuthToken}` + token text + `Обновить QR` `PrimaryButton`;
 * in `scan` mode shows camera scan frame placeholder + token input / paste field + `Разрешить вход` `PrimaryButton`.
 */
@Composable
fun AuthQrScreen(
    state: NeongramUiState,
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    onApproveToken: (String) -> Unit
) {
    val scheme = LocalNeonScheme.current
    val lang = state.session.language.ifBlank { "ru" }

    var mode by remember { mutableStateOf("show") }
    var scannedTokenInput by remember { mutableStateOf("") }

    val qrPayload = remember(state.qrAuthToken) {
        if (state.qrAuthToken.isNotBlank()) {
            "neongram://login/${state.qrAuthToken}"
        } else {
            ""
        }
    }

    PageScaffold(
        title = tr(lang, "Вход по QR", "QR аркылуу кирүү", "QR Sign-In"),
        onBack = onBack
    ) {
        // 2-Tab Mode Switcher (`Показать QR` / `Сканировать QR`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "show" to tr(lang, "Показать QR", "QR көрсөтүү", "Show QR"),
                "scan" to tr(lang, "Сканировать QR", "QR скандоо", "Scan QR")
            ).forEach { (tabKey, tabLabel) ->
                val isSelected = mode == tabKey
                val bg = if (isSelected) scheme.primary else scheme.surface
                val fg = if (isSelected) {
                    if (scheme.isDark) Color(0xFF05070C) else Color(0xFFFFFFFF)
                } else {
                    scheme.text
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, scheme.border, RoundedCornerShape(10.dp))
                        .clickable { mode = tabKey }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (mode == "show") {
            // Show QR Card
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
                    text = tr(
                        lang,
                        "Откройте этот QR на устройстве и подтвердите вход сканированием.",
                        "Бул QR кодду башка түзмөктө ачып, скандоо менен ырастаңыз.",
                        "Open this QR on another device and confirm sign-in by scanning."
                    ),
                    color = scheme.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoading && qrPayload.isBlank()) {
                        CircularProgressIndicator(color = scheme.primary)
                    } else if (qrPayload.isNotBlank()) {
                        QrMatrixCanvas(
                            value = qrPayload,
                            sizeDp = 264.dp,
                            moduleColor = if (scheme.isDark) Color.White else Color(0xFF0E1526),
                            backgroundColor = if (scheme.isDark) Color(0xFF0C1323) else Color.White,
                            borderColor = scheme.border
                        )
                    } else {
                        Text(
                            text = tr(lang, "QR еще не сгенерирован", "QR азырынча түзүлгөн жок", "QR is not generated yet"),
                            color = scheme.muted,
                            fontSize = 14.sp
                        )
                    }
                }

                if (qrPayload.isNotBlank()) {
                    Text(
                        text = qrPayload,
                        color = scheme.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                PrimaryButton(
                    label = tr(lang, "Обновить QR", "QR жаңылоо", "Regenerate QR"),
                    onClick = onRegenerate
                )
            }
        } else {
            // Scan QR Card
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
                    text = tr(
                        lang,
                        "Сканируйте QR входа и подтвердите действие на авторизованном устройстве.",
                        "Кирүү QR кодун скандап, ыйгарым укуктуу түзмөктө ырастаңыз.",
                        "Scan login QR and approve it on an already authorized device."
                    ),
                    color = scheme.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                // Camera scan frame placeholder (height = 280.dp, inner 220x220 frame)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070912))
                        .border(1.dp, scheme.border, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle grid background inside scanner viewport
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 28.dp.toPx()
                        val gridColor = scheme.primary.copy(alpha = 0.08f)
                        var x = 0f
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }
                    }

                    // 220x220 scan frame with primary border
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .border(2.dp, scheme.primary, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = scheme.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = tr(lang, "Сканировать QR", "QR скандоо", "Scan QR"),
                                color = scheme.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Scan status card
                val hasScannedToken = scannedTokenInput.trim().isNotEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceAlt)
                        .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (hasScannedToken) {
                            tr(
                                lang,
                                "Вход для нового устройства подтвержден",
                                "Жаңы түзмөк үчүн кирүү ырасталды",
                                "Sign-in for the target device confirmed"
                            )
                        } else {
                            tr(lang, "Сканировать QR", "QR скандоо", "Scan QR")
                        },
                        color = scheme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasScannedToken) {
                            tr(lang, "Разрешить вход", "Кирүүгө уруксат берүү", "Approve login")
                        } else {
                            tr(
                                lang,
                                "Наведите камеру на QR-код для подключения web или устройства.",
                                "Web же түзмөк кошуу үчүн камераны QR-кодго багыттаңыз.",
                                "Point your camera at a QR code to connect web or a device."
                            )
                        },
                        color = scheme.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Token input / paste field
                NeonInput(
                    value = scannedTokenInput,
                    onValueChange = { scannedTokenInput = it },
                    placeholder = tr(
                        lang,
                        "neongram://login/<token>",
                        "neongram://login/<token>",
                        "neongram://login/<token>"
                    )
                )

                if (!state.session.isAuthenticated) {
                    Text(
                        text = tr(
                            lang,
                            "Для подтверждения войдите в аккаунт на этом устройстве.",
                            "Ырастоо үчүн бул түзмөктө аккаунтка кириңиз.",
                            "Sign in on this device first to approve login."
                        ),
                        color = scheme.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                PrimaryButton(
                    label = tr(lang, "Разрешить вход", "Кирүүгө уруксат берүү", "Approve login"),
                    enabled = hasScannedToken && state.session.isAuthenticated,
                    onClick = {
                        onApproveToken(scannedTokenInput.trim())
                    }
                )

                if (hasScannedToken) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { scannedTokenInput = "" }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tr(lang, "Сканировать снова", "Кайра скандоо", "Scan again"),
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

/**
 * Deterministic visual QR matrix Canvas for `neongram://login/<token>` (1:1 with `SimpleQrCode.jsx`).
 * Draws standard 7x7 finder patterns in the three corners, timing patterns, and deterministic data modules.
 */
@Composable
fun QrMatrixCanvas(
    value: String,
    sizeDp: Dp = 264.dp,
    moduleColor: Color = Color.White,
    backgroundColor: Color = Color(0xFF0C1323),
    borderColor: Color = Color(0xFF242A44)
) {
    val gridSize = 25
    val matrix = remember(value) {
        val grid = Array(gridSize) { BooleanArray(gridSize) }
        val reserved = Array(gridSize) { BooleanArray(gridSize) }

        fun placeFinder(startRow: Int, startCol: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = startRow + r
                    val cc = startCol + c
                    if (rr in 0 until gridSize && cc in 0 until gridSize) {
                        reserved[rr][cc] = true
                        val inOuter = r in 0..6 && c in 0..6 && (r == 0 || r == 6 || c == 0 || c == 6)
                        val inInner = r in 2..4 && c in 2..4
                        grid[rr][cc] = inOuter || inInner
                    }
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, gridSize - 7)
        placeFinder(gridSize - 7, 0)

        // Timing lines
        for (i in 8 until gridSize - 8) {
            reserved[6][i] = true
            reserved[i][6] = true
            grid[6][i] = (i % 2 == 0)
            grid[i][6] = (i % 2 == 0)
        }

        val seedBytes = value.ifBlank { "neongram://login/demo" }.encodeToByteArray()
        var hash = 2166136261L
        for (b in seedBytes) {
            hash = (hash xor (b.toLong() and 0xFF)) * 16777619L
        }

        var bitIdx = 0
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (!reserved[r][c]) {
                    val byteVal = seedBytes[bitIdx % seedBytes.size].toInt() and 0xFF
                    val shift = (bitIdx / seedBytes.size) % 8
                    val mix = ((byteVal shr shift) xor ((r * 31 + c * 17 + (hash and 0xFF).toInt()))) and 1
                    grid[r][c] = mix == 1
                    bitIdx++
                }
            }
        }
        grid
    }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellSize = size.width / gridSize.toFloat()
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (matrix[r][c]) {
                        drawRoundRect(
                            color = moduleColor,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize * 0.92f, cellSize * 0.92f),
                            cornerRadius = CornerRadius(cellSize * 0.2f, cellSize * 0.2f)
                        )
                    }
                }
            }
        }
    }
}
