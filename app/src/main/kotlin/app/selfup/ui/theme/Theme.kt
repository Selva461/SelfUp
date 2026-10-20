package app.selfup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.selfup.core.Stat

/** Near-black with a faint blue cast, so glass has depth to refract instead of flat grey. */
val Ink = Color(0xFF05060A)

/** Hairline and fill alphas shared by every translucent surface. */
object Surfaces {
    val Fill = Color.White.copy(alpha = 0.055f)
    val FillPressed = Color.White.copy(alpha = 0.09f)
    val HairlineTop = Color.White.copy(alpha = 0.16f)
    val HairlineBottom = Color.White.copy(alpha = 0.04f)
    val Separator = Color.White.copy(alpha = 0.07f)
}

private val Colors = darkColorScheme(
    primary = Color(0xFF8AD4FF),
    onPrimary = Color(0xFF00213A),
    secondary = Color(0xFFC2B3FF),
    background = Ink,
    onBackground = Color(0xFFF2F3F7),
    surface = Color(0xFF101219),
    onSurface = Color(0xFFF2F3F7),
    surfaceVariant = Color(0xFF181B24),
    onSurfaceVariant = Color(0xFF8E93A6),
    surfaceContainer = Color(0xFF12141C),
    surfaceContainerHigh = Color(0xFF1A1D27),
    surfaceContainerLow = Color(0xFF0C0E14),
    outline = Color(0xFF2E3242),
    error = Color(0xFFFF6B6B),
)

/**
 * Type scale tuned like a native iOS hierarchy: large, tight display numerals; slightly
 * negative tracking on titles; calm, roomy body text.
 */
private val Type = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontSize = 72.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em),
        displayMedium = displayMedium.copy(fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.035).em),
        headlineLarge = headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
        titleLarge = titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
        titleMedium = titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.005).em),
        titleSmall = titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, letterSpacing = (-0.005).em, lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    )
}

/** Small caps-style section label, like grouped-list headers on iOS. */
val SectionLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em)

@Composable
fun SelfUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

/** Each stat's accent, used for its ring, glow and chips. */
val Stat.color: Color
    get() = when (this) {
        Stat.STRENGTH -> Color(0xFFFF6A5E)
        Stat.NUTRITION -> Color(0xFF5EE29A)
        Stat.TECHNICAL -> Color(0xFF5FB8FF)
        Stat.SPEAKING -> Color(0xFFFFB84D)
        Stat.SOCIAL -> Color(0xFFFF7AC6)
        Stat.LIFE -> Color(0xFFA996FF)
        Stat.MONEY -> Color(0xFFF5DA5B)
    }

/** A lighter partner for the ring's gradient tail. */
val Stat.colorLight: Color
    get() = when (this) {
        Stat.STRENGTH -> Color(0xFFFFA59B)
        Stat.NUTRITION -> Color(0xFFA6F5C8)
        Stat.TECHNICAL -> Color(0xFFA9DCFF)
        Stat.SPEAKING -> Color(0xFFFFD99A)
        Stat.SOCIAL -> Color(0xFFFFB5E0)
        Stat.LIFE -> Color(0xFFD3C8FF)
        Stat.MONEY -> Color(0xFFFFF0A6)
    }
