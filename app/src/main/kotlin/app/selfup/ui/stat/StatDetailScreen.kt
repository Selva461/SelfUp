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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.selfup.ui.components.BackScaffold
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.StatusField
import app.selfup.ui.components.SystemBar
import app.selfup.ui.components.SystemDial
import app.selfup.ui.components.SystemGroup
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.XpTag
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.abbreviation
import app.selfup.ui.theme.color
import app.selfup.ui.theme.colorLight
import app.selfup.ui.theme.glow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatDetailScreen(viewModel: StatDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatDetailScreen(state, onBack)
}

/** One stat's window: its level on a dial, its title and XP, what feeds it, and recent XP. */
@Composable
fun StatDetailScreen(state: StatDetailState, onBack: () -> Unit) {
    val progress = state.progress
    val stat = progress.stat
    BackScaffold(stat.displayName, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hero") {
                SystemWindow(title = stat.abbreviation, accent = stat.color, spacing = 16.dp) {
                    Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                        SystemDial(progress.progress.fraction, stat.color, size = 176.dp, stroke = 12.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LEVEL", style = SectionLabel, color = SystemColors.TextMuted)
                            Text(
                                "${progress.level}",
                                style = MaterialTheme.typography.displayMedium.copy(shadow = glow(stat.color, 24f)),
                                color = SystemColors.Text,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusField("Title", progress.rank.displayName)
                        StatusField("XP", "${progress.progress.xpIntoLevel} / ${progress.progress.xpToAdvance}")
                        StatusField("Next", "Lv ${progress.level + 1} in ${progress.progress.xpToAdvance - progress.progress.xpIntoLevel} XP")
                    }
                    SystemBar(progress.progress.fraction, stat.color, height = 6.dp, light = stat.colorLight)
                }
            }
            item(key = "feeding-header") { GroupHeader("Quests that feed ${stat.displayName}") }
            item(key = "feeding") {
                SystemGroup {
                    if (state.feeding.isEmpty()) {
                        MutedText("None yet. Tag a quest with ${stat.displayName} to grow it.", Modifier.padding(16.dp))
                    }
                    state.feeding.forEachIndexed { i, quest ->
                        if (i > 0) GroupSeparator(start = 16.dp)
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(quest.title, style = MaterialTheme.typography.bodyLarge)
                                MutedText(questSubtitle(quest))
                            }
                            XpTag(XpRules.completionXp(quest.difficulty), stat.color)
                        }
                    }
                }
            }
            item(key = "recent-header") { GroupHeader("Recent XP") }
            item(key = "recent") {
                SystemGroup {
                    if (state.recent.isEmpty()) MutedText("No XP yet.", Modifier.padding(16.dp))
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

/** What earned (or cost) the XP, in words. */
private fun reasonLabel(reason: String): String = when (reason) {
    XpReason.COMPLETION -> "Quest completed"
    XpReason.PENALTY -> "Penalty: missed daily"
    XpReason.UNDO -> "Undone"
    XpReason.RECORD -> "Personal best"
    XpReason.SKILL -> "Skill practice"
    XpReason.MIND -> "Mind training"
    XpReason.GOAL -> "Goal task"
    else -> reason.replaceFirstChar { it.uppercase() }
}

@Composable
private fun XpEventRow(event: XpEventEntity) {
    val negative = event.delta < 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(reasonLabel(event.reason), style = MaterialTheme.typography.bodyMedium, color = if (negative) SystemColors.Danger else SystemColors.Text)
            MutedText(LocalDate.ofEpochDay(event.epochDay).format(dayFormat))
        }
        Text(
            if (negative) "${event.delta} XP" else "+${event.delta} XP",
            style = MaterialTheme.typography.titleMedium,
            color = if (negative) SystemColors.Danger else event.statColor(),
        )
    }
}

private fun XpEventEntity.statColor(): Color = Stat.fromKey(stat).color
