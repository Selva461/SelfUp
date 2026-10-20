package app.selfup.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.selfup.core.Stat
import app.selfup.ui.theme.Ink
import app.selfup.ui.theme.color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow-drifting light behind everything: a few soft color fields in the stat palette.
 * It gives the glass something to bend. It's one Canvas of radial gradients with no blur
 * pass, so it costs almost nothing per frame.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, accent: Stat? = null) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 48_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val primary = (accent ?: Stat.TECHNICAL).color
    Canvas(modifier) {
        drawRect(Ink)
        glow(primary, 0.22f, center = orbit(0.18f, 0.12f, phase, 0.08f), radius = 0.85f)
        glow(Stat.LIFE.color, 0.16f, center = orbit(0.88f, 0.30f, phase + 2.1f, 0.10f), radius = 0.75f)
        glow(Stat.STRENGTH.color, 0.10f, center = orbit(0.30f, 0.78f, phase + 4.2f, 0.12f), radius = 0.9f)
        glow(Stat.NUTRITION.color, 0.08f, center = orbit(0.85f, 0.95f, phase + 1.0f, 0.08f), radius = 0.7f)
    }
}

private fun DrawScope.orbit(x: Float, y: Float, phase: Float, amplitude: Float) = Offset(
    x = size.width * (x + amplitude * cos(phase)),
    y = size.height * (y + amplitude * sin(phase * 0.8f)),
)

private fun DrawScope.glow(color: Color, alpha: Float, center: Offset, radius: Float) {
    val r = size.maxDimension * radius
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
}
