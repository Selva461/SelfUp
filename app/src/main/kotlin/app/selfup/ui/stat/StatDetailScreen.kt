@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.stat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selfup.core.Stat
import app.selfup.core.XpRules
import app.selfup.data.XpEventEntity
import app.selfup.data.XpReason
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.StatRing
import app.selfup.ui.components.XpBar
import app.selfup.ui.components.XpPill
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.glass.frosted
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatDetailScreen(viewModel: StatDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stat = state.progress.stat
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stat.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hero") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .frosted(tint = stat.color.copy(alpha = 0.06f))
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        StatRing(state.progress, size = 168.dp, stroke = 16.dp, showLevel = false)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LEVEL", style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.progress.level}", style = MaterialTheme.typography.displayMedium, color = stat.color)
                        }
                    }
                    Text(state.progress.rank.displayName, style = MaterialTheme.typography.titleLarge)
                    XpBar(state.progress)
                    Text(
                        "${state.progress.progress.xpIntoLevel} / ${state.progress.progress.xpToAdvance} XP to Lv ${state.progress.level + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "feeding-header") { GroupHeader("Quests that feed ${stat.displayName}") }
            item(key = "feeding") {
                GlassGroup {
                    if (state.feeding.isEmpty()) {
                        Muted("None yet. Tag a quest with ${stat.displayName} to grow it.", Modifier.padding(16.dp))
                    }
                    state.feeding.forEachIndexed { i, quest ->
                        if (i > 0) GroupSeparator(start = 16.dp)
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(quest.title, style = MaterialTheme.typography.bodyLarge)
                                Muted(questSubtitle(quest))
                            }
                            XpPill(XpRules.completionXp(quest.difficulty), stat.color)
                        }
                    }
                }
            }
            item(key = "recent-header") { GroupHeader("Recent XP") }
            item(key = "recent") {
                GlassGroup {
                    if (state.recent.isEmpty()) Muted("No XP yet.", Modifier.padding(16.dp))
                    state.recent.forEachIndexed { i, event ->
                        if (i > 0) GroupSeparator(start = 16.dp)
                        XpEventRow(event)
                    }
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
private fun XpEventRow(event: XpEventEntity) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                when (event.reason) {
                    XpReason.COMPLETION -> "Quest completed"
                    XpReason.PENALTY -> "Missed daily"
                    XpReason.UNDO -> "Undone"
                    XpReason.RECORD -> "Personal best"
                    XpReason.SKILL -> "Skill practice"
                    else -> event.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Muted(LocalDate.ofEpochDay(event.epochDay).format(dayFormat))
        }
        Text(
            if (event.delta >= 0) "+${event.delta}" else "${event.delta}",
            style = MaterialTheme.typography.titleMedium,
            color = if (event.delta >= 0) event.statColor() else MaterialTheme.colorScheme.error,
        )
    }
}

private fun XpEventEntity.statColor(): Color = Stat.fromKey(stat).color

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
