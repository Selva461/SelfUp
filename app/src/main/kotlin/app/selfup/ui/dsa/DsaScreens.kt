package app.selfup.ui.dsa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.selfup.core.Dsa
import app.selfup.core.DsaDifficulty
import app.selfup.core.DsaTopic
import app.selfup.core.Stat
import app.selfup.data.DsaOverview
import app.selfup.data.DsaProblemView
import app.selfup.ui.components.BackScaffold
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemBar
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemGroup
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.Tag
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Accent = Stat.TECHNICAL.color
private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")

private fun DsaDifficulty.color(): Color = when (this) {
    DsaDifficulty.EASY -> SystemColors.Success
    DsaDifficulty.MEDIUM -> SystemColors.Gold
    DsaDifficulty.HARD -> SystemColors.Danger
}

/** Where a problem stands, in a few words. */
private fun status(view: DsaProblemView, today: LocalDate): Pair<String, Color> {
    val state = view.state
    return when {
        state.mastered -> "Mastered" to SystemColors.Gold
        state.isDue(today) -> {
            val late = Dsa.daysOverdue(state, today)
            (if (late > 0) "Re-solve, ${late}d late" else "Re-solve today") to SystemColors.Danger
        }
        else -> (state.due?.let { "Next ${it.format(shortDay)}" } ?: "") to SystemColors.TextMuted
    }
}

/** Interview prep on the Skills tab: what's due to re-solve, and progress through the 150. */
fun LazyListScope.dsaSection(overview: DsaOverview?, onLog: () -> Unit, onProblem: (DsaProblemView) -> Unit, onOpen: () -> Unit) {
    if (overview == null) return
    item(key = "dsa-h") { GroupHeader("DSA") }
    item(key = "dsa-card") {
        val due = overview.due
        SystemWindow(title = "Interview prep", accent = Accent) {
            Text(
                when {
                    due.isNotEmpty() -> if (due.size == 1) "1 re-solve due" else "${due.size} re-solves due"
                    overview.problems.isEmpty() -> "Start the NeetCode 150"
                    else -> "Nothing due. Next: ${overview.nextTopic?.displayName ?: "all topics done"}"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Figure("Solved alone", "${overview.solvedAlone}/${DsaTopic.roadmapTotal}")
                Figure("Mastered", "${overview.mastered}")
            }
            SystemBar(overview.solvedAlone.toFloat() / DsaTopic.roadmapTotal, Accent)
            if (due.isNotEmpty()) {
                Column {
                    due.take(3).forEachIndexed { i, view ->
                        if (i > 0) GroupSeparator(start = 0.dp)
                        ProblemRow(view, overview.today, onClick = { onProblem(view) }, padding = 0.dp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemButton("Log a problem", onLog, Modifier.weight(1f), accent = Accent)
                SystemButton("Tracker", onOpen, Modifier.weight(1f), accent = Accent, filled = false)
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = SectionLabel, color = SystemColors.TextMuted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProblemRow(view: DsaProblemView, today: LocalDate, onClick: () -> Unit, padding: androidx.compose.ui.unit.Dp = 16.dp) {
    val (label, color) = status(view, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = padding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(view.problem.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${view.topic.displayName} · ${view.state.attempts} ${if (view.state.attempts == 1) "attempt" else "attempts"}",
                style = MaterialTheme.typography.labelSmall,
                color = SystemColors.TextMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tag(view.difficulty.displayName, view.difficulty.color())
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/** Every problem: re-solves due, the roadmap by topic, and the full list. */
@Composable
fun DsaScreen(overview: DsaOverview?, onLog: () -> Unit, onProblem: (DsaProblemView) -> Unit, onBack: () -> Unit) {
    BackScaffold("DSA tracker", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (overview == null) return@LazyColumn
            item(key = "log") { SystemButton("Log a problem", onLog, accent = Accent) }
            item(key = "how") {
                MutedText(
                    "Solve each problem from scratch, then again after 3, 7 and 14 days. Solving alone climbs a step, a hint repeats it, " +
                        "reading the solution starts over. Passing the 14-day re-solve masters it.",
                    Modifier.padding(horizontal = 4.dp),
                )
            }
            val due = overview.due
            if (due.isNotEmpty()) {
                item(key = "due-h") { GroupHeader("Due to re-solve") }
                item(key = "due") {
                    SystemGroup(accent = SystemColors.Danger) {
                        due.forEachIndexed { i, view ->
                            if (i > 0) GroupSeparator(start = 16.dp)
                            ProblemRow(view, overview.today, onClick = { onProblem(view) })
                        }
                    }
                }
            }
            item(key = "roadmap-h") { GroupHeader("Roadmap · NeetCode 150") }
            item(key = "roadmap") {
                SystemGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DsaTopic.entries.forEach { topic ->
                            val solved = overview.solvedIn(topic)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(
                                        topic.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (topic == overview.nextTopic) SystemColors.Text else SystemColors.TextMuted,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${solved.coerceAtMost(topic.roadmap)}/${topic.roadmap}", style = MaterialTheme.typography.labelMedium, color = SystemColors.TextMuted)
                                }
                                SystemBar(solved.toFloat() / topic.roadmap, if (solved >= topic.roadmap) SystemColors.Success else Accent, height = 4.dp)
                            }
                        }
                    }
                }
            }
            DsaTopic.entries.forEach { topic ->
                val problems = overview.problems.filter { it.topic == topic }
                if (problems.isNotEmpty()) {
                    item(key = "topic-${topic.key}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupHeader(topic.displayName)
                            SystemGroup {
                                problems.forEachIndexed { i, view ->
                                    if (i > 0) GroupSeparator(start = 16.dp)
                                    ProblemRow(view, overview.today, onClick = { onProblem(view) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Last: solved alone, 18 min on Tue 20 Oct". */
internal fun lastAttemptLine(view: DsaProblemView): String? {
    val last = view.attempts.lastOrNull() ?: return null
    val outcome = app.selfup.core.DsaOutcome.fromKey(last.outcome).displayName.lowercase()
    val minutes = last.minutes?.let { ", $it min" }.orEmpty()
    return "Last: $outcome$minutes on ${LocalDate.ofEpochDay(last.epochDay).format(shortDay)}"
}
