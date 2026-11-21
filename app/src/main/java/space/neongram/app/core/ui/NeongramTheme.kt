package space.neongram.app.core.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Helper for 1:1 RU / KY / EN localization across Neongram screens.
 */
fun tr(lang: String, ru: String, ky: String, en: String): String {
    return when (lang.lowercase()) {
        "ky" -> ky
        "en" -> en
        else -> ru
    }
}

/**
 * Safely parses a `#RRGGBB` or `#AARRGGBB` hex string into a Compose [Color].
 */
fun parseHexColorOrNull(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6 && clean.length != 8) return null
    return try {
        val longVal = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000L or longVal)
        } else {
            Color(longVal)
        }
    } catch (_: Exception) {
        null
    }
}

@Immutable
data class NeonScheme(
    val id: String = "cyber_dark",
    val isDark: Boolean = true,
    val bg: Color = Color(0xFF0A0B10),
    val bgAlt: Color = Color(0xFF0F1220),
    val surface: Color = Color(0xFF15182A),
    val surfaceAlt: Color = Color(0xFF1B1F36),
    val text: Color = Color(0xFFE7EAFF),
    val muted: Color = Color(0xFF9AA3C1),
    val primary: Color = Color(0xFF2FF2FF),
    val accent: Color = Color(0xFF7C5CFF),
    val border: Color = Color(0xFF242A44),
    val bubbleMe: Color = Color(0xFF1F2A44),
    val bubbleOther: Color = Color(0xFF141927),
    val bubbleText: Color = Color(0xFFE7EAFF),
    val bubbleMuted: Color = Color(0xFFB1B8D5),
    val inputBg: Color = Color(0xFF111626),
    val tabBar: Color = Color(0xFF0E1120),
    val danger: Color = Color(0xFFFF4D6D),
    val gradientStart: Color = Color(0xFF0B0E1A),
    val gradientEnd: Color = Color(0xFF07070D),
    val fontScale: Float = 1.0f,
    val wallpaper: String = "gradient"
) {
    // Backwards-compatible property aliases
    val background: Color get() = bg
    val card: Color get() = surface
    val cardBorder: Color get() = border
    val accentCyan: Color get() = primary
    val accentPurple: Color get() = accent
    val accentPink: Color get() = danger
    val outgoingBubble: Color get() = bubbleMe
    val incomingBubble: Color get() = bubbleOther
    val textPrimary: Color get() = text
    val textSecondary: Color get() = muted
    val textMuted: Color get() = bubbleMuted
    val onlineGreen: Color get() = Color(0xFF10B981)
    val dangerRed: Color get() = danger
}

val CyberDarkScheme = NeonScheme(
    id = "cyber_dark",
    isDark = true,
    bg = Color(0xFF0A0B10),
    bgAlt = Color(0xFF0F1220),
    surface = Color(0xFF15182A),
    surfaceAlt = Color(0xFF1B1F36),
    text = Color(0xFFE7EAFF),
    muted = Color(0xFF9AA3C1),
    primary = Color(0xFF2FF2FF),
    accent = Color(0xFF7C5CFF),
    border = Color(0xFF242A44),
    bubbleMe = Color(0xFF1F2A44),
    bubbleOther = Color(0xFF141927),
    bubbleText = Color(0xFFE7EAFF),
    bubbleMuted = Color(0xFFB1B8D5),
    inputBg = Color(0xFF111626),
    tabBar = Color(0xFF0E1120),
    danger = Color(0xFFFF4D6D),
    gradientStart = Color(0xFF0B0E1A),
    gradientEnd = Color(0xFF07070D)
)

val CyberLightScheme = NeonScheme(
    id = "cyber_light",
    isDark = false,
    bg = Color(0xFFF4F7FF),
    bgAlt = Color(0xFFEDF1FF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF2F4FF),
    text = Color(0xFF0E1324),
    muted = Color(0xFF5C6A92),
    primary = Color(0xFF00B7E8),
    accent = Color(0xFF4B5CFF),
    border = Color(0xFFD7DDF2),
    bubbleMe = Color(0xFFDFF6FF),
    bubbleOther = Color(0xFFFFFFFF),
    bubbleText = Color(0xFF0E1324),
    bubbleMuted = Color(0xFF576284),
    inputBg = Color(0xFFFFFFFF),
    tabBar = Color(0xFFFFFFFF),
    danger = Color(0xFFE53950),
    gradientStart = Color(0xFFF4F7FF),
    gradientEnd = Color(0xFFE8EEFF)
)

val NeonPurpleScheme = NeonScheme(
    id = "neon_purple",
    isDark = true,
    bg = Color(0xFF0D0A14),
    bgAlt = Color(0xFF120E1D),
    surface = Color(0xFF1B152B),
    surfaceAlt = Color(0xFF231B38),
    text = Color(0xFFF3E9FF),
    muted = Color(0xFFB6A7D6),
    primary = Color(0xFFD15CFF),
    accent = Color(0xFF29F0FF),
    border = Color(0xFF2C2442),
    bubbleMe = Color(0xFF2E1F4F),
    bubbleOther = Color(0xFF1A132B),
    bubbleText = Color(0xFFF3E9FF),
    bubbleMuted = Color(0xFFD7C8F5),
    inputBg = Color(0xFF171125),
    tabBar = Color(0xFF120E1D),
    danger = Color(0xFFFF4D9A),
    gradientStart = Color(0xFF120E1D),
    gradientEnd = Color(0xFF07050D)
)

val NeonThemeList = listOf(CyberDarkScheme, CyberLightScheme, NeonPurpleScheme)

val LocalNeonScheme = staticCompositionLocalOf { CyberDarkScheme }

@Composable
fun NeongramTheme(
    themeId: String = "cyber_dark",
    useSystemTheme: Boolean = false,
    bubbleColorHex: String = "",
    bubbleScope: String = "mine",
    fontSize: String = "medium",
    wallpaper: String = "gradient",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val baseScheme = when {
        useSystemTheme -> if (systemDark) CyberDarkScheme else CyberLightScheme
        themeId == "cyber_light" || themeId == "cyberLight" -> CyberLightScheme
        themeId == "neon_purple" || themeId == "neonPurple" -> NeonPurpleScheme
        else -> CyberDarkScheme
    }

    val customBubbleColor = parseHexColorOrNull(bubbleColorHex)
    val resolvedFontScale = when (fontSize.lowercase()) {
        "small" -> 0.92f
        "large" -> 1.12f
        else -> 1.0f
    }
    val resolvedWallpaper = when (wallpaper.lowercase()) {
        "mesh" -> "mesh"
        "grid" -> "grid"
        else -> "gradient"
    }

    val resolvedScheme = baseScheme.copy(
        bubbleMe = customBubbleColor ?: baseScheme.bubbleMe,
        bubbleOther = if (bubbleScope.lowercase() == "all" && customBubbleColor != null) {
            customBubbleColor.copy(alpha = 0.78f)
        } else {
            baseScheme.bubbleOther
        },
        fontScale = resolvedFontScale,
        wallpaper = resolvedWallpaper
    )

    val materialColors = if (resolvedScheme.isDark) {
        darkColorScheme(
            primary = resolvedScheme.primary,
            secondary = resolvedScheme.accent,
            tertiary = resolvedScheme.danger,
            background = resolvedScheme.bg,
            surface = resolvedScheme.surface,
            onPrimary = Color(0xFF06070C),
            onBackground = resolvedScheme.text,
            onSurface = resolvedScheme.text,
            error = resolvedScheme.danger
        )
    } else {
        lightColorScheme(
            primary = resolvedScheme.primary,
            secondary = resolvedScheme.accent,
            tertiary = resolvedScheme.danger,
            background = resolvedScheme.bg,
            surface = resolvedScheme.surface,
            onPrimary = Color(0xFFFFFFFF),
            onBackground = resolvedScheme.text,
            onSurface = resolvedScheme.text,
            error = resolvedScheme.danger
        )
    }

    CompositionLocalProvider(LocalNeonScheme provides resolvedScheme) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}

/**
 * 1:1 with `AnimatedBackground.jsx`:
 * LinearGradient base + 6000ms pulsing diagonal overlay + support for `gradient`, `mesh`, and `grid` wallpapers.
 */
@Composable
fun AnimatedNeonBackground(
    modifier: Modifier = Modifier,
    wallpaperOverride: String? = null
) {
    val scheme = LocalNeonScheme.current
    val activeWallpaper = wallpaperOverride ?: scheme.wallpaper

    val infiniteTransition = rememberInfiniteTransition(label = "NeonBgPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val (minAlpha, maxAlpha) = when (activeWallpaper) {
        "mesh" -> 0.14f to 0.34f
        "grid" -> 0.05f to 0.18f
        else -> 0.08f to 0.26f
    }
    val overlayAlpha = minAlpha + (maxAlpha - minAlpha) * pulse

    val overlayColors = when (activeWallpaper) {
        "mesh" -> listOf(
            scheme.accent.copy(alpha = 0.45f),
            scheme.primary.copy(alpha = 0.33f)
        )
        "grid" -> listOf(
            scheme.primary.copy(alpha = 0.22f),
            scheme.accent.copy(alpha = 0.16f)
        )
        else -> listOf(
            scheme.accent.copy(alpha = 0.35f),
            scheme.primary.copy(alpha = 0.25f)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Base gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(scheme.gradientStart, scheme.gradientEnd),
                        start = Offset.Zero,
                        end = Offset(1000f, 1800f)
                    )
                )
        )
        // Animated pulsing overlay gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(overlayAlpha)
                .background(
                    Brush.linearGradient(
                        colors = overlayColors,
                        start = Offset(0f, 1800f),
                        end = Offset(1000f, 0f)
                    )
                )
        )
        // Optional grid lines when wallpaper == "grid"
        if (activeWallpaper == "grid") {
            val gridLineColor = scheme.primary.copy(alpha = 0.12f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 36.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
            }
        }
    }
}

private val BrandNeonColors = listOf(
    Color(0xFF00B8FF),
    Color(0xFF7C3AED),
    Color(0xFFB026FF),
    Color(0xFFFF2EF7),
    Color(0xFFD946EF),
    Color(0xFFFF3D81),
    Color(0xFF4D6DFF),
    Color(0xFF00E5FF),
    Color(0xFF5CFFCD),
    Color(0xFF39FF14),
    Color(0xFFFFE600),
    Color(0xFFFF8A00),
    Color(0xFF7DD3FC),
    Color(0xFFFFFFFF)
)

/**
 * 1:1 with `CyberBrandTitle` in `ChatsScreen.jsx`:
 * Animated neon letter color wipe with dual glow + core text layers.
 */
@Composable
fun CyberBrandTitle(
    text: String = "Neongram",
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val letters = remember(text) { text.toList() }
    val transition = rememberInfiniteTransition(label = "CyberBrandWipe")
    val totalDurationMs = BrandNeonColors.size * 1800

    val fontSize = if (compact) 22.sp else 29.sp

    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            letters.forEachIndexed { index, ch ->
                val color by transition.animateColor(
                    initialValue = BrandNeonColors[index % BrandNeonColors.size],
                    targetValue = BrandNeonColors[(index + BrandNeonColors.size / 2) % BrandNeonColors.size],
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = totalDurationMs
                            BrandNeonColors.forEachIndexed { colorIdx, c ->
                                val shiftedIdx = (colorIdx + index) % BrandNeonColors.size
                                BrandNeonColors[shiftedIdx] at (colorIdx * (totalDurationMs / BrandNeonColors.size)) using LinearEasing
                            }
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "letter_$index"
                )
                Box {
                    Text(
                        text = ch.toString(),
                        color = color.copy(alpha = 0.45f),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = ch.toString(),
                        color = color,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }
    }
}

/**
 * 1:1 with `PrimaryButton.jsx`:
 * minHeight 50.dp, borderRadius 16.dp, primary color fill, bold 16.sp text (`#06070C` in dark theme, `#FFFFFF` in light theme).
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = LocalNeonScheme.current
    val bgColor = if (enabled) scheme.primary else scheme.surfaceAlt
    val textColor = if (!enabled) {
        scheme.muted
    } else if (scheme.isDark) {
        Color(0xFF06070C)
    } else {
        Color(0xFFFFFFFF)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = (16f * scheme.fontScale).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 1:1 with `NeonInput.jsx`:
 * borderRadius 14.dp, surface bg, 1.dp border, minHeight 48.dp, password eye toggle.
 */
@Composable
fun NeonInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val scheme = LocalNeonScheme.current
    var passwordVisible by remember { mutableStateOf(false) }

    val visualTransformation = if (isPassword && !passwordVisible) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = scheme.muted,
                    fontSize = (16f * scheme.fontScale).sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = scheme.text,
                    fontSize = (16f * scheme.fontScale).sp
                ),
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isPassword) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { passwordVisible = !passwordVisible },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = scheme.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 1:1 with `PageScaffold.jsx`:
 * Top header with 29.sp bold centered title, left 40.dp slot with back chevron, right 40.dp slot, and AnimatedNeonBackground.
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = scheme.text,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    color = scheme.text,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    rightContent?.invoke()
                }
            }

            if (scroll) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * 1:1 with `NeonAvatar` across `ChatsScreen.jsx` and `ListRow.jsx`:
 * Renders Saved bookmark gradient (`#2F80ED` -> `#56CCF2`), AI sparkles gradient, System shield gradient,
 * or Coil `AsyncImage` with initial letter fallback.
 */
@Composable
fun NeonAvatar(
    avatarUrl: String? = null,
    label: String = "N",
    sizeDp: Dp = 42.dp,
    cornerRadiusDp: Dp = 14.dp,
    isSaved: Boolean = false,
    isAi: Boolean = false,
    isSystem: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(cornerRadiusDp)
    val initial = label.trim().take(1).uppercase().ifBlank { "N" }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(shape)
            .background(scheme.surfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        when {
            isSaved -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2F80ED), Color(0xFF56CCF2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved Messages",
                        tint = Color.White,
                        modifier = Modifier.size(sizeDp * 0.42f)
                    )
                }
            }
            isAi -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(scheme.accent, scheme.primary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Neongram AI",
                        tint = Color.White,
                        modifier = Modifier.size(sizeDp * 0.42f)
                    )
                }
            }
            isSystem -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(scheme.primary, scheme.accent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "System",
                        tint = Color.White,
                        modifier = Modifier.size(sizeDp * 0.42f)
                    )
                }
            }
            !avatarUrl.isNullOrBlank() -> {
                Text(
                    text = initial,
                    color = scheme.text,
                    fontSize = (sizeDp.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold
                )
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Text(
                    text = initial,
                    color = scheme.text,
                    fontSize = (sizeDp.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 1:1 with `ListRow.jsx`:
 * minHeight 62.dp, borderRadius 14.dp, 42x42 squircle avatar with borderRadius 14.dp using `coil.compose.AsyncImage` + letter fallback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    avatarUrl: String? = null,
    avatarLabel: String? = null,
    danger: Boolean = false,
    showChevron: Boolean = true,
    rightContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scheme = LocalNeonScheme.current
    val hasAvatar = !avatarUrl.isNullOrBlank() || !avatarLabel.isNullOrBlank()
    val shape = RoundedCornerShape(14.dp)

    val clickMod = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.border, shape)
            .then(clickMod)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasAvatar) {
            NeonAvatar(
                avatarUrl = avatarUrl,
                label = avatarLabel?.ifBlank { title } ?: title,
                sizeDp = 42.dp,
                cornerRadiusDp = 14.dp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = if (danger) scheme.danger else scheme.text,
                fontSize = (16f * scheme.fontScale).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = scheme.muted,
                    fontSize = (13f * scheme.fontScale).sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            rightContent != null -> rightContent()
            showChevron -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 1:1 with `LanguageSwitcher.jsx`:
 * `RU` | `KY` | `EN` pill switcher (`borderRadius = 12.dp`, item `minWidth = 44.dp`, `minHeight = 34.dp`, `borderRadius = 10.dp`).
 */
@Composable
fun LanguageSwitcher(
    currentLang: String,
    onSelectLang: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = LocalNeonScheme.current
    val options = listOf("ru", "ky", "en")
    val activeLang = currentLang.lowercase().ifBlank { "ru" }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { code ->
            val isActive = activeLang == code
            Box(
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) scheme.surfaceAlt else Color.Transparent)
                    .clickable { onSelectLang(code) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code.uppercase(),
                    color = if (isActive) scheme.text else scheme.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}
