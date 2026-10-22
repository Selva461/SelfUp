package app.selfup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.selfup.core.CompletionInput
import app.selfup.core.Quest
import app.selfup.core.XpRules
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.color
import app.selfup.ui.theme.glow

/**
 * One quest, as a goal line in a quest window: a check, the title with its stats and reward,
 * and the System's [0/1] counter. Tap completes it (logging quests open a number entry
 * first), long-press edits it. A make-up quest is marked as a penalty, in red.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestRow(
    quest: Quest,
    done: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    showCounter: Boolean = true,
) {
    val stats = quest.stats.sortedBy { it.ordinal }
    val penalty = quest.penaltyFor != null
    val accent = if (penalty) SystemColors.Danger else stats.first().color
    val interaction = remember { MutableInteractionSource() }
    val titleColor by animateColorAsState(if (done) SystemColors.TextMuted else SystemColors.Text, label = "title")
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onClick = onTap,
                onLongClick = onLongPress,
                onLongClickLabel = "Edit quest",
            )
            .pressScale(interaction, 0.985f)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(quest.title)
                    if (penalty) append(", penalty quest")
                    append(if (done) ", done" else ", ${XpRules.completionXp(quest.difficulty)} XP")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SystemCheck(done, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                quest.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (penalty) Tag("Penalty", SystemColors.Danger)
                Text(
                    "+${XpRules.completionXp(quest.difficulty)} XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done) SystemColors.TextMuted else accent,
                    maxLines = 1,
                )
                Text(
                    questSubtitle(quest),
                    style = MaterialTheme.typography.labelSmall,
                    color = SystemColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showCounter) {
            Text(
                if (done) "[1/1]" else "[0/1]",
                style = MaterialTheme.typography.titleSmall.copy(shadow = if (done) glow(radius = 8f) else null),
                color = if (done) SystemColors.Blue else SystemColors.TextMuted,
            )
        }
    }
}

/** Stat names and markers, without the XP (the row shows that). */
fun questSubtitle(quest: Quest): String {
    val parts = buildList {
        add(quest.stats.sortedBy { it.ordinal }.joinToString(" · ") { it.displayName })
        if (quest.input is CompletionInput.Number) add("Log")
    }
    return parts.joinToString(" · ")
}
