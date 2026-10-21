package app.selfup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selfup.core.Rank
import app.selfup.core.StatChange
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                visible.targetState = false
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(targetScale = 0.9f) + fadeOut(),
        ) {
            Column(
                Modifier
                    .shadow(32.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "LEVEL UP",
                    color = accent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
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
