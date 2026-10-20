package app.selfup.ui.quests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.core.Quest
import app.selfup.ui.TodayState
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
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            // Room for the add button.
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Header("Today · ${state.doneCount} of ${state.quests.size} done") }
        items(state.quests, key = { "today-${it.quest.id}" }) { item ->
            QuestRow(
                quest = item.quest,
                done = item.isDone,
                onTap = { if (!item.isDone) onComplete(item.quest) },
                onLongPress = { onEdit(item.quest) },
            )
        }
        if (others.isNotEmpty()) {
            item { Header("Not today") }
            items(others, key = { "other-${it.id}" }) { quest ->
                QuestRow(quest = quest, done = false, onTap = { onEdit(quest) }, onLongPress = { onEdit(quest) })
            }
        }
        item {
            Text(
                "Tap to complete. Long-press to edit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}
