@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.selfup.ui.dsa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.selfup.core.Dsa
import app.selfup.core.DsaDifficulty
import app.selfup.core.DsaOutcome
import app.selfup.core.DsaTopic
import app.selfup.core.Stat
import app.selfup.data.DsaProblemView
import app.selfup.ui.components.FormSection
import app.selfup.ui.components.SheetTitle
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemChoice
import app.selfup.ui.components.SystemSegmented
import app.selfup.ui.components.SystemSheet
import app.selfup.ui.components.SystemTextButton
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.color

private val Accent = Stat.TECHNICAL.color

/**
 * Logs a DSA attempt: a new problem with its first try when [problem] is null, otherwise a
 * from-scratch re-solve of [problem]. Outcome and time decide the XP, so both are asked.
 */
@Composable
fun DsaLogSheet(
    problem: DsaProblemView?,
    suggestedTopic: DsaTopic?,
    onAdd: (title: String, topic: DsaTopic, difficulty: DsaDifficulty, url: String?, outcome: DsaOutcome, minutes: Int?) -> Unit,
    onAttempt: (problemId: Long, outcome: DsaOutcome, minutes: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf(suggestedTopic ?: DsaTopic.ARRAYS) }
    var difficulty by remember { mutableStateOf(DsaDifficulty.MEDIUM) }
    var outcome by remember { mutableStateOf(DsaOutcome.ALONE) }
    var minutes by remember { mutableStateOf("") }

    val shownDifficulty = problem?.difficulty ?: difficulty
    val minutesValue = minutes.toIntOrNull()?.takeIf { it in 1..600 }
    val canSave = (problem != null || title.isNotBlank()) && (minutes.isEmpty() || minutesValue != null)

    SystemSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetTitle(if (problem == null) "New problem" else "Re-solve", Accent)
            if (problem == null) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Problem") },
                    placeholder = { Text("Two Sum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(300) },
                    label = { Text("Link (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormSection("Topic") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DsaTopic.entries.forEach { t ->
                            SystemChoice(t.displayName, t == topic, onClick = { topic = t }, accent = Accent)
                        }
                    }
                }
                FormSection("Difficulty") {
                    SystemSegmented(DsaDifficulty.entries, difficulty, label = { it.displayName }, onSelect = { difficulty = it }, accent = Accent)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(problem.problem.title, style = MaterialTheme.typography.titleLarge)
                    Text("${problem.topic.displayName} · ${problem.difficulty.displayName}", style = MaterialTheme.typography.labelMedium, color = SystemColors.TextMuted)
                    lastAttemptLine(problem)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted) }
                    Text("Close the old code and write it from a blank file.", style = MaterialTheme.typography.bodyMedium, color = SystemColors.TextMuted)
                }
            }
            FormSection("How it went") {
                SystemSegmented(
                    DsaOutcome.entries,
                    outcome,
                    label = { listOf("Alone", "With a hint", "Solution")[it.ordinal] },
                    onSelect = { outcome = it },
                    accent = Accent,
                )
            }
            FormSection("Minutes (target ${shownDifficulty.targetMinutes})") {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    placeholder = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val first = problem == null || !problem.state.solvedAlone
            val xp = Dsa.xp(shownDifficulty, Dsa.performance(shownDifficulty, outcome, minutesValue), firstSolve = first)
            Text(
                (if (xp >= 0) "+$xp" else "−${-xp}") + " Technical XP" + if (!first) " (re-solves pay half)" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (xp >= 0) Accent else SystemColors.Danger,
            )
            SystemButton(
                if (problem == null) "Log problem" else "Log re-solve",
                onClick = {
                    if (problem == null) onAdd(title.trim(), topic, difficulty, url, outcome, minutesValue) else onAttempt(problem.problem.id, outcome, minutesValue)
                },
                enabled = canSave,
                accent = Accent,
            )
            SystemTextButton("Cancel", onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
