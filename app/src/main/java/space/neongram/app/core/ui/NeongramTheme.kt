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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
    val bg: Color = Color(0xFF060714),
    val bgAlt: Color = Color(0xFF0C0E24),
    val surface: Color = Color(0x451A1E40),
    val surfaceAlt: Color = Color(0x66252B58),
    val text: Color = Color(0xFFF6F8FF),
    val muted: Color = Color(0xFFA5B0D6),
    val primary: Color = Color(0xFF38F2FF),
    val accent: Color = Color(0xFFA855F7),
    val border: Color = Color(0x4DFFFFFF),
    val bubbleMe: Color = Color(0x754C1D95),
    val bubbleOther: Color = Color(0x4D1E2448),
    val bubbleText: Color = Color(0xFFF8FAFF),
    val bubbleMuted: Color = Color(0xFFB8C2E8),
    val inputBg: Color = Color(0x52161B38),
    val tabBar: Color = Color(0x78121630),
    val danger: Color = Color(0xFFFF4D7D),
    val gradientStart: Color = Color(0xFF060714),
    val gradientEnd: Color = Color(0xFF0B0722),
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

val LiquidAuroraDark = NeonScheme(
    id = "cyber_dark",
    isDark = true,
    bg = Color(0xFF060714),
    bgAlt = Color(0xFF0C0E24),
    surface = Color(0x451A1E40),
    surfaceAlt = Color(0x66252B58),
    text = Color(0xFFF6F8FF),
    muted = Color(0xFFA5B0D6),
    primary = Color(0xFF38F2FF),
    accent = Color(0xFFA855F7),
    border = Color(0x4DFFFFFF),
    bubbleMe = Color(0x754C1D95),
    bubbleOther = Color(0x4D1E2448),
    bubbleText = Color(0xFFF8FAFF),
    bubbleMuted = Color(0xFFB8C2E8),
    inputBg = Color(0x52161B38),
    tabBar = Color(0x78121630),
    danger = Color(0xFFFF4D7D),
    gradientStart = Color(0xFF060714),
    gradientEnd = Color(0xFF0B0722)
)

val CyberDarkScheme = LiquidAuroraDark

val CyberLightScheme = NeonScheme(
    id = "cyber_light",
    isDark = false,
    bg = Color(0xFFF2F5FF),
    bgAlt = Color(0xFFE8EDFF),
    surface = Color(0xA6FFFFFF),
    surfaceAlt = Color(0xCCEEF2FF),
    text = Color(0xFF0C1226),
    muted = Color(0xFF56648C),
    primary = Color(0xFF00B7E8),
    accent = Color(0xFF7C3AED),
    border = Color(0x80FFFFFF),
    bubbleMe = Color(0xCCDBEAFE),
    bubbleOther = Color(0xB3FFFFFF),
    bubbleText = Color(0xFF0C1226),
    bubbleMuted = Color(0xFF525F82),
    inputBg = Color(0xB3FFFFFF),
    tabBar = Color(0xCCFFFFFF),
    danger = Color(0xFFE53950),
    gradientStart = Color(0xFFF2F5FF),
    gradientEnd = Color(0xFFE5ECFF)
)

val NeonPurpleScheme = NeonScheme(
    id = "neon_purple",
    isDark = true,
    bg = Color(0xFF090516),
    bgAlt = Color(0xFF110A26),
    surface = Color(0x4D241445),
    surfaceAlt = Color(0x6E321C5E),
    text = Color(0xFFF7F0FF),
    muted = Color(0xFFBFAEE0),
    primary = Color(0xFFD946EF),
    accent = Color(0xFF38F2FF),
    border = Color(0x4DFFFFFF),
    bubbleMe = Color(0x7A581C87),
    bubbleOther = Color(0x4D22153D),
    bubbleText = Color(0xFFF7F0FF),
    bubbleMuted = Color(0xFFD7C8F5),
    inputBg = Color(0x521C1036),
    tabBar = Color(0x78140B29),
    danger = Color(0xFFFF4D9A),
    gradientStart = Color(0xFF090516),
    gradientEnd = Color(0xFF14082E)
)

val NeonThemeList = listOf(CyberDarkScheme, CyberLightScheme, NeonPurpleScheme)

val LocalNeonScheme = staticCompositionLocalOf { LiquidAuroraDark }

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
        useSystemTheme -> if (systemDark) LiquidAuroraDark else CyberLightScheme
        themeId == "cyber_light" || themeId == "cyberLight" -> CyberLightScheme
        themeId == "neon_purple" || themeId == "neonPurple" -> NeonPurpleScheme
        else -> LiquidAuroraDark
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
            customBubbleColor.copy(alpha = 0.72f)
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
            onPrimary = Color(0xFF060714),
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
 * 2026 iOS 18 Liquid Glass & Aurora Borealis Background:
 * Deep midnight base (`#060714` -> `#0B0722`) with 3 smoothly drifting, breathing radial glowing orbs:
 * - Orb 1: Electric Aurora Violet (`Color(0x669333EA)`) drifting in top-left/center
 * - Orb 2: Liquid Cyan (`Color(0x5506B6D4)`) drifting in center-right
 * - Orb 3: Deep Magenta-Blue (`Color(0x4D3B82F6)`) drifting in bottom-left
 */
@Composable
fun AnimatedNeonBackground(
    modifier: Modifier = Modifier,
    wallpaperOverride: String? = null
) {
    val scheme = LocalNeonScheme.current
    val activeWallpaper = wallpaperOverride ?: scheme.wallpaper

    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAuroraBg")
    val drift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift1"
    )
    val drift2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )
    val drift3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift3"
    )

    val baseStart = if (scheme.isDark) Color(0xFF060714) else scheme.gradientStart
    val baseEnd = if (scheme.isDark) Color(0xFF0B0722) else scheme.gradientEnd

    val orb1Color = if (scheme.isDark) Color(0x669333EA) else Color(0x339333EA)
    val orb2Color = if (scheme.isDark) Color(0x5506B6D4) else Color(0x2E06B6D4)
    val orb3Color = if (scheme.isDark) Color(0x4D3B82F6) else Color(0x293B82F6)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Deep Midnight Indigo-Violet Base Gradient
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(baseStart, baseEnd),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val minDim = minOf(w, h)

            // Orb 1: Electric Aurora Violet drifting in top-left / center
            val orb1Center = Offset(
                x = w * (0.20f + 0.22f * drift1),
                y = h * (0.16f + 0.16f * drift2)
            )
            val orb1Radius = (minDim * (0.68f + 0.14f * drift3)).coerceAtLeast(10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb1Color, Color.Transparent),
                    center = orb1Center,
                    radius = orb1Radius
                ),
                radius = orb1Radius,
                center = orb1Center
            )

            // Orb 2: Liquid Cyan drifting in center-right
            val orb2Center = Offset(
                x = w * (0.82f - 0.20f * drift2),
                y = h * (0.44f + 0.16f * drift1)
            )
            val orb2Radius = (minDim * (0.64f + 0.15f * drift1)).coerceAtLeast(10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb2Color, Color.Transparent),
                    center = orb2Center,
                    radius = orb2Radius
                ),
                radius = orb2Radius,
                center = orb2Center
            )

            // Orb 3: Deep Magenta-Blue drifting in bottom-left
            val orb3Center = Offset(
                x = w * (0.25f + 0.24f * drift3),
                y = h * (0.80f - 0.14f * drift1)
            )
            val orb3Radius = (minDim * (0.72f + 0.12f * drift2)).coerceAtLeast(10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb3Color, Color.Transparent),
                    center = orb3Center,
                    radius = orb3Radius
                ),
                radius = orb3Radius,
                center = orb3Center
            )

            if (activeWallpaper == "mesh") {
                val meshCenter = Offset(w * 0.5f, h * 0.5f)
                val meshRadius = (minDim * 0.85f).coerceAtLeast(10f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(scheme.accent.copy(alpha = 0.20f), Color.Transparent),
                        center = meshCenter,
                        radius = meshRadius
                    ),
                    radius = meshRadius,
                    center = meshCenter
                )
            }

            if (activeWallpaper == "grid") {
                val gridLineColor = scheme.primary.copy(alpha = 0.10f)
                val step = 36.dp.toPx()
                var x = 0f
                while (x < w) {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = 0f
                while (y < h) {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
            }
        }
    }
}

/**
 * 2026 iOS 18 Liquid Glass Card Container:
 * Translucent glass fill blended over `scheme.surface` with a specular 1.dp gradient rim border.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val glassHighlightBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.14f),
            Color.White.copy(alpha = 0.05f)
        )
    )
    val rimBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.42f),
            scheme.primary.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.08f)
        )
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(scheme.surface)
            .background(glassHighlightBrush)
            .border(width = 1.dp, brush = rimBorderBrush, shape = shape)
            .then(clickMod),
        content = content
    )
}

private val BrandNeonColors = listOf(
    Color(0xFF38F2FF),
    Color(0xFFA855F7),
    Color(0xFFC084FC),
    Color(0xFFFF2EF7),
    Color(0xFFD946EF),
    Color(0xFFFF4D7D),
    Color(0xFF6366F1),
    Color(0xFF00E5FF),
    Color(0xFF5CFFCD),
    Color(0xFF7DD3FC),
    Color(0xFFFFFFFF)
)

/**
 * Animated Liquid Aurora & Neon letter color wipe with dual glow + core text layers.
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
                            BrandNeonColors.forEachIndexed { colorIdx, _ ->
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
 * 2026 iOS 18 Liquid Pill / Capsule Primary Button:
 * `RoundedCornerShape(24.dp)`, `minHeight = 54.dp`, filled with a vibrant Liquid Aurora gradient
 * (`#00E5FF` -> `#7C3AED` -> `#C084FC`), specular top border (`1.dp`, `Color.White.copy(alpha = 0.45f)`),
 * bold white text (`16.sp`).
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = LocalNeonScheme.current
    val shape = RoundedCornerShape(24.dp)
    val gradientBrush = if (enabled) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF00E5FF),
                Color(0xFF7C3AED),
                Color(0xFFC084FC)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                scheme.surfaceAlt,
                scheme.surface
            )
        )
    }
    val borderColor = if (enabled) {
        Color.White.copy(alpha = 0.45f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    val textColor = if (enabled) Color.White else scheme.muted

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(gradientBrush)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = (16f * scheme.fontScale).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 2026 iOS 18 Frosted Liquid Glass Capsule Input:
 * `RoundedCornerShape(22.dp)`, `minHeight = 54.dp`, translucent glass fill (`Color.White.copy(alpha = 0.08f)`),
 * specular gradient border (`1.dp`, brighter cyan/white when focused), optional leading icon, and password eye toggle.
 */
@Composable
fun NeonInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val scheme = LocalNeonScheme.current
    var passwordVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val visualTransformation = if (isPassword && !passwordVisible) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    val shape = RoundedCornerShape(22.dp)
    val glassFill = if (scheme.isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        scheme.inputBg
    }
    val borderBrush = if (isFocused) {
        Brush.linearGradient(
            colors = listOf(
                scheme.primary.copy(alpha = 0.90f),
                Color.White.copy(alpha = 0.70f),
                scheme.accent.copy(alpha = 0.75f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.36f),
                scheme.primary.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(scheme.inputBg)
            .background(glassFill)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (isFocused) scheme.primary else scheme.muted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = scheme.muted.copy(alpha = 0.85f),
                    fontSize = (16f * scheme.fontScale).sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = scheme.text,
                    fontSize = (16f * scheme.fontScale).sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(scheme.primary),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
        if (isPassword) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { passwordVisible = !passwordVisible },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = if (passwordVisible) scheme.primary else scheme.muted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * 2026 iOS 18 Liquid Glass PageScaffold:
 * Ultra-clean modern iOS 18 style header (`28.sp` ExtraBold title, frosted glass circular back button `40.dp`
 * with `1.dp` glass border) over `AnimatedNeonBackground()`.
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
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.width(44.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (onBack != null) {
                        val backGlassBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        )
                        val backBorderBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                scheme.primary.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(scheme.surface)
                                .background(backGlassBrush)
                                .border(width = 1.dp, brush = backBorderBrush, shape = CircleShape)
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier.widthIn(min = 44.dp),
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
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 124.dp),
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
 * 2026 iOS 18 Circular Glowing NeonAvatar:
 * Renders a **Circular Avatar (`CircleShape`)** surrounded by a `1.8.dp` glowing gradient ring
 * (`Brush.linearGradient(listOf(scheme.primary, scheme.accent))`), with inner avatar image (`AsyncImage`)
 * or glowing gradient monogram!
 */
@Composable
fun NeonAvatar(
    avatarUrl: String? = null,
    label: String = "N",
    sizeDp: Dp = 46.dp,
    cornerRadiusDp: Dp = 23.dp,
    isSaved: Boolean = false,
    isAi: Boolean = false,
    isSystem: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = LocalNeonScheme.current
    val initial = label.trim().take(1).uppercase().ifBlank { "N" }
    val ringBrush = Brush.linearGradient(
        colors = listOf(
            scheme.primary,
            scheme.accent,
            Color.White.copy(alpha = 0.65f)
        )
    )
    val monogramGradient = Brush.linearGradient(
        colors = listOf(
            scheme.accent.copy(alpha = 0.55f),
            scheme.primary.copy(alpha = 0.35f)
        )
    )

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .border(width = 1.8.dp, brush = ringBrush, shape = CircleShape)
            .padding(2.2.dp)
            .clip(CircleShape)
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
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF6366F1))
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(monogramGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = (sizeDp.value * 0.36f).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(monogramGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = (sizeDp.value * 0.36f).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * 2026 iOS 18 Frosted Liquid Glass Row:
 * `RoundedCornerShape(24.dp)`, `minHeight = 72.dp`, `1.dp` specular glass gradient border,
 * circular avatar with glowing cyan-violet ring (`NeonAvatar`), clean bold typography.
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
    val shape = RoundedCornerShape(24.dp)

    val glassOverlay = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.13f),
            Color.White.copy(alpha = 0.04f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = if (danger) {
            listOf(
                scheme.danger.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.18f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.38f),
                scheme.primary.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.08f)
            )
        }
    )

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
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(scheme.surface)
            .background(glassOverlay)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(clickMod)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        if (hasAvatar) {
            NeonAvatar(
                avatarUrl = avatarUrl,
                label = avatarLabel?.ifBlank { title } ?: title,
                sizeDp = 46.dp
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
                fontWeight = FontWeight.Bold,
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
                    tint = scheme.muted.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 2026 iOS 18 Liquid Glass Pill LanguageSwitcher:
 * `RU` | `KY` | `EN` pill switcher (`RoundedCornerShape(18.dp)`, frosted glass border + active aurora capsule).
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
    val outerShape = RoundedCornerShape(18.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.36f),
            scheme.primary.copy(alpha = 0.22f)
        )
    )

    Row(
        modifier = modifier
            .clip(outerShape)
            .background(scheme.surface)
            .background(Color.White.copy(alpha = 0.06f))
            .border(width = 1.dp, brush = borderBrush, shape = outerShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { code ->
            val isActive = activeLang == code
            val pillShape = RoundedCornerShape(14.dp)
            val activeBrush = Brush.horizontalGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = 0.35f),
                    scheme.accent.copy(alpha = 0.45f)
                )
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .heightIn(min = 34.dp)
                    .clip(pillShape)
                    .then(
                        if (isActive) {
                            Modifier
                                .background(activeBrush)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.35f),
                                    shape = pillShape
                                )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectLang(code) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code.uppercase(),
                    color = if (isActive) scheme.text else scheme.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
