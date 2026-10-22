package app.selfup.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.selfup.core.Rank
import app.selfup.core.StatChange
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.glow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single level-up moment, as the System announces it: a notification window with
 * "You have leveled up!". When a completion levels up several stats they share the window.
 * Dismisses itself, or on tap.
 */
@Composable
fun LevelUpOverlay(changes: List<StatChange>, onDismiss: () -> Unit) {
    CelebrationOverlay(
        label = "You have leveled up!",
        headline = "LV. ${changes.maxOf { it.levelAfter }}",
        message = levelUpMessage(changes),
        accent = SystemColors.Blue,
        key = changes,
        onDismiss = onDismiss,
    )
}

/**
 * A System notification window for the moments worth stopping for. It opens the way the
 * System's windows do: a line of light draws across, then the window unfolds from it.
 */
@Composable
fun CelebrationOverlay(
    label: String,
    headline: String,
    message: String,
    accent: Color,
    key: Any,
    onDismiss: () -> Unit,
) {
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            open.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    LaunchedEffect(key) {
        open.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(key) {
        delay(2_800)
        close()
    }

    val scrim by animateFloatAsState(if (closing) 0f else 0.62f, tween(300), label = "scrim")
    // A one-shot burst of light behind the window as it lands.
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(durationMillis = 1_400, easing = FastOutSlowInEasing)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            val radius = size.minDimension * (0.25f + 0.6f * t)
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.45f * (1f - t)), accent.copy(alpha = 0f)), center = center, radius = radius),
                radius = radius,
            )
        }
        val t = open.value
        SystemWindow(
            title = "Notification",
            accent = accent,
            notice = true,
            spacing = 10.dp,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .graphicsLayer {
                    // First the width draws out as a bright line, then the height unfolds.
                    val wide = (t * 2f).coerceIn(0f, 1f)
                    val tall = ((t - 0.35f) / 0.65f).coerceIn(0f, 1.2f)
                    scaleX = 0.2f + 0.8f * wide
                    scaleY = 0.015f + 0.985f * tall
                    alpha = (t * 3f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin.Center
                },
        ) {
            SystemLine("[ $label ]", color = SystemColors.Text)
            Text(
                headline,
                style = MaterialTheme.typography.displayLarge.copy(shadow = glow(accent, 30f)),
                color = SystemColors.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = SystemColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

fun levelUpMessage(changes: List<StatChange>): String = changes.joinToString("\n") { change ->
    val newRank = Rank.forLevel(change.levelAfter)
    val rankUp = newRank != Rank.forLevel(change.levelBefore)
    buildString {
        append("${change.stat.displayName} reached Lv ${change.levelAfter}")
        if (rankUp) append(" · now ${newRank.displayName}")
    }
}
