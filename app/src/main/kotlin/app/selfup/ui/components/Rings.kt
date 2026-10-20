package app.selfup.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.core.StatProgress
import app.selfup.ui.theme.color
import app.selfup.ui.theme.colorLight

/** A progress value that springs to its target, starting from empty on first show. */
@Composable
private fun animatedFraction(target: Float): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow))
    }
    return anim.value
}

/**
 * Concentric progress rings, one per stat, outermost first: each shows progress through the
 * current level. The overall level sits in the middle.
 */
@Composable
fun StatRings(
    stats: List<StatProgress>,
    overallLevel: Int,
    modifier: Modifier = Modifier,
    size: Dp = 236.dp,
    stroke: Dp = 15.dp,
    gap: Dp = 4.dp,
) {
    val fractions = stats.map { animatedFraction(it.progress.fraction) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val step = strokePx + gap.toPx()
            stats.forEachIndexed { i, stat ->
                val inset = strokePx / 2 + i * step
                ring(stat, fractions[i], inset, strokePx)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "LEVEL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("$overallLevel", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** One stat's ring with its level inside, for cards and the stat screen. */
@Composable
fun StatRing(stat: StatProgress, size: Dp, stroke: Dp, modifier: Modifier = Modifier, showLevel: Boolean = true) {
    val fraction = animatedFraction(stat.progress.fraction)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { ring(stat, fraction, stroke.toPx() / 2, stroke.toPx()) }
        if (showLevel) {
            Text("${stat.level}", style = MaterialTheme.typography.titleMedium, color = stat.stat.color)
        }
    }
}

private fun DrawScope.ring(stat: StatProgress, fraction: Float, inset: Float, strokePx: Float) {
    val base = stat.stat.color
    val diameter = size.minDimension - inset * 2
    if (diameter <= 0f) return
    val topLeft = Offset(inset, inset)
    val arcSize = Size(diameter, diameter)

    // Track: the same hue, very dim, so empty rings still read as that stat.
    drawArc(
        color = base.copy(alpha = 0.16f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(strokePx),
    )
    if (fraction <= 0f) return

    // Start at 12 o'clock; the gradient brightens toward the leading cap.
    val sweep = (fraction * 360f).coerceAtLeast(2f)
    rotate(-90f) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to base,
                (sweep / 360f) to stat.stat.colorLight,
                1f to base,
                center = center,
            ),
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokePx, cap = StrokeCap.Round),
        )
    }
    // A soft glow on the leading cap, like light catching the end of the ring.
    val angle = Math.toRadians((sweep - 90f).toDouble())
    val r = diameter / 2
    val cap = Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat())
    drawCircle(
        brush = Brush.radialGradient(listOf(stat.stat.colorLight.copy(alpha = 0.55f), Color.Transparent), center = cap, radius = strokePx * 1.6f),
        radius = strokePx * 1.6f,
        center = cap,
    )
}
