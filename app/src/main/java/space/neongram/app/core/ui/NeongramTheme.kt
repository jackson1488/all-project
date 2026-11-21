package space.neongram.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class NeonScheme(
    val background: Color = Color(0xFF07080F),
    val surface: Color = Color(0xFF0E101C),
    val card: Color = Color(0xFF15182B),
    val cardBorder: Color = Color(0xFF232846),
    val accentCyan: Color = Color(0xFF00F0FF),
    val accentPurple: Color = Color(0xFF8B5CF6),
    val accentPink: Color = Color(0xFFEC4899),
    val outgoingBubble: Color = Color(0xFF251A4A),
    val incomingBubble: Color = Color(0xFF141829),
    val textPrimary: Color = Color(0xFFF3F6FF),
    val textSecondary: Color = Color(0xFF9BA3C7),
    val textMuted: Color = Color(0xFF5F678A),
    val onlineGreen: Color = Color(0xFF10B981),
    val dangerRed: Color = Color(0xFFEF4444)
)

val LocalNeonScheme = staticCompositionLocalOf { NeonScheme() }

private val NeonDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFFEC4899),
    background = Color(0xFF07080F),
    surface = Color(0xFF0E101C),
    onPrimary = Color(0xFF07080F),
    onBackground = Color(0xFFF3F6FF),
    onSurface = Color(0xFFF3F6FF)
)

@Composable
fun NeongramTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonDarkColorScheme,
        content = content
    )
}
