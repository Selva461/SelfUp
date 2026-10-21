package app.selfup.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.core.Quest
import app.selfup.core.Stat
import app.selfup.ui.TodayState
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.LargeTitle
import app.selfup.ui.components.QuestRow
import app.selfup.ui.components.StatCard
import app.selfup.ui.components.StatRings
import app.selfup.ui.glass.frosted
import app.selfup.ui.theme.color
import java.time.format.DateTimeFormatter

private val eyebrowFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
fun HomeScreen(
    state: TodayState,
    contentPadding: PaddingValues,
    onComplete: (Quest) -> Unit,
    onEdit: (Quest) -> Unit,
    onStatClick: (Stat) -> Unit,
    extra: LazyListScope.() -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") { LargeTitle("Today", eyebrow = state.day?.format(eyebrowFormat)) }
        item(key = "hero") { Hero(state) }
        extra()
        // Two stat tiles per row.
        items(state.stats.chunked(2), key = { pair -> pair.joinToString { it.stat.key } }) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { stat ->
                    StatCard(stat, onClick = { onStatClick(stat.stat) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "quests") {
            Column {
                GroupHeader("Today's quests") {
                    Text(
                        "${state.doneCount} of ${state.quests.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.size(6.dp))
                if (state.loaded && state.quests.isEmpty()) {
                    GlassGroup {
                        Text(
                            "Nothing due today. Add a quest from the Quests tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                } else if (state.quests.isNotEmpty()) {
                    GlassGroup {
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
            }
        }
    }
}

/** The rings with a legend, like the Fitness summary: one ring per stat, level in the middle. */
@Composable
private fun Hero(state: TodayState) {
    Row(
        Modifier
            .fillMaxWidth()
            .frosted()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatRings(state.stats, state.overallLevel, size = 184.dp, stroke = 10.dp, gap = 3.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.stats.forEach { stat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(stat.stat.color, CircleShape),
                    )
                    Text(
                        stat.stat.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text("${(stat.progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = stat.stat.color)
                }
            }
        }
    }
}
