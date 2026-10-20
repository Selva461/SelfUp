package app.selfup.ui.quests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.core.Quest
import app.selfup.ui.TodayState
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.LargeTitle
import app.selfup.ui.components.QuestRow

/** Today's quests first, then every other quest so off-day and finished ones stay editable. */
@Composable
fun QuestsScreen(
    state: TodayState,
    allQuests: List<Quest>,
    contentPadding: PaddingValues,
    onComplete: (Quest) -> Unit,
    onEdit: (Quest) -> Unit,
) {
    val todayIds = state.quests.map { it.quest.id }.toSet()
    val others = allQuests.filter { it.id !in todayIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { LargeTitle("Quests") }
        item(key = "today-header") {
            GroupHeader("Today") {
                Text(
                    "${state.doneCount} of ${state.quests.size} done",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item(key = "today") {
            GlassGroup {
                if (state.quests.isEmpty()) {
                    Text(
                        "Nothing due today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp),
                    )
                }
                state.quests.forEachIndexed { i, item ->
                    if (i > 0) GroupSeparator()
                    QuestRow(
                        quest = item.quest,
                        done = item.isDone,
                        onTap = { if (!item.isDone) onComplete(item.quest) },
                        onLongPress = { onEdit(item.quest) },
                    )
                }
            }
        }
        if (others.isNotEmpty()) {
            item(key = "other-header") { GroupHeader("Not today") }
            item(key = "other") {
                GlassGroup {
                    others.forEachIndexed { i, quest ->
                        if (i > 0) GroupSeparator()
                        QuestRow(quest = quest, done = false, onTap = { onEdit(quest) }, onLongPress = { onEdit(quest) })
                    }
                }
            }
        }
        item(key = "hint") {
            Text(
                "Tap to complete. Long-press to edit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
    }
}
