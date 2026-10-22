package app.selfup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.selfup.core.Rank
import app.selfup.core.StatProgress
import app.selfup.ui.system.PanelShape
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.abbreviation
import app.selfup.ui.theme.color
import app.selfup.ui.theme.colorLight
import app.selfup.ui.theme.glow

/**
 * The status window, after the System's own: the overall level large on the left, the
 * player's title and today's numbers beside it, then every stat with its level and bar.
 */
@Composable
fun StatusWindow(
    stats: List<StatProgress>,
    overallLevel: Int,
    fields: List<Pair<String, String>>,
    onStatClick: (StatProgress) -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemWindow(title = "Status", modifier = modifier, spacing = 14.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.widthIn(min = 104.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$overallLevel",
                    style = MaterialTheme.typography.displayMedium.copy(shadow = glow(radius = 24f)),
                    color = SystemColors.Text,
                )
                Text("LEVEL", style = SectionLabel, color = SystemColors.Blue)
            }
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(SystemColors.Blue.copy(alpha = 0f), SystemColors.Blue.copy(alpha = 0.6f), SystemColors.Blue.copy(alpha = 0f)))),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.forEach { (label, value) -> StatusField(label, value) }
            }
        }
        Rule()
        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                pair.forEach { StatCell(it, onClick = { onStatClick(it) }, modifier = Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A "LABEL: value" line, the way the status window lists NAME, JOB and TITLE. */
@Composable
fun StatusField(label: String, value: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = SystemColors.TextMuted, modifier = Modifier.width(70.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = SystemColors.Text, maxLines = 1)
    }
}

/** One stat in the status window: STR and its level, the bar to the next level, its XP. */
@Composable
fun StatCell(stat: StatProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val color = stat.stat.color
    Column(
        modifier
            .pressScale(interaction)
            .clip(PanelShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 4.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "${stat.stat.displayName}, level ${stat.level}, ${stat.rank.displayName}. " +
                    "${stat.progress.xpIntoLevel} of ${stat.progress.xpToAdvance} XP"
            },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stat.stat.abbreviation,
                style = MaterialTheme.typography.titleMedium.copy(shadow = glow(color, 10f)),
                color = color,
            )
            Spacer(Modifier.weight(1f))
            Text("${stat.level}", style = MaterialTheme.typography.headlineSmall, color = SystemColors.Text)
        }
        SystemBar(stat.progress.fraction, color, height = 4.dp, light = stat.stat.colorLight)
        Row {
            Text(stat.stat.displayName, style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                "${stat.progress.xpIntoLevel}/${stat.progress.xpToAdvance}",
                style = MaterialTheme.typography.labelSmall,
                color = SystemColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

/** The title the status window gives: the rank of the average stat level. */
fun playerTitle(stats: List<StatProgress>): String =
    if (stats.isEmpty()) Rank.NOVICE.displayName else Rank.forLevel(stats.sumOf { it.level } / stats.size).displayName
