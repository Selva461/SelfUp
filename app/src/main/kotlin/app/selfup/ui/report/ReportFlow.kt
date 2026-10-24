package app.selfup.ui.report

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.selfup.alert.AlertFrame
import app.selfup.core.CompletionInput
import app.selfup.core.Drill
import app.selfup.core.Quest
import app.selfup.core.ReportItem
import app.selfup.core.XpRules
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemChip
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.theme.SystemColors
import java.time.Duration
import java.time.Instant

/** A full-screen System window inside the app that back and outside taps can't close. */
@Composable
fun SystemTakeover(content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) { content() }
}

/** What closing yesterday found, read out before anything else. */
@Composable
fun VerdictTakeover(text: String, onDismiss: () -> Unit) {
    SystemTakeover {
        AlertFrame("Verdict") {
            SystemLine("[Failure on record]", color = SystemColors.Danger, style = MaterialTheme.typography.titleLarge)
            SystemLine(text, style = MaterialTheme.typography.bodyMedium)
            SystemButton("Understood", onClick = onDismiss, accent = SystemColors.Danger)
        }
    }
}

/**
 * The guided report: one question at a time, each answer moving straight to the next, so
 * nothing has to be hunted for. [onComplete] completes a quest the way a tap would.
 */
@Composable
fun ReportTakeover(state: ReportState, vm: ReportViewModel, lastValue: suspend (Long) -> Double?, onComplete: (Quest, Double?) -> Unit) {
    SystemTakeover {
        val open = state.openAfter.orEmpty()
        val accent = when {
            !state.finished -> SystemColors.Blue
            open.isEmpty() -> SystemColors.Success
            else -> SystemColors.Danger
        }
        AlertFrame("Daily report", accent = accent) {
            AnimatedContent(
                targetState = if (state.finished) -1 else state.index,
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                label = "report",
            ) { index ->
                val item = state.items.getOrNull(index)
                val progress = "Question ${index + 1} of ${state.items.size}"
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        index < 0 -> Finished(state, vm::close)
                        item is ReportItem.Meal -> MealStep(state, progress, vm)
                        item is ReportItem.Amount -> AmountStep(item.quest, progress, lastValue, onLog = { value ->
                            onComplete(item.quest, value)
                            vm.answered(item.quest)
                        }, onNotYet = vm::notYet)
                        item is ReportItem.Confirm -> ConfirmStep(item.quest, progress, onDone = {
                            onComplete(item.quest, null)
                            vm.answered(item.quest)
                        }, onNotYet = vm::notYet)
                        // Between the last answer and the verdict loading.
                        else -> MutedText("Counting…")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealStep(state: ReportState, progress: String, vm: ReportViewModel) {
    var other by remember { mutableStateOf("") }
    MutedText(progress)
    if (state.mealsNow.isEmpty()) {
        SystemLine("What did you eat?", style = MaterialTheme.typography.titleLarge)
        MutedText(state.lastMeal?.let { "Last meal logged ${ago(it)}." } ?: "No meal logged today.")
    } else {
        SystemLine("Logged: ${state.mealsNow.joinToString(", ")}.", color = SystemColors.Success)
        SystemLine("Anything else?", style = MaterialTheme.typography.titleLarge)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.quickFoods.forEach { food -> SystemChip(food, onClick = { vm.logMeal(food) }) }
    }
    OutlinedTextField(
        value = other,
        onValueChange = { other = it },
        singleLine = true,
        label = { Text("Something else") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            vm.logMeal(other)
            other = ""
        }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (other.isNotBlank()) {
            SystemButton("Log it", onClick = {
                vm.logMeal(other)
                other = ""
            }, modifier = Modifier.weight(1f))
        } else {
            SystemButton(
                if (state.mealsNow.isEmpty()) "Nothing since" else "That's all",
                onClick = vm::mealsDone,
                modifier = Modifier.weight(1f),
                filled = state.mealsNow.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun AmountStep(quest: Quest, progress: String, lastValue: suspend (Long) -> Double?, onLog: (Double) -> Unit, onNotYet: () -> Unit) {
    val unit = (quest.input as? CompletionInput.Number)?.unit.orEmpty()
    var text by remember(quest.id) { mutableStateOf("") }
    var hint by remember(quest.id) { mutableStateOf<Double?>(null) }
    LaunchedEffect(quest.id) { hint = lastValue(quest.id) }
    val value = text.trim().toDoubleOrNull()?.takeIf { it >= 0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(quest.id) { runCatching { focus.requestFocus() } }
    MutedText(progress)
    SystemLine(quest.title, style = MaterialTheme.typography.titleLarge)
    MutedText(hint?.let { "Last time: $unit${format(it)}. The real number, not a nice one." } ?: "The real number, not a nice one.")
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        prefix = { if (unit.isNotEmpty()) Text(unit) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { value?.let(onLog) }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SystemButton("Not yet", onClick = onNotYet, modifier = Modifier.weight(1f), filled = false, accent = SystemColors.TextMuted)
        SystemButton("Log", onClick = { value?.let(onLog) }, modifier = Modifier.weight(1f), enabled = value != null)
    }
}

@Composable
private fun ConfirmStep(quest: Quest, progress: String, onDone: () -> Unit, onNotYet: () -> Unit) {
    MutedText(progress)
    SystemLine(quest.title, style = MaterialTheme.typography.titleLarge)
    MutedText("${questSubtitle(quest)} · +${XpRules.completionXp(quest.difficulty)} XP")
    SystemLine("Did you do it? Don't lie to the System.", color = SystemColors.Danger)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SystemButton("Not yet", onClick = onNotYet, modifier = Modifier.weight(1f), filled = false, accent = SystemColors.TextMuted)
        SystemButton("Done", onClick = onDone, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Finished(state: ReportState, onClose: () -> Unit) {
    val open = state.openAfter.orEmpty()
    val seed = remember { System.currentTimeMillis() / 60_000 }
    val clean = open.isEmpty()
    val tone = if (clean) SystemColors.Success else SystemColors.Danger
    SystemLine(Drill.reportDone(state.completed.size, open.size, seed), color = tone)
    open.take(6).forEach { SystemLine("· ${it.title}", style = MaterialTheme.typography.bodyMedium) }
    SystemButton(if (clean) "Understood" else "Get to work", onClick = onClose, accent = tone)
}

private fun ago(at: Instant): String {
    val minutes = Duration.between(at, Instant.now()).toMinutes()
    return when {
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}

private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
