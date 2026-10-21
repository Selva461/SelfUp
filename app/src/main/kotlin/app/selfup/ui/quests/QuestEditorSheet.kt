@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.selfup.ui.quests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.core.CompletionInput
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.ui.theme.color
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private enum class Repeat(val label: String) { EVERY_DAY("Every day"), DAYS("Some days"), ONCE("Once") }

private enum class InputKind(val label: String) { TAP("Tap"), NUMBER("Number") }

/** Create (when [initial] is null) or edit a quest. */
@Composable
fun QuestEditorSheet(
    initial: Quest?,
    onSave: (Quest) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialSchedule = initial?.schedule
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var difficulty by remember { mutableStateOf(initial?.difficulty ?: Difficulty.EASY) }
    var stats by remember { mutableStateOf(initial?.stats ?: emptySet()) }
    var repeat by remember {
        mutableStateOf(
            when {
                initialSchedule is Schedule.OneTime -> Repeat.ONCE
                initialSchedule is Schedule.Recurring && initialSchedule.days.size < 7 -> Repeat.DAYS
                else -> Repeat.EVERY_DAY
            },
        )
    }
    var days by remember {
        mutableStateOf((initialSchedule as? Schedule.Recurring)?.days ?: DayOfWeek.entries.toSet())
    }
    var inputKind by remember {
        mutableStateOf(if (initial?.input is CompletionInput.Number) InputKind.NUMBER else InputKind.TAP)
    }
    var unit by remember { mutableStateOf((initial?.input as? CompletionInput.Number)?.unit ?: "₹") }

    val canSave = title.isNotBlank() && stats.isNotEmpty() && (repeat != Repeat.DAYS || days.isNotEmpty())

    fun build(): Quest {
        val schedule = when (repeat) {
            Repeat.EVERY_DAY -> Schedule.Recurring()
            Repeat.DAYS -> Schedule.Recurring(days)
            // Keep an existing expiry (e.g. on a penalty quest) when editing.
            Repeat.ONCE -> initialSchedule as? Schedule.OneTime ?: Schedule.OneTime()
        }
        val input = when (inputKind) {
            InputKind.TAP -> if (initial?.input == CompletionInput.YesNo) CompletionInput.YesNo else CompletionInput.Tap
            InputKind.NUMBER -> CompletionInput.Number(unit.trim())
        }
        val base = initial ?: Quest(title = title, difficulty = difficulty, stats = stats, schedule = schedule)
        return base.copy(title = title.trim(), difficulty = difficulty, stats = stats, schedule = schedule, input = input)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(if (initial == null) "New quest" else "Edit quest", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Difficulty · +${difficulty.value * 10} XP") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val labels = listOf("Easy", "Medium", "Hard")
                    Difficulty.entries.forEachIndexed { i, d ->
                        SegmentedButton(
                            selected = difficulty == d,
                            onClick = { difficulty = d },
                            shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                        ) { Text(labels[i]) }
                    }
                }
            }

            Section("Stats") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat.launchSet.forEach { stat ->
                        FilterChip(
                            selected = stat in stats,
                            onClick = { stats = if (stat in stats) stats - stat else stats + stat },
                            label = { Text(stat.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = stat.color.copy(alpha = 0.25f),
                                selectedLabelColor = stat.color,
                            ),
                        )
                    }
                }
            }

            Section("Repeats") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Repeat.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = repeat == r,
                            onClick = { repeat = r },
                            shape = SegmentedButtonDefaults.itemShape(i, Repeat.entries.size),
                        ) { Text(r.label) }
                    }
                }
                if (repeat == Repeat.DAYS) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                }
            }

            Section("Completed by") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    InputKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = inputKind == k,
                            onClick = { inputKind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, InputKind.entries.size),
                        ) { Text(k.label) }
                    }
                }
                if (inputKind == InputKind.NUMBER) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(6) },
                        label = { Text("Unit") },
                        singleLine = true,
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(build()) }, enabled = canSave) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
