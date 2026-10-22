@file:OptIn(ExperimentalHazeApi::class)

package app.selfup.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.ui.theme.SystemColors
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Two capture layers:
 * - [backdrop] holds only the background light. Windows blur it.
 * - [chrome] also holds the scrolling content, so the tab bar and notices blur what scrolls under them.
 */
class BlurLayers(val backdrop: HazeState, val chrome: HazeState)

val LocalBlurLayers = compositionLocalOf<BlurLayers?> { null }

/** Every System window has the same cut: barely rounded corners. */
val PanelShape = RoundedCornerShape(4.dp)

/** How hard a panel's frame glows. Windows glow; rows and chips only outline. */
enum class Glow { None, Soft, Full }

/**
 * A System window surface: the blurred void behind it tinted deep blue, a thin electric
 * frame with light bleeding outward, and bright brackets on the corners.
 *
 * The bleed is drawn as stacked strokes rather than a blur pass, so a screen full of
 * windows stays cheap. Without blur layers (previews, older paths) the fill is a plain tint.
 */
@Composable
fun Modifier.systemPanel(
    shape: RoundedCornerShape = PanelShape,
    tint: Color = SystemColors.Pane.copy(alpha = 0.62f),
    accent: Color = SystemColors.Blue,
    glow: Glow = Glow.Full,
    brackets: Boolean = glow == Glow.Full,
    layer: (BlurLayers) -> HazeState = { it.backdrop },
): Modifier {
    val layers = LocalBlurLayers.current
    val style = remember(tint) {
        HazeBlurStyle {
            blurRadius(24.dp)
            backgroundColor(SystemColors.Void)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            noiseFactor(0.05f)
        }
    }
    val bleed = if (glow == Glow.None) this else drawBehind { frameGlow(accent, glow) }
    val body = bleed.clip(shape).let {
        if (layers != null) it.hazeBlur(input = HazeInput.Sources(layer(layers)), style = style) else it.background(tint)
    }
    return body
        // A sheen across the top of the glass, as if the window is lit from above.
        .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.10f), 0.35f to Color.Transparent))
        .drawWithContent {
            drawContent()
            frame(accent, glow, brackets)
        }
}

/** Just the frame of a window, for surfaces that bring their own fill. */
fun Modifier.systemFrame(accent: Color = SystemColors.Blue, glow: Glow = Glow.None, brackets: Boolean = false): Modifier =
    drawWithContent {
        drawContent()
        frame(accent, glow, brackets)
    }

private val Radius = 4.dp

private fun DrawScope.frameGlow(accent: Color, glow: Glow) {
    val strength = if (glow == Glow.Full) 1f else 0.5f
    val r = CornerRadius(Radius.toPx())
    listOf(10.dp to 0.035f, 6.dp to 0.06f, 3.dp to 0.12f).forEach { (width, alpha) ->
        val w = width.toPx()
        drawRoundRect(
            color = accent.copy(alpha = alpha * strength),
            topLeft = Offset(-w / 4, -w / 4),
            size = Size(size.width + w / 2, size.height + w / 2),
            cornerRadius = r,
            style = Stroke(w),
        )
    }
}

private fun DrawScope.frame(accent: Color, glow: Glow, brackets: Boolean) {
    val line = 1.dp.toPx()
    val alpha = when (glow) {
        Glow.Full -> 0.85f
        Glow.Soft -> 0.55f
        Glow.None -> 0.30f
    }
    drawRoundRect(
        color = accent.copy(alpha = alpha),
        topLeft = Offset(line / 2, line / 2),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(Radius.toPx()),
        style = Stroke(line),
    )
    if (brackets) cornerBrackets(SystemColors.BlueBright.takeIf { accent == SystemColors.Blue } ?: accent)
}

/** L-shaped brackets on each corner, a little outside the frame, like a HUD target. */
private fun DrawScope.cornerBrackets(color: Color, length: Dp = 12.dp, width: Dp = 2.dp) {
    val l = length.toPx()
    val w = width.toPx()
    val o = w / 2
    val corners = listOf(
        Offset(o, o) to Offset(1f, 1f),
        Offset(size.width - o, o) to Offset(-1f, 1f),
        Offset(o, size.height - o) to Offset(1f, -1f),
        Offset(size.width - o, size.height - o) to Offset(-1f, -1f),
    )
    corners.forEach { (p, d) ->
        drawLine(color, p, Offset(p.x + d.x * l, p.y), strokeWidth = w, cap = StrokeCap.Square)
        drawLine(color, p, Offset(p.x, p.y + d.y * l), strokeWidth = w, cap = StrokeCap.Square)
    }
}

/** A plain 1px frame line, for small inner surfaces such as options and code blocks. */
fun Modifier.hairline(shape: RoundedCornerShape, width: Dp = 1.dp, color: Color = SystemColors.Blue.copy(alpha = 0.28f)): Modifier =
    border(width, color, shape)
