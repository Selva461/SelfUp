package app.selfup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selfup.core.Rank
import app.selfup.core.StatChange
import app.selfup.ui.glass.liquidGlass
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.color
import kotlinx.coroutines.delay

/**
 * The single level-up moment: one animation, one message. When a completion levels up
 * several stats at once they share the same card. Dismisses itself, or on tap.
 */
@Composable
fun LevelUpOverlay(changes: List<StatChange>, onDismiss: () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(changes) {
        delay(2_400)
        visible.targetState = false
    }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.targetState && visible.isIdle) onDismiss()
    }

    val accent = changes.first().stat.color
    // A one-shot burst of light behind the card: expands and fades as the card lands.
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(durationMillis = 1_400, easing = FastOutSlowInEasing)) }
    val scrim by animateFloatAsState(if (visible.targetState) 0.5f else 0f, tween(300), label = "scrim")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                visible.targetState = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            val radius = size.minDimension * (0.25f + 0.55f * t)
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.55f * (1f - t) * scrim * 2f), accent.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
        }
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.7f) + fadeIn(),
            exit = scaleOut(targetScale = 0.92f) + fadeOut(),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 32.dp)
                    .liquidGlass(RoundedCornerShape(32.dp), tint = accent.copy(alpha = 0.10f))
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "LEVEL UP",
                    style = SectionLabel,
                    color = accent,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Lv ${changes.maxOf { it.levelAfter }}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(levelUpMessage(changes), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
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
