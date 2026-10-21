package app.selfup.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.selfup.core.StatProgress
import app.selfup.ui.glass.frosted
import app.selfup.ui.theme.color
import app.selfup.ui.theme.colorLight

/** A stat tile: its ring with the level inside, name, rank and XP to the next level. */
@Composable
fun StatCard(stat: StatProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .pressScale(interaction)
            .frosted(shape, tint = stat.stat.color.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(14.dp)
            .semantics {
                contentDescription = "${stat.stat.displayName}, level ${stat.level}, ${stat.rank.displayName}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatRing(stat, size = 46.dp, stroke = 5.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stat.stat.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                stat.rank.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = stat.stat.color,
            )
            Text(
                "${stat.progress.xpToAdvance - stat.progress.xpIntoLevel} XP to go",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A thin gradient progress bar through the current level. */
@Composable
fun XpBar(stat: StatProgress, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = stat.progress.fraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 60f),
        label = "xp",
    )
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(stat.stat.color.copy(alpha = 0.16f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(stat.stat.color, stat.stat.colorLight))),
        )
    }
}
