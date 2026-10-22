@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.selfup.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.selfup.core.GoalPlanner
import app.selfup.core.GoalRules
import app.selfup.core.GoalStatus
import app.selfup.data.GoalView
import app.selfup.data.GoalsOverview
import app.selfup.data.TodayTask
import app.selfup.ui.components.BackScaffold
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.PrimaryButton
import app.selfup.ui.components.SheetTitle
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemCheck
import app.selfup.ui.components.SystemChip
import app.selfup.ui.components.SystemChoice
import app.selfup.ui.components.SystemDial
import app.selfup.ui.components.SystemGroup
import app.selfup.ui.components.SystemSheet
import app.selfup.ui.components.SystemTextButton
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.Tag
import app.selfup.ui.components.pressScale
import app.selfup.ui.records.Stepper
import app.selfup.ui.system.Glow
import app.selfup.ui.system.systemPanel
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val dateFormat = DateTimeFormatter.ofPattern("d MMM")
private val Behind = SystemColors.Gold

private fun statusColor(status: GoalStatus, accent: Color): Color = when (status) {
    GoalStatus.AHEAD, GoalStatus.REACHED -> SystemColors.Success
    GoalStatus.ON_TRACK -> accent
    GoalStatus.BEHIND, GoalStatus.MISSED -> Behind
    GoalStatus.NEEDS_BASELINE -> SystemColors.TextMuted
}

/** Today's plan on the Today tab: every active goal's tasks, ticked off by what you log. */
fun LazyListScope.planSection(overview: GoalsOverview, onTask: (TodayTask) -> Unit, onGoals: () -> Unit) {
    item(key = "plan") {
        val tasks = overview.todayTasks
        SystemWindow(title = "Goal quests", contentPadding = 0.dp, spacing = 4.dp) {
            Spacer(Modifier.height(12.dp))
            when {
                overview.active.isEmpty() -> MutedText(
                    "Set a goal, like bench 80 kg by January, and the System plans each day's tasks.",
                    Modifier.padding(horizontal = 18.dp),
                )
                tasks.isEmpty() -> MutedText("Rest day for your goals.", Modifier.padding(horizontal = 18.dp))
                else -> Column {
                    tasks.forEachIndexed { i, task ->
                        if (i > 0) GroupSeparator()
                        val goal = overview.active.first { it.goal.id == task.task.goalId }
                        TaskRow(task, goal.goal.measure.stat.color, onClick = { onTask(task) })
                    }
                }
            }
            SystemTextButton(
                if (overview.active.isEmpty()) "Set a goal" else "Goals · ${overview.active.size} of ${GoalRules.MAX_ACTIVE}",
                onClick = onGoals,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 6.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun TaskRow(task: TodayTask, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressScale(interaction, 0.985f)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SystemCheck(task.done, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    task.task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (task.task.isTest) Tag("Test", accent)
            }
            Text(task.task.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Every goal: active ones with where they stand and what to adjust, then the queue. */
@Composable
fun GoalsScreen(
    overview: GoalsOverview,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onAiPlan: () -> Unit,
    onMoveDeadline: (GoalView, LocalDate) -> Unit,
    onChangeTarget: (GoalView, Double) -> Unit,
    onReached: (GoalView) -> Unit,
    onDrop: (GoalView) -> Unit,
    onBack: () -> Unit,
) {
    BackScaffold("Goals", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (overview.active.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "A goal is a number by a date. The plan starts with a test of where you are, then spreads the gap over the weeks, with a retest every 4th week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
            }
            overview.active.forEach { view ->
                item(key = "goal-${view.goal.id}") {
                    GoalCard(view, overview.today, onMoveDeadline, onChangeTarget, onReached, onDrop)
                }
            }
            item(key = "add") {
                val full = !GoalRules.canAdd(overview.active.size)
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SystemButton(if (full) "Add to the queue" else "Add a goal", onAdd)
                    SystemButton("Plan with AI", onAiPlan, filled = false)
                    SystemTextButton("Import a plan from Claude", onImport, Modifier.align(Alignment.CenterHorizontally))
                    if (full) {
                        Text(
                            "Three goals is the limit, so each one gets real attention. New goals wait and start when one ends.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
            if (overview.queued.isNotEmpty()) {
                item(key = "queue-h") { GroupHeader("Up next") }
                item(key = "queue") {
                    SystemGroup {
                        overview.queued.forEachIndexed { i, view ->
                            if (i > 0) GroupSeparator(start = 16.dp)
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(view.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                SystemTextButton("Remove", onClick = { onDrop(view) })
                            }
                        }
                    }
                }
            }
            if (overview.finished.isNotEmpty()) {
                item(key = "done-h") { GroupHeader("Reached") }
                item(key = "done") {
                    SystemGroup {
                        overview.finished.forEachIndexed { i, view ->
                            if (i > 0) GroupSeparator(start = 16.dp)
                            Text(view.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    view: GoalView,
    today: LocalDate,
    onMoveDeadline: (GoalView, LocalDate) -> Unit,
    onChangeTarget: (GoalView, Double) -> Unit,
    onReached: (GoalView) -> Unit,
    onDrop: (GoalView) -> Unit,
) {
    val measure = view.goal.measure
    val accent = measure.stat.color
    val tone = statusColor(view.status, accent)
    val weeksLeft = (ChronoUnit.DAYS.between(today, view.goal.deadline) + 6) / 7
    var confirmDrop by remember(view.goal.id) { mutableStateOf(false) }
    // The confirm state lapses, so a stray tap minutes later can't drop the goal.
    LaunchedEffect(confirmDrop) {
        if (confirmDrop) {
            delay(4_000)
            confirmDrop = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .systemPanel(accent = accent, glow = Glow.Soft)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                SystemDial(view.progress.toFloat(), accent, size = 64.dp, stroke = 7.dp)
                Text("${(view.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(view.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(view.status.displayName, tone)
                    if (weeksLeft > 0) {
                        Text(
                            "$weeksLeft week${if (weeksLeft == 1L) "" else "s"} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val current = view.current
        val expected = view.expected
        val baseline = view.goal.baseline
        if (current != null && baseline != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure("NOW", GoalPlanner.format(measure, GoalRules.round(measure, current)), Modifier.weight(1f))
                Figure("ON THE LINE", expected?.let { GoalPlanner.format(measure, GoalRules.round(measure, it)) } ?: "–", Modifier.weight(1f))
                Figure("START", GoalPlanner.format(measure, GoalRules.round(measure, baseline)), Modifier.weight(1f))
            }
        } else {
            Text("The first task is a test of where you are now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // The adjust step: plainly ahead or behind, with the choice left to you.
        when (view.status) {
            GoalStatus.BEHIND, GoalStatus.MISSED -> {
                val projected = view.projectedDeadline
                Text(
                    if (projected != null) "At this pace you'd get there around ${projected.format(dateFormat)}."
                    else "No progress since the start yet. Move the deadline, or lower the target.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val newDeadline = projected ?: view.goal.deadline.plusWeeks(4)
                    Action("Move deadline to ${newDeadline.format(dateFormat)}", tone) { onMoveDeadline(view, newDeadline) }
                    if (current != null) {
                        val lower = GoalRules.round(measure, (current + view.goal.target) / 2)
                        if (lower != view.goal.target && lower != current) {
                            Action("Lower target to ${GoalPlanner.format(measure, lower)}", tone) { onChangeTarget(view, lower) }
                        }
                    }
                }
            }
            GoalStatus.AHEAD -> view.raisedTarget?.let { raised ->
                Text("You're ahead of the line. Raise the bar?", style = MaterialTheme.typography.bodyMedium)
                Action("Raise target to ${GoalPlanner.format(measure, raised)}", tone) { onChangeTarget(view, raised) }
            }
            GoalStatus.REACHED -> {
                Text("Target hit. Mark it reached to free the slot for the next goal.", style = MaterialTheme.typography.bodyMedium)
                Action("Mark reached", tone) { onReached(view) }
            }
            else -> Unit
        }
        SystemTextButton(
            if (confirmDrop) "Tap again to drop" else "Drop goal",
            onClick = { if (confirmDrop) onDrop(view) else confirmDrop = true },
            color = if (confirmDrop) SystemColors.Danger else SystemColors.TextMuted,
            modifier = Modifier.offset(x = (-12).dp),
        )
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Action(text: String, color: Color, onClick: () -> Unit) = SystemChip(text, onClick, accent = color)

/** Pick what to improve, the number, and how many weeks. Your best so far sets the start. */
@Composable
fun AddGoalSheet(
    best: suspend (GoalTemplate) -> Double?,
    queued: Boolean,
    today: LocalDate,
    onAdd: (GoalTemplate, Double, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf(GoalTemplate.groups.first()) }
    var template by remember { mutableStateOf(GoalTemplate.all.first()) }
    var current by remember { mutableStateOf<Double?>(null) }
    var target by remember { mutableDoubleStateOf(template.suggest(null)) }
    var weeks by remember { mutableIntStateOf(12) }

    LaunchedEffect(template) {
        current = best(template)
        target = template.suggest(current)
    }

    SystemSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetTitle("New goal")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalTemplate.groups.forEach { g ->
                    Choice(g, selected = g == group) {
                        group = g
                        template = GoalTemplate.all.first { it.group == g }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalTemplate.all.filter { it.group == group }.forEach { t ->
                    Choice(t.name, selected = t == template) { template = t }
                }
            }
            val measure = template.measure
            val accent = measure.stat.color
            Text(
                current?.let { "Your best: ${GoalPlanner.format(measure, GoalRules.round(measure, it))}" } ?: "No score yet. The plan starts with a test.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper(
                "Target",
                GoalPlanner.format(measure, target),
                accent,
                onMinus = { target = (Math.round((target - template.step) / template.step) * template.step).coerceIn(template.min, template.max) },
                onPlus = { target = (Math.round((target + template.step) / template.step) * template.step).coerceIn(template.min, template.max) },
            )
            Stepper(
                "Deadline",
                "$weeks weeks · ${today.plusWeeks(weeks.toLong()).format(dateFormat)}",
                accent,
                onMinus = { weeks = (weeks - 1).coerceAtLeast(MIN_WEEKS) },
                onPlus = { weeks = (weeks + 1).coerceAtMost(MAX_WEEKS) },
            )
            PrimaryButton(if (queued) "Add to the queue" else "Start goal", accent, {
                val deadline = today.plusWeeks(weeks.toLong())
                scope.launch { sheetState.hide() }.invokeOnCompletion { onAdd(template, target, deadline) }
            })
        }
    }
}

private const val MIN_WEEKS = 4
private const val MAX_WEEKS = 52

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) = SystemChoice(text, selected, onClick)
