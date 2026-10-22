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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.core.CompletionInput
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.ui.components.FormSection
import app.selfup.ui.components.SheetTitle
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemChoice
import app.selfup.ui.components.SystemDialog
import app.selfup.ui.components.SystemSegmented
import app.selfup.ui.components.SystemSheet
import app.selfup.ui.components.SystemTextButton
import app.selfup.ui.theme.color
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    var reminder by remember { mutableStateOf(initial?.reminder) }
    var pickingTime by remember { mutableStateOf(false) }

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
        return base.copy(
            title = title.trim(),
            difficulty = difficulty,
            stats = stats,
            schedule = schedule,
            input = input,
            reminder = reminder,
        )
    }

    SystemSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetTitle(if (initial == null) "New quest" else "Edit quest")

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FormSection("Difficulty · +${difficulty.value * 10} XP") {
                SystemSegmented(
                    options = Difficulty.entries,
                    selected = difficulty,
                    label = { listOf("Easy", "Medium", "Hard")[it.ordinal] },
                    onSelect = { difficulty = it },
                )
            }

            FormSection("Stats") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat.launchSet.forEach { stat ->
                        SystemChoice(
                            text = stat.displayName,
                            selected = stat in stats,
                            onClick = { stats = if (stat in stats) stats - stat else stats + stat },
                            accent = stat.color,
                        )
                    }
                }
            }

            FormSection("Repeats") {
                SystemSegmented(Repeat.entries, repeat, label = { it.label }, onSelect = { repeat = it })
                if (repeat == Repeat.DAYS) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            SystemChoice(
                                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day },
                            )
                        }
                    }
                }
            }

            FormSection("Completed by") {
                SystemSegmented(InputKind.entries, inputKind, label = { it.label }, onSelect = { inputKind = it })
                if (inputKind == InputKind.NUMBER) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(6) },
                        label = { Text("Unit") },
                        singleLine = true,
                    )
                }
            }

            FormSection("Reminder") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reminder != null,
                        onCheckedChange = { on -> reminder = if (on) reminder ?: LocalTime.of(21, 0) else null },
                    )
                    Spacer(Modifier.width(8.dp))
                    reminder?.let { time ->
                        SystemTextButton(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), onClick = { pickingTime = true })
                    } ?: Text("Off", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                }
            }

            SystemButton(if (initial == null) "Add quest" else "Save", onClick = { onSave(build()) }, enabled = canSave)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    SystemTextButton("Delete", onClick = { onDelete(initial.id) }, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                SystemTextButton("Cancel", onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            initial = reminder ?: LocalTime.of(21, 0),
            onConfirm = {
                reminder = it
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    SystemDialog(
        title = "Reminder",
        onDismiss = onDismiss,
        confirmText = "Set",
        onConfirm = { onConfirm(LocalTime.of(state.hour, state.minute)) },
    ) {
        TimePicker(state, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
