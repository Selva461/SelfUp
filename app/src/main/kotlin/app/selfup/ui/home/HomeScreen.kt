package app.selfup.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selfup.core.Quest
import app.selfup.core.Stat
import app.selfup.ui.TodayState
import app.selfup.ui.components.QuestRow
import app.selfup.ui.components.StatCard

@Composable
fun HomeScreen(
    state: TodayState,
    contentPadding: PaddingValues,
    onComplete: (Quest) -> Unit,
    onEdit: (Quest) -> Unit,
    onStatClick: (Stat) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text("OVERALL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Level ${state.overallLevel}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Two stat cards per row.
        items(state.stats.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { stat ->
                    StatCard(stat, onClick = { onStatClick(stat.stat) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("Today", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "${state.doneCount} / ${state.quests.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(state.quests, key = { it.quest.id }) { item ->
            QuestRow(
                quest = item.quest,
                done = item.isDone,
                onTap = { if (!item.isDone) onComplete(item.quest) },
                onLongPress = { onEdit(item.quest) },
            )
        }
    }
}
