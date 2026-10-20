@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.stat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selfup.core.Stat
import app.selfup.data.XpEventEntity
import app.selfup.data.XpReason
import app.selfup.ui.components.XpBar
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatDetailScreen(viewModel: StatDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stat = state.progress.stat
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        "Lv ${state.progress.level}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = stat.color,
                    )
                    Text(state.progress.rank.displayName, style = MaterialTheme.typography.titleMedium)
                    XpBar(state.progress)
                    Text(
                        "${state.progress.progress.xpIntoLevel} / ${state.progress.progress.xpToAdvance} XP to Lv ${state.progress.level + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle("Quests that feed ${stat.displayName}") }
            if (state.feeding.isEmpty()) {
                item { Muted("None yet. Tag a quest with ${stat.displayName} to grow it.") }
            }
            items(state.feeding, key = { "q-${it.id}" }) { quest ->
                Column {
                    Text(quest.title, style = MaterialTheme.typography.bodyLarge)
                    Muted(questSubtitle(quest))
                }
            }
            item { SectionTitle("Recent XP") }
            if (state.recent.isEmpty()) {
                item { Muted("No XP yet.") }
            }
            items(state.recent, key = { "e-${it.id}" }) { event -> XpEventRow(event) }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
private fun XpEventRow(event: XpEventEntity) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                when (event.reason) {
                    XpReason.COMPLETION -> "Quest completed"
                    XpReason.PENALTY -> "Missed daily"
                    XpReason.UNDO -> "Undone"
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
