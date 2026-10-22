package app.selfup.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.selfup.R
import app.selfup.core.Stat

/**
 * The palette of the System's windows in Solo Leveling: near-black space, one electric blue
 * for every frame and title, ice-white text, and red kept for penalties and warnings.
 */
object SystemColors {
    /** The void behind the windows: black with the faintest navy, so blue glow reads as light. */
    val Void = Color(0xFF02050B)

    /** The frame, title and glow blue. */
    val Blue = Color(0xFF45C4FF)

    /** Brighter partner of [Blue] for titles and the hottest part of a glow. */
    val BlueBright = Color(0xFFA8E8FF)

    /** Deep blue of a window's glass, drawn translucent over the void. */
    val Pane = Color(0xFF061A2E)

    val Text = Color(0xFFE8F5FF)
    val TextMuted = Color(0xFF86A3BF)

    /** The penalty window red. */
    val Danger = Color(0xFFFF4A5C)

    /** Rewards and records, the gold the System pays in. */
    val Gold = Color(0xFFFFCB57)

    val Success = Color(0xFF5EE8A6)

    /** Dim frame lines: separators, inactive borders and tracks. */
    val Line = Blue.copy(alpha = 0.18f)
}

/** Kept for the few surfaces that tint by alpha rather than by colour. */
object Surfaces {
    val Fill = SystemColors.Pane.copy(alpha = 0.62f)
    val FillPressed = SystemColors.Blue.copy(alpha = 0.14f)
    val Separator = SystemColors.Line
}

val Ink = SystemColors.Void

private val Colors = darkColorScheme(
    primary = SystemColors.Blue,
    onPrimary = Color(0xFF00131F),
    primaryContainer = Color(0xFF0B3350),
    onPrimaryContainer = SystemColors.BlueBright,
    secondary = SystemColors.BlueBright,
    onSecondary = Color(0xFF00131F),
    secondaryContainer = Color(0xFF0B2A42),
    onSecondaryContainer = SystemColors.BlueBright,
    tertiary = SystemColors.Gold,
    background = SystemColors.Void,
    onBackground = SystemColors.Text,
    surface = Color(0xFF050F1C),
    onSurface = SystemColors.Text,
    surfaceVariant = Color(0xFF0A1C2E),
    onSurfaceVariant = SystemColors.TextMuted,
    surfaceContainerLowest = Color(0xFF02070F),
    surfaceContainerLow = Color(0xFF040C17),
    surfaceContainer = Color(0xFF06111F),
    surfaceContainerHigh = Color(0xFF0A1828),
    surfaceContainerHighest = Color(0xFF0E1F33),
    outline = Color(0xFF1F4566),
    outlineVariant = Color(0xFF14304A),
    error = SystemColors.Danger,
    onError = Color(0xFF1F0005),
)

/** Rajdhani: squared, technical letterforms close to the System's window lettering. */
val SystemFont = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** The System's titles carry a soft blue bloom, like light coming off the window. */
fun glow(color: Color = SystemColors.Blue, radius: Float = 14f) = Shadow(color.copy(alpha = 0.75f), blurRadius = radius)

/**
 * Display, titles and labels in Rajdhani, the way the System letters its windows; body copy
 * stays in the platform sans so longer reading is easy.
 */
private val Type = Typography().run {
    fun TextStyle.system(size: Int, weight: FontWeight, tracking: Double = 0.02, height: Int? = null) = copy(
        fontFamily = SystemFont,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        lineHeight = (height ?: (size * 1.2).toInt()).sp,
    )
    copy(
        displayLarge = displayLarge.system(76, FontWeight.Bold, tracking = 0.0, height = 76),
        displayMedium = displayMedium.system(56, FontWeight.Bold, tracking = 0.0, height = 58),
        headlineLarge = headlineLarge.system(32, FontWeight.Bold, tracking = 0.06),
        headlineSmall = headlineSmall.system(24, FontWeight.Bold, tracking = 0.03),
        titleLarge = titleLarge.system(22, FontWeight.Bold, tracking = 0.02),
        titleMedium = titleMedium.system(18, FontWeight.Bold, tracking = 0.02),
        titleSmall = titleSmall.system(16, FontWeight.Bold, tracking = 0.02),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = labelLarge.system(16, FontWeight.Bold, tracking = 0.08),
        labelMedium = labelMedium.system(14, FontWeight.SemiBold, tracking = 0.04),
        labelSmall = labelSmall.system(13, FontWeight.SemiBold, tracking = 0.03, height = 16),
    )
}

/** Uppercase, widely tracked label, used for window titles and section headers. */
val SectionLabel = TextStyle(
    fontFamily = SystemFont,
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.16.em,
)

/** Small and sharp: the System's windows are cut glass, not pills. */
private val SystemShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun SelfUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = SystemShapes) {
        // Screens sit on transparent scaffolds, which hand down black content color by default.
        CompositionLocalProvider(LocalContentColor provides SystemColors.Text, content = content)
    }
}

/**
 * Each stat's accent, for its bar, tags and the glow of its rows. Tuned to sit with the System
 * blue; Strength is ember orange so it never reads as the penalty red.
 */
val Stat.color: Color
    get() = when (this) {
        Stat.STRENGTH -> Color(0xFFFF8A3D)
        Stat.NUTRITION -> Color(0xFF52E3A0)
        Stat.TECHNICAL -> Color(0xFF45C4FF)
        Stat.SPEAKING -> Color(0xFFFFB84D)
        Stat.SOCIAL -> Color(0xFFFF7AC6)
        Stat.LIFE -> Color(0xFFA48BFF)
        Stat.MONEY -> Color(0xFFFFCB57)
    }

/** A lighter partner for gradients and glow tips. */
val Stat.colorLight: Color
    get() = when (this) {
        Stat.STRENGTH -> Color(0xFFFFC09A)
        Stat.NUTRITION -> Color(0xFFA6F5CF)
        Stat.TECHNICAL -> Color(0xFFA8E8FF)
        Stat.SPEAKING -> Color(0xFFFFD99A)
        Stat.SOCIAL -> Color(0xFFFFB5E0)
        Stat.LIFE -> Color(0xFFD2C6FF)
        Stat.MONEY -> Color(0xFFFFE6A6)
    }

/** The three-letter tag the status window uses, like STR and AGI in the System. */
val Stat.abbreviation: String
    get() = when (this) {
        Stat.STRENGTH -> "STR"
        Stat.NUTRITION -> "NUT"
        Stat.TECHNICAL -> "TEC"
        Stat.SPEAKING -> "SPK"
        Stat.SOCIAL -> "SOC"
        Stat.LIFE -> "LIF"
        Stat.MONEY -> "MNY"
    }
