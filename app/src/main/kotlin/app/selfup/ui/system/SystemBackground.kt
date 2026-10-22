package app.selfup.ui.system

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import app.selfup.ui.theme.SystemColors
import kotlin.math.sin
import kotlin.random.Random

/** One mote of light drifting up through the void. */
private class Mote(val x: Float, val y: Float, val speed: Float, val size: Float, val alpha: Float, val sway: Float)

/**
 * The space behind the System's windows: black void, a cold blue light from above, a faint
 * violet shadow below, and motes of mana drifting slowly upward.
 *
 * It's one Canvas with a few gradients and ~40 dots, no blur pass, so it costs almost nothing
 * per frame. [accent] tints the top light, for example on a stat's screen.
 */
@Composable
fun SystemBackground(modifier: Modifier = Modifier, accent: Color = SystemColors.Blue) {
    val transition = rememberInfiniteTransition(label = "void")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val motes = remember {
        val random = Random(7)
        List(42) {
            Mote(
                x = random.nextFloat(),
                y = random.nextFloat(),
                speed = 0.6f + random.nextFloat() * 1.4f,
                size = 0.6f + random.nextFloat() * 1.4f,
                alpha = 0.10f + random.nextFloat() * 0.35f,
                sway = random.nextFloat() * 6.28f,
            )
        }
    }
    Canvas(modifier) {
        drawRect(SystemColors.Void)
        light(accent, 0.20f, Offset(size.width * 0.5f, -size.height * 0.08f), radius = size.maxDimension * 0.75f)
        light(SystemColors.Blue, 0.07f, Offset(size.width * 1.05f, size.height * 0.45f), radius = size.maxDimension * 0.5f)
        light(Color(0xFF6B4BFF), 0.08f, Offset(size.width * 0.0f, size.height * 1.02f), radius = size.maxDimension * 0.6f)
        motes.forEach { mote ->
            // Each mote rises at its own pace and wraps around, swaying a little side to side.
            val y = ((mote.y - time * mote.speed) % 1f + 1f) % 1f
            val x = mote.x + 0.012f * sin(time * 6.28f * 3 + mote.sway)
            val center = Offset(x * size.width, y * size.height)
            val r = mote.size.dp.toPx()
            // Fade in at the bottom and out near the top, so wrapping is never visible.
            val edge = (minOf(y, 1f - y) * 6f).coerceIn(0f, 1f)
            drawCircle(SystemColors.BlueBright.copy(alpha = mote.alpha * edge), radius = r, center = center)
            drawCircle(SystemColors.Blue.copy(alpha = mote.alpha * edge * 0.25f), radius = r * 4f, center = center)
        }
    }
}

private fun DrawScope.light(color: Color, alpha: Float, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.3f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
