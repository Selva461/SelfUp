package app.selfup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.selfup.core.Stat

private val Background = Color(0xFF07080C)
private val Surface = Color(0xFF12141B)
private val SurfaceHigh = Color(0xFF1B1E28)
private val Accent = Color(0xFF7CC4FF)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00213A),
    secondary = Color(0xFFB9A6FF),
    background = Background,
    onBackground = Color(0xFFE7E9F0),
    surface = Surface,
    onSurface = Color(0xFFE7E9F0),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFF9EA3B5),
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    outline = Color(0xFF3A3F50),
)

@Composable
fun SelfUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

/** Each stat's accent, used for its bar, glow and chips. */
val Stat.color: Color
    get() = when (this) {
        Stat.STRENGTH -> Color(0xFFFF7A6B)
        Stat.NUTRITION -> Color(0xFF7BE0A0)
        Stat.TECHNICAL -> Color(0xFF7CC4FF)
        Stat.SPEAKING -> Color(0xFFFFC46B)
        Stat.SOCIAL -> Color(0xFFFF8FD1)
        Stat.LIFE -> Color(0xFFB9A6FF)
        Stat.MONEY -> Color(0xFFF2E27A)
    }
