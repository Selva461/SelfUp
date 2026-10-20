@file:OptIn(ExperimentalHazeApi::class)

package app.selfup.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.ui.theme.Surfaces
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Two capture layers, following Apple's split between content and the navigation layer:
 * - [backdrop] holds only the ambient background. Content cards frost it.
 * - [chrome] also holds the scrolling content. Floating controls (tab bar, add button,
 *   level-up card) refract it as Liquid Glass.
 */
class GlassLayers(val backdrop: HazeState, val chrome: HazeState)

val LocalGlassLayers = compositionLocalOf<GlassLayers?> { null }

/**
 * Frosted content surface: backdrop blur, a whisper of white, and a top-lit hairline.
 * Cheap enough to use on every card. Without layers it falls back to a plain translucent fill.
 */
@Composable
fun Modifier.frosted(shape: RoundedCornerShape = RoundedCornerShape(22.dp), tint: Color = Surfaces.Fill): Modifier {
    val layers = LocalGlassLayers.current
    val style = remember(tint) {
        HazeBlurStyle {
            blurRadius(28.dp)
            backgroundColor(Color(0xFF07080D))
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            noiseFactor(0.04f)
        }
    }
    val base = clip(shape)
    val surface = if (layers != null) {
        base.hazeBlur(input = HazeInput.Sources(layers.backdrop), style = style)
    } else {
        base.background(tint)
    }
    return surface.hairline(shape)
}

/** The 1px light-catching edge: brighter on top, fading toward the bottom. */
fun Modifier.hairline(shape: RoundedCornerShape, width: Dp = 1.dp): Modifier = border(
    width = width,
    brush = Brush.verticalGradient(listOf(Surfaces.HairlineTop, Surfaces.HairlineBottom)),
    shape = shape,
)

/**
 * Liquid Glass for floating controls: refraction at the edges, depth blur, specular
 * highlight lit from the top, and a gentle press response.
 */
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    tint: Color = Color.White.copy(alpha = 0.06f),
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val layers = LocalGlassLayers.current ?: return clip(shape).background(Surfaces.FillPressed).hairline(shape)
    val style = remember(shape, tint) {
        GlassStyle.regular then GlassStyle {
            shape(shape)
            tint(tint)
            backgroundColor(Color(0xFF0A0B10))
            lightPosition(Alignment.TopCenter)
            specularIntensity(0.55f)
            pressed {
                lightingIntensity(1f)
                refractionMultiplier(1.08f)
                scale(0.97f)
            }
        }
    }
    return hazeGlass(
        input = HazeInput.Sources(layers.chrome),
        style = style,
        interactionSource = interactionSource,
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
    ).clip(shape)
}
