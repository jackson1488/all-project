package space.neongram.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val HARDCODED_CLOUD_SERVER = "https://cloud.neongram.space"

private data class OnboardingSlideData(
    val key: String,
    val badge: String,
    val title: String,
    val desc: String,
    val accentStart: Color,
    val accentEnd: Color
)

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet OnboardingScreen:
 * 5 slides inside a floating frosted Liquid Glass hero card (`RoundedCornerShape(34.dp)`)
 * over the Aurora Borealis background (`AnimatedNeonBackground()`), glowing neon vector icons,
 * animated liquid pill indicators, and vibrant gradient `PrimaryButton`.
 */
@Composable
fun OnboardingScreen(
    lang: String,
    onSelectLang: (String) -> Unit,
    onFinish: () -> Unit
) {
    var activeSlide by remember { mutableIntStateOf(0) }

    val slides = remember(lang) {
        listOf(
            OnboardingSlideData(
                key = "security",
                badge = "QUANTUM SHIELD • E2EE",
                title = tr(lang, "Безопасность", "Коопсуздук", "Security First"),
                desc = tr(
                    lang,
                    "Сквозное шифрование для всех ваших сообщений. Ваши данные — только ваши.",
                    "Бардык билдирүүлөрүңүз үчүн учтан-учка шифрлөө. Маалыматтарыңыз — сиздики гана.",
                    "End-to-end encryption for all your messages. Your data belongs only to you."
                ),
                accentStart = Color(0xFF00E5FF),
                accentEnd = Color(0xFFA855F7)
            ),
            OnboardingSlideData(
                key = "chat",
                badge = "REALTIME • LIQUID SYNC",
                title = tr(lang, "Мгновенные чаты", "Ыкчам чаттар", "Instant Chats"),
                desc = tr(
                    lang,
                    "Быстрые сообщения, фото, видео, голосовые и файлы — всё в одном месте.",
                    "Тез билдирүүлөр, сүрөттөр, видеолор, үн жана файлдар — баарысы бир жерде.",
                    "Fast messages, photos, videos, voice notes and files — all in one place."
                ),
                accentStart = Color(0xFF2FF2FF),
                accentEnd = Color(0xFF7C3AED)
            ),
            OnboardingSlideData(
                key = "calls",
                badge = "ULTRA HD • SPATIAL AUDIO",
                title = tr(lang, "HD Звонки", "HD Чалуулар", "HD Calls"),
                desc = tr(
                    lang,
                    "Кристально чистые аудио и видеозвонки. Оставайтесь на связи в любой точке мира.",
                    "Тунук аудио жана видео чалуулар. Дүйнөнүн каалаган жеринде байланышта болуңуз.",
                    "Crystal clear audio and video calls. Stay connected anywhere in the world."
                ),
                accentStart = Color(0xFFC084FC),
                accentEnd = Color(0xFF38BDF8)
            ),
            OnboardingSlideData(
                key = "ai",
                badge = "NEURAL CORE • AI 2026",
                title = tr(lang, "Neongram AI", "Neongram AI", "Neongram AI"),
                desc = tr(
                    lang,
                    "Встроенный AI-ассистент поможет с вопросами и задачами прямо в чате.",
                    "Камтылган AI-жардамчы суроолор жана тапшырмалар менен чатта жардам берет.",
                    "Built-in AI assistant to help with questions and tasks right in your chat."
                ),
                accentStart = Color(0xFFE879F9),
                accentEnd = Color(0xFF00E5FF)
            ),
            OnboardingSlideData(
                key = "start",
                badge = "CLOUD • LIQUID GLASS",
                title = tr(lang, "Добро пожаловать", "Кош келиңиз", "Welcome"),
                desc = tr(
                    lang,
                    "Всё готово. Создайте аккаунт и начните общение в Neongram.",
                    "Баары даяр. Аккаунт түзүп, Neongram менен баарлашууну баштаңыз.",
                    "All set. Create your account and start chatting on Neongram."
                ),
                accentStart = Color(0xFF00F0FF),
                accentEnd = Color(0xFF9333EA)
            )
        )
    }

    val currentSlide = slides[activeSlide.coerceIn(0, slides.lastIndex)]
    val isLast = activeSlide == slides.lastIndex

    val infiniteTransition = rememberInfiniteTransition(label = "LiquidOnboardingPulse")
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconPulse"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070512))
    ) {
        AnimatedNeonBackground()

        // Ambient Aurora Radial Orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        currentSlide.accentStart.copy(alpha = auraAlpha * 0.65f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.22f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.2f, size.height * 0.22f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        currentSlide.accentEnd.copy(alpha = auraAlpha * 0.75f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.68f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.82f, size.height * 0.68f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Floating Header: Brand Pill + Frosted Glass LanguageSwitcher
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.20f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.55f),
                                        currentSlide.accentStart.copy(alpha = 0.45f),
                                        currentSlide.accentEnd.copy(alpha = 0.35f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "Neongram",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.40f),
                                    Color(0xFF00E5FF).copy(alpha = 0.25f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    LanguageSwitcher(
                        currentLang = lang,
                        onSelectLang = onSelectLang
                    )
                }
            }

            // Center Floating Frosted Liquid Glass Hero Card (RoundedCornerShape(34.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                val heroShape = RoundedCornerShape(34.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(heroShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.13f),
                                    Color.White.copy(alpha = 0.04f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(900f, 1200f)
                            )
                        )
                        .border(
                            width = 1.3.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.48f),
                                    currentSlide.accentStart.copy(alpha = 0.35f),
                                    currentSlide.accentEnd.copy(alpha = 0.25f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(800f, 1000f)
                            ),
                            shape = heroShape
                        )
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    AnimatedContent(
                        targetState = activeSlide,
                        transitionSpec = {
                            (fadeIn(tween(340)) + scaleIn(initialScale = 0.90f, animationSpec = tween(340)))
                                .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)))
                        },
                        label = "LiquidOnboardingSlide"
                    ) { slideIndex ->
                        val slide = slides[slideIndex]
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Subtle Top Capsule Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                slide.accentStart.copy(alpha = 0.18f),
                                                slide.accentEnd.copy(alpha = 0.18f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.35f),
                                                slide.accentStart.copy(alpha = 0.40f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = slide.badge,
                                    color = Color(0xFFE9D5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            // Layered 3D Liquid Glass Orb & Glowing Neon Vector Icon
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(196.dp)
                            ) {
                                // Outer glowing aura ring
                                Box(
                                    modifier = Modifier
                                        .size(188.dp)
                                        .scale(iconPulse)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    slide.accentStart.copy(alpha = 0.22f),
                                                    slide.accentEnd.copy(alpha = 0.08f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.2.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.35f),
                                                    slide.accentStart.copy(alpha = 0.35f),
                                                    slide.accentEnd.copy(alpha = 0.15f)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )

                                // Inner Frosted Squircle Pedestal
                                Box(
                                    modifier = Modifier
                                        .size(132.dp)
                                        .clip(RoundedCornerShape(38.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.16f),
                                                    Color.White.copy(alpha = 0.04f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.55f),
                                                    slide.accentStart.copy(alpha = 0.45f),
                                                    slide.accentEnd.copy(alpha = 0.30f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(38.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(106.dp)
                                            .scale(iconPulse),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OnboardingSlideIcon(
                                            slideIndex = slideIndex,
                                            color = slide.accentStart
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            Text(
                                text = slide.title,
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = slide.desc,
                                color = Color(0xFFE2E8F0).copy(alpha = 0.78f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Controls: Liquid Pill Indicators + Gradient PrimaryButton + Skip
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated Liquid Pill Indicators inside a frosted glass capsule
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slides.forEachIndexed { index, _ ->
                        val isActive = index == activeSlide
                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 30.dp else 8.dp,
                            animationSpec = tween(280),
                            label = "liquidDot_$index"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(dotWidth)
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isActive) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                currentSlide.accentStart,
                                                currentSlide.accentEnd
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.25f),
                                                Color.White.copy(alpha = 0.25f)
                                            )
                                        )
                                    }
                                )
                                .clickable { activeSlide = index }
                        )
                    }
                }

                // Gradient PrimaryButton wrapper with Liquid Aurora glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    currentSlide.accentStart,
                                    currentSlide.accentEnd
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.65f),
                                    Color.White.copy(alpha = 0.20f)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    PrimaryButton(
                        label = if (isLast) {
                            tr(lang, "Начать общение", "Баштоо", "Get Started")
                        } else {
                            tr(lang, "Далее", "Кийинки", "Continue")
                        },
                        onClick = {
                            if (!isLast) {
                                activeSlide += 1
                            } else {
                                onFinish()
                            }
                        }
                    )
                }

                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onFinish)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Пропустить", "Өткөрүп жиберүү", "Skip"),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(28.dp))
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
            // Neon Shield with keyhole circle + vertical stem
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(76.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 32.dp,
                            topEnd = 32.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        )
                    )
                    .background(color.copy(alpha = 0.12f))
                    .border(
                        width = 2.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White, color)
                        ),
                        shape = RoundedCornerShape(
                            topStart = 32.dp,
                            topEnd = 32.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(2.5.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
        }
        1 -> {
            // Neon Chat Bubble with 3 glowing message bars + tail
            Box(
                modifier = Modifier.size(106.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(74.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(Color.White, color)),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.85f))
                        )
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.55f))
                        )
                    }
                }
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 22.dp, bottom = 16.dp)
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
            // Neon HD Call pulse rings
            Box(
                modifier = Modifier.size(106.dp),
                contentAlignment = Alignment.Center
            ) {
                listOf(78.dp, 92.dp, 104.dp).forEachIndexed { idx, ringSize ->
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .border(
                                width = 1.2.dp,
                                color = color.copy(alpha = (0.30f - idx * 0.08f).coerceAtLeast(0.08f)),
                                shape = CircleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f))
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(Color.White, color)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(135f)
                            .border(
                                width = 2.5.dp,
                                color = Color.White,
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
            // Neon AI Core with 6 orbiting nodes
            Box(
                modifier = Modifier.size(106.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f))
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(Color.White, color)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
                listOf(0, 60, 120, 180, 240, 300).forEach { deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val offsetX = (cos(rad) * 42.0).dp
                    val offsetY = (sin(rad) * 42.0).dp
                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, color, CircleShape)
                    )
                }
            }
        }
        else -> {
            // Neon Rocket Launch
            Box(
                modifier = Modifier.size(106.dp),
                contentAlignment = Alignment.Center
            ) {
                // Left fin
                Box(
                    modifier = Modifier
                        .offset(x = (-24).dp, y = 14.dp)
                        .width(14.dp)
                        .height(20.dp)
                        .rotate(15f)
                        .border(2.dp, color.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                )
                // Right fin
                Box(
                    modifier = Modifier
                        .offset(x = 24.dp, y = 14.dp)
                        .width(14.dp)
                        .height(20.dp)
                        .rotate(-15f)
                        .border(2.dp, color.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                )
                // Rocket hull
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(64.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 19.dp,
                                topEnd = 19.dp,
                                bottomStart = 6.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .background(color.copy(alpha = 0.14f))
                        .border(
                            width = 2.5.dp,
                            brush = Brush.verticalGradient(listOf(Color.White, color)),
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
                            .background(Color.White)
                    )
                }
                // Exhaust flames
                listOf(-8, 0, 8).forEachIndexed { idx, xOff ->
                    val flameHeight = (16 - abs(xOff)).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(x = xOff.dp, y = (-4).dp)
                            .width(5.dp)
                            .height(flameHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = 0.55f + idx * 0.12f))
                    )
                }
            }
        }
    }
}

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet AuthScreen:
 * - NO SERVER SELECTOR AT ALL: Hardcodes `"https://cloud.neongram.space"` internally when calling
 *   `onLogin` and `onRegister`.
 * - Full-screen Aurora Borealis background (`AnimatedNeonBackground()`) with floating frosted glass
 *   `LanguageSwitcher` pill at the top-right.
 * - Center-top floating **3D Liquid Glass App Emblem**: `88.dp` glowing frosted glass squircle
 *   (`RoundedCornerShape(28.dp)`) with multi-layered neon cyan-to-violet gradient aura, specular
 *   glass rim (`1.5.dp`), glowing `"N"` monogram + `"Neongram"` title (`34.sp`, ExtraBold,
 *   letterSpacing `1.sp`) and subtle capsule badge `"CLOUD • LIQUID GLASS"`.
 * - Main **Frosted Liquid Glass Card** (`RoundedCornerShape(32.dp)`, translucent glass gradient fill
 *   `Color.White.copy(alpha = 0.12f)` -> `0.04f`, `1.2.dp` specular diagonal gradient border
 *   `White@0.45 -> Cyan@0.3 -> Purple@0.2`, `padding = 24.dp`):
 *   - Floating Glass Segmented Pill Switcher (`"Вход"` | `"Регистрация"`, `RoundedCornerShape(20.dp)`).
 *   - Sleek frosted capsule inputs (`NeonInput` with `RoundedCornerShape(22.dp)`) for `@username`,
 *     `Пароль`, and `Подтвердите пароль` (smoothly animated in Register mode).
 *   - Inline error pill if `state.errorMessage != null` or local validation fails.
 *   - Vibrant Liquid Aurora gradient `PrimaryButton` (`"Войти в Neongram"` / `"Создать аккаунт"`).
 *   - Bottom frosted glass pill button `"Войти по QR-коду"` (`QrCode` icon + text, calls `onOpenQrLogin()`).
 *   - 2FA verification view in the same Liquid Glass card when `state.twoFactorChallengeId != null`.
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
    val lang = state.session.language.ifBlank { "ru" }

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var twoFactorCode by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val effectiveError = localError ?: state.errorMessage

    val infiniteTransition = rememberInfiniteTransition(label = "AuthLiquidAura")
    val emblemBreath by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emblemBreath"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val cyanNeon = Color(0xFF00E5FF)
    val violetNeon = Color(0xFFA855F7)
    val deepViolet = Color(0xFF7C3AED)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070512))
    ) {
        // Full-screen Aurora Borealis background
        AnimatedNeonBackground()

        // Multi-layered Aurora Violet & Cyan light blooms
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyanNeon.copy(alpha = glowPulse * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.16f),
                    radius = size.width * 0.68f
                ),
                radius = size.width * 0.68f,
                center = Offset(size.width * 0.18f, size.height * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        violetNeon.copy(alpha = glowPulse * 0.55f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.48f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.85f, size.height * 0.48f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with floating frosted glass LanguageSwitcher pill at the top-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    cyanNeon.copy(alpha = 0.30f),
                                    violetNeon.copy(alpha = 0.20f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    LanguageSwitcher(
                        currentLang = lang,
                        onSelectLang = onSelectLang
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center-top floating 3D Liquid Glass App Emblem (88.dp squircle, RoundedCornerShape(28.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                // Multi-layered neon cyan-to-violet gradient aura behind emblem
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .scale(emblemBreath)
                        .clip(RoundedCornerShape(42.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    cyanNeon.copy(alpha = glowPulse * 0.55f),
                                    violetNeon.copy(alpha = glowPulse * 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                val emblemShape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(emblemBreath)
                        .clip(emblemShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    cyanNeon.copy(alpha = 0.18f),
                                    violetNeon.copy(alpha = 0.24f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(220f, 220f)
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.78f),
                                    cyanNeon.copy(alpha = 0.55f),
                                    violetNeon.copy(alpha = 0.45f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(200f, 200f)
                            ),
                            shape = emblemShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle inner specular highlight + glowing "N" monogram
                    Text(
                        text = "N",
                        color = cyanNeon.copy(alpha = 0.45f),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.offset(x = 1.dp, y = 2.dp)
                    )
                    Text(
                        text = "N",
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "Neongram" title (34.sp, ExtraBold, letterSpacing = 1.sp)
            Text(
                text = "Neongram",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtle capsule badge "CLOUD • LIQUID GLASS"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                cyanNeon.copy(alpha = 0.16f),
                                violetNeon.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.40f),
                                cyanNeon.copy(alpha = 0.35f),
                                violetNeon.copy(alpha = 0.30f)
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "CLOUD • LIQUID GLASS",
                    color = Color(0xFFE9D5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.3.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Frosted Liquid Glass Card
            // RoundedCornerShape(32.dp), translucent glass gradient fill Color.White.copy(alpha = 0.12f) -> 0.04f,
            // 1.2.dp specular diagonal gradient border White@0.45 -> Cyan@0.3 -> Purple@0.2, padding = 24.dp
            val glassCardShape = RoundedCornerShape(32.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(glassCardShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(900f, 1100f)
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                cyanNeon.copy(alpha = 0.30f),
                                violetNeon.copy(alpha = 0.20f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(900f, 1100f)
                        ),
                        shape = glassCardShape
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.twoFactorChallengeId != null) {
                    // 2FA Verification View inside the same Liquid Glass Card
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            cyanNeon.copy(alpha = 0.24f),
                                            violetNeon.copy(alpha = 0.24f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = tr(lang, "Двухфакторная защита", "Эки факторлуу текшерүү", "Two-Factor Shield"),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "CLOUD • 2FA VERIFICATION",
                                color = cyanNeon.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp
                            )
                        }
                    }

                    Text(
                        text = tr(
                            lang,
                            "Введите 6-значный код из приложения-аутентификатора или код, отправленный на ваше устройство.",
                            "Аутентификатор колдонмосундагы же түзмөгүңүзгө жөнөтүлгөн 6 орундуу кодду жазыңыз.",
                            "Enter the 6-digit code from your authenticator app or the code sent to your device."
                        ),
                        color = Color(0xFFE2E8F0).copy(alpha = 0.78f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Sleek Frosted Capsule Input for 6-digit 2FA Code
                    FrostedCapsuleInputWrapper(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = cyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    ) {
                        NeonInput(
                            value = twoFactorCode,
                            onValueChange = { raw ->
                                twoFactorCode = raw.filter { it.isDigit() }.take(6)
                                localError = null
                            },
                            placeholder = "000000",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.clip(RoundedCornerShape(22.dp))
                        )
                    }

                    if (!effectiveError.isNullOrBlank()) {
                        LiquidErrorPill(message = effectiveError)
                    }

                    // Liquid Aurora Gradient PrimaryButton
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(cyanNeon, deepViolet, violetNeon)
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        Color.White.copy(alpha = 0.20f)
                                    )
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        PrimaryButton(
                            label = if (state.isLoading) {
                                tr(lang, "Проверка...", "Текшерилүүдө...", "Verifying...")
                            } else {
                                tr(lang, "Подтвердить вход", "Кирүүнү ырастоо", "Confirm Sign In")
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
                    }

                    // Back to password pill button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                twoFactorCode = ""
                                localError = null
                                onCancel2fa()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(lang, "Назад к паролю", "Сырсөзгө кайтуу", "Back to password"),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Floating Glass Segmented Pill Switcher at the top of the card
                    // ("Вход" | "Регистрация", RoundedCornerShape(20.dp), active tab has glowing cyan/violet glass pill fill and bold white text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        cyanNeon.copy(alpha = 0.18f),
                                        violetNeon.copy(alpha = 0.18f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            false to tr(lang, "Вход", "Кирүү", "Sign In"),
                            true to tr(lang, "Регистрация", "Катталуу", "Register")
                        )
                        tabs.forEach { (registerTab, label) ->
                            val isSelected = isRegisterMode == registerTab
                            val pillShape = RoundedCornerShape(16.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .clip(pillShape)
                                    .background(
                                        if (isSelected) {
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    cyanNeon.copy(alpha = 0.34f),
                                                    violetNeon.copy(alpha = 0.42f)
                                                )
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color.Transparent)
                                            )
                                        }
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 1.dp,
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        cyanNeon.copy(alpha = 0.45f)
                                                    )
                                                ),
                                                shape = pillShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        isRegisterMode = registerTab
                                        localError = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.58f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }

                    // Sleek Frosted Capsule Inputs (NeonInput with RoundedCornerShape(22.dp))
                    // 1. @username with @ icon
                    FrostedCapsuleInputWrapper(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = "Username",
                                tint = cyanNeon.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    ) {
                        NeonInput(
                            value = username,
                            onValueChange = {
                                username = it
                                localError = null
                            },
                            placeholder = tr(lang, "@username", "@username", "@username"),
                            modifier = Modifier.clip(RoundedCornerShape(22.dp))
                        )
                    }

                    // 2. Пароль with lock icon & eye toggle
                    FrostedCapsuleInputWrapper(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = violetNeon.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    ) {
                        NeonInput(
                            value = password,
                            onValueChange = {
                                password = it
                                localError = null
                            },
                            placeholder = tr(lang, "Пароль", "Сырсөз", "Password"),
                            isPassword = true,
                            modifier = Modifier.clip(RoundedCornerShape(22.dp))
                        )
                    }

                    // 3. Подтвердите пароль (smoothly animated in Register mode)
                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                    ) {
                        FrostedCapsuleInputWrapper(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Confirm Password",
                                    tint = cyanNeon.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        ) {
                            NeonInput(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    localError = null
                                },
                                placeholder = tr(lang, "Подтвердите пароль", "Сырсөздү ырастаңыз", "Confirm password"),
                                isPassword = true,
                                modifier = Modifier.clip(RoundedCornerShape(22.dp))
                            )
                        }
                    }

                    // Inline error pill if state.errorMessage != null or local validation error
                    if (!effectiveError.isNullOrBlank()) {
                        LiquidErrorPill(message = effectiveError)
                    }

                    // Vibrant Liquid Aurora gradient PrimaryButton ("Войти в Neongram" / "Создать аккаунт")
                    // Hardcodes "https://cloud.neongram.space" internally!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(cyanNeon, deepViolet, violetNeon)
                                )
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        Color.White.copy(alpha = 0.25f)
                                    )
                                ),
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        PrimaryButton(
                            label = if (state.isLoading) {
                                tr(lang, "Подключение...", "Туташууда...", "Connecting...")
                            } else if (isRegisterMode) {
                                tr(lang, "Создать аккаунт", "Аккаунт түзүү", "Create Account")
                            } else {
                                tr(lang, "Войти в Neongram", "Neongram'га кирүү", "Sign In to Neongram")
                            },
                            enabled = !state.isLoading,
                            onClick = {
                                val cleanUser = username.trim().removePrefix("@")
                                val cleanPass = password.trim()
                                val cleanConfirm = confirmPassword.trim()
                                if (cleanUser.isEmpty() || cleanPass.isEmpty() || (isRegisterMode && cleanConfirm.isEmpty())) {
                                    localError = tr(lang, "Заполните все поля", "Бардык талааларды толтуруңуз", "Please fill in all fields")
                                    return@PrimaryButton
                                }
                                if (isRegisterMode && cleanPass != cleanConfirm) {
                                    localError = tr(lang, "Пароли не совпадают", "Сырсөздөр дал келбейт", "Passwords do not match")
                                    return@PrimaryButton
                                }
                                localError = null
                                if (isRegisterMode) {
                                    onRegister(cleanUser, cleanPass, cleanConfirm, HARDCODED_CLOUD_SERVER)
                                } else {
                                    onLogin(cleanUser, cleanPass, HARDCODED_CLOUD_SERVER)
                                }
                            }
                        )
                    }

                    // Bottom frosted glass pill button "Войти по QR-коду" (QrCode icon + text, calls onOpenQrLogin())
                    val qrPillShape = RoundedCornerShape(22.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(qrPillShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.35f),
                                        cyanNeon.copy(alpha = 0.30f),
                                        violetNeon.copy(alpha = 0.25f)
                                    )
                                ),
                                shape = qrPillShape
                            )
                            .clickable(onClick = onOpenQrLogin)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "QR Login",
                            tint = cyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = tr(lang, "Войти по QR-коду", "QR-код аркылуу кирүү", "Sign In with QR Code"),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FrostedCapsuleInputWrapper(
    leadingIcon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val capsuleShape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(capsuleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.09f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color(0xFF00E5FF).copy(alpha = 0.22f),
                        Color(0xFFA855F7).copy(alpha = 0.18f)
                    )
                ),
                shape = capsuleShape
            )
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            leadingIcon()
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun LiquidErrorPill(message: String) {
    val pillShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(pillShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF4D6D).copy(alpha = 0.22f),
                        Color(0xFF991B1B).copy(alpha = 0.18f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFFFF4D6D).copy(alpha = 0.55f),
                shape = pillShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = Color(0xFFFF758F),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            color = Color(0xFFFFD6E0),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 2026 iOS 18 Liquid Glass & Aurora Violet AuthQrScreen:
 * Frosted Liquid Glass QR card (`RoundedCornerShape(30.dp)`) with glowing cyan/violet QR matrix
 * and segmented pill switcher (`Показать QR` / `Сканировать QR`).
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

    val cyanNeon = Color(0xFF00E5FF)
    val violetNeon = Color(0xFFA855F7)

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
        // Floating Liquid Glass Segmented Pill Switcher (`Показать QR` / `Сканировать QR`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.40f),
                            cyanNeon.copy(alpha = 0.28f),
                            violetNeon.copy(alpha = 0.22f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "show" to tr(lang, "Показать QR", "QR көрсөтүү", "Show QR"),
                "scan" to tr(lang, "Сканировать QR", "QR скандоо", "Scan QR")
            ).forEach { (tabKey, tabLabel) ->
                val isSelected = mode == tabKey
                val tabShape = RoundedCornerShape(17.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(tabShape)
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        cyanNeon.copy(alpha = 0.35f),
                                        violetNeon.copy(alpha = 0.42f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.55f),
                                            cyanNeon.copy(alpha = 0.40f)
                                        )
                                    ),
                                    shape = tabShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { mode = tabKey }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        val qrCardShape = RoundedCornerShape(30.dp)

        if (mode == "show") {
            // Frosted Liquid Glass QR Card (RoundedCornerShape(30.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(qrCardShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                cyanNeon.copy(alpha = 0.30f),
                                violetNeon.copy(alpha = 0.22f)
                            )
                        ),
                        shape = qrCardShape
                    )
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    cyanNeon.copy(alpha = 0.16f),
                                    violetNeon.copy(alpha = 0.16f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.30f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "INSTANT QR HANDSHAKE",
                        color = Color(0xFFE9D5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp
                    )
                }

                Text(
                    text = tr(
                        lang,
                        "Откройте этот QR на устройстве и подтвердите вход сканированием.",
                        "Бул QR кодду башка түзмөктө ачып, скандоо менен ырастаңыз.",
                        "Open this QR on another device and confirm sign-in by scanning."
                    ),
                    color = Color(0xFFE2E8F0).copy(alpha = 0.80f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 290.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoading && qrPayload.isBlank()) {
                        CircularProgressIndicator(color = cyanNeon)
                    } else if (qrPayload.isNotBlank()) {
                        QrMatrixCanvas(
                            value = qrPayload,
                            sizeDp = 264.dp,
                            moduleColor = cyanNeon,
                            backgroundColor = Color(0xFF090718),
                            borderColor = violetNeon.copy(alpha = 0.45f)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = qrPayload,
                            color = cyanNeon.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(cyanNeon, Color(0xFF7C3AED), violetNeon)
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            color = Color.White.copy(alpha = 0.50f),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    PrimaryButton(
                        label = tr(lang, "Обновить QR", "QR жаңылоо", "Regenerate QR"),
                        onClick = onRegenerate
                    )
                }
            }
        } else {
            // Frosted Liquid Glass Scan QR Card (RoundedCornerShape(30.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(qrCardShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                cyanNeon.copy(alpha = 0.30f),
                                violetNeon.copy(alpha = 0.22f)
                            )
                        ),
                        shape = qrCardShape
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = tr(
                        lang,
                        "Сканируйте QR входа и подтвердите действие на авторизованном устройстве.",
                        "Кирүү QR кодун скандап, ыйгарым укуктуу түзмөктө ырастаңыз.",
                        "Scan login QR and approve it on an already authorized device."
                    ),
                    color = Color(0xFFE2E8F0).copy(alpha = 0.80f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // Liquid Glass Camera Scanner Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(268.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF070614))
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    cyanNeon.copy(alpha = 0.45f),
                                    violetNeon.copy(alpha = 0.35f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 28.dp.toPx()
                        val gridColor = cyanNeon.copy(alpha = 0.08f)
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

                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(cyanNeon, violetNeon)
                                ),
                                shape = RoundedCornerShape(28.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = cyanNeon,
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = tr(lang, "Сканировать QR", "QR скандоо", "Scan QR"),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val hasScannedToken = scannedTokenInput.trim().isNotEmpty()

                // Frosted Status Pill
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                            tr(lang, "Ожидание QR-кода", "QR-код күтүлүүдө", "Awaiting QR Code")
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasScannedToken) {
                            tr(lang, "Нажмите «Разрешить вход» ниже", "Төмөндөгү «Кирүүгө уруксат берүү» басыңыз", "Tap 'Approve login' below")
                        } else {
                            tr(
                                lang,
                                "Наведите камеру на QR-код или вставьте токен подключения ниже.",
                                "Камераны QR-кодго багыттаңыз же токенди төмөнгө жазыңыз.",
                                "Point your camera at a QR code or paste the login token below."
                            )
                        },
                        color = Color(0xFFE2E8F0).copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                // Frosted Capsule Token Input
                FrostedCapsuleInputWrapper(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = cyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                ) {
                    NeonInput(
                        value = scannedTokenInput,
                        onValueChange = { scannedTokenInput = it },
                        placeholder = "neongram://login/<token>",
                        modifier = Modifier.clip(RoundedCornerShape(22.dp))
                    )
                }

                if (!state.session.isAuthenticated) {
                    Text(
                        text = tr(
                            lang,
                            "Для подтверждения сначала войдите в аккаунт на этом устройстве.",
                            "Ырастоо үчүн бул түзмөктө аккаунтка кириңиз.",
                            "Sign in on this device first to approve login."
                        ),
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(cyanNeon, Color(0xFF7C3AED), violetNeon)
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            color = Color.White.copy(alpha = 0.50f),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    PrimaryButton(
                        label = tr(lang, "Разрешить вход", "Кирүүгө уруксат берүү", "Approve Login"),
                        enabled = hasScannedToken && state.session.isAuthenticated,
                        onClick = {
                            onApproveToken(scannedTokenInput.trim())
                        }
                    )
                }

                if (hasScannedToken) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(50))
                            .clickable { scannedTokenInput = "" }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = cyanNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tr(lang, "Сбросить токен", "Кайра скандоо", "Reset token"),
                            color = cyanNeon,
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
 * Deterministic visual QR matrix Canvas for `neongram://login/<token>` with glowing cyan/violet modules
 * and frosted glass border.
 */
@Composable
fun QrMatrixCanvas(
    value: String,
    sizeDp: Dp = 264.dp,
    moduleColor: Color = Color(0xFF00E5FF),
    backgroundColor: Color = Color(0xFF090718),
    borderColor: Color = Color(0xFFA855F7)
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

    val qrContainerShape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(qrContainerShape)
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.50f),
                        moduleColor.copy(alpha = 0.55f),
                        borderColor
                    )
                ),
                shape = qrContainerShape
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellSize = size.width / gridSize.toFloat()
            val moduleBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White,
                    moduleColor,
                    Color(0xFFC084FC)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (matrix[r][c]) {
                        drawRoundRect(
                            brush = moduleBrush,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize * 0.90f, cellSize * 0.90f),
                            cornerRadius = CornerRadius(cellSize * 0.25f, cellSize * 0.25f)
                        )
                    }
                }
            }
        }
    }
}
