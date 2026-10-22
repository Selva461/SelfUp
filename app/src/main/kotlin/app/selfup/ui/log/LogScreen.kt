@file:OptIn(ExperimentalLayoutApi::class)

package app.selfup.ui.log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.selfup.core.DayBoundary
import app.selfup.core.HealthDay
import app.selfup.core.HealthLog
import app.selfup.core.Stat
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.LargeTitle
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemChip
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemDialog
import app.selfup.ui.components.SystemGroup
import app.selfup.ui.components.SystemTextButton
import app.selfup.ui.system.systemPanel
import app.selfup.ui.theme.color
import app.selfup.ui.theme.colorLight
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Bedtime used to flag late meals (the spec's "last meal 2+ hours before bed"). */
private val Bedtime: LocalTime = LocalTime.MIDNIGHT

@Composable
fun LogScreen(
    state: LogState,
    boundary: DayBoundary,
    contentPadding: PaddingValues,
    onMeal: (String) -> Unit,
    onShare: () -> Unit,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    var typingFood by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val logMeal: (String) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onMeal(it)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { LargeTitle("Log", eyebrow = "Food and spending") }

        item(key = "meal-header") { GroupHeader("Ate just now") }
        item(key = "meals") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.quickFoods.forEach { food -> SystemChip(food, onClick = { logMeal(food) }) }
                SystemChip("Other…", onClick = { typingFood = true }, accent = MaterialTheme.colorScheme.primary)
            }
        }

        item(key = "spend-header") { GroupHeader("Spending · last 14 days") }
        item(key = "spend") { SpendChart(state.spend) }

        item(key = "days-header") {
            GroupHeader("Last 7 days") {
                SystemTextButton("Share", onClick = onShare)
            }
        }
        item(key = "days") {
            SystemGroup {
                state.days.forEachIndexed { i, day ->
                    if (i > 0) GroupSeparator(start = 16.dp)
                    DayRow(day, boundary)
                }
            }
        }

        item(key = "backup-header") { GroupHeader("Backup") }
        item(key = "backup") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("Everything in one file: quests, XP, streak, records, skills and logs. Keep a copy somewhere safe.", Modifier.padding(start = 6.dp))
                SystemButton("Save backup", onClick = onBackup)
                SystemButton("Restore from backup", onClick = { confirmRestore = true }, filled = false)
            }
        }
    }

    if (confirmRestore) {
        SystemDialog(
            title = "Restore",
            onDismiss = { confirmRestore = false },
            confirmText = "Choose file",
            onConfirm = {
                confirmRestore = false
                onRestore()
            },
        ) {
            Text("Everything in the app is replaced by the backup, and SelfUp restarts.", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (typingFood) {
        var text by remember { mutableStateOf("") }
        SystemDialog(
            title = "Meal",
            onDismiss = { typingFood = false },
            confirmText = "Log",
            onConfirm = {
                logMeal(text.trim())
                typingFood = false
            },
            confirmEnabled = text.isNotBlank(),
        ) {
            Text("What did you eat?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val chartDayFormat = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun DayRow(day: HealthDay, boundary: DayBoundary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(day.day.format(dayFormat), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            day.lastMealAt?.let { Muted("Last meal ${it.atZone(boundary.zone).toLocalTime().format(timeFormat)}") }
        }
        if (day.meals.isEmpty()) {
            Muted("No meals logged")
        } else {
            Text(
                day.meals.joinToString("   ") { meal ->
                    val time = meal.at.atZone(boundary.zone).toLocalTime().format(timeFormat)
                    val late = if (HealthLog.isLateMeal(meal.at, Bedtime, boundary)) " (late)" else ""
                    "$time ${meal.food}$late"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpendChart(spend: List<Pair<LocalDate, Double>>) {
    val max = spend.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val total = spend.sumOf { it.second }
    val logged = spend.count { it.second > 0 }
    val bar = Brush.verticalGradient(listOf(Stat.MONEY.colorLight, Stat.MONEY.color))
    val track = Stat.MONEY.color.copy(alpha = 0.08f)
    Column(
        Modifier
            .fillMaxWidth()
            .systemPanel()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (logged == 0) "No spending yet" else "₹${(total / logged).roundToInt()} a day",
            style = MaterialTheme.typography.titleLarge,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            if (spend.isEmpty()) return@Canvas
            val gap = 6.dp.toPx()
            val width = (size.width - gap * (spend.size - 1)) / spend.size
            spend.forEachIndexed { i, (_, value) ->
                val x = i * (width + gap)
                drawRect(track, Offset(x, 0f), Size(width, size.height))
                val h = (value / max * size.height).toFloat()
                if (h > 0f) {
                    drawRect(bar, Offset(x, size.height - h), Size(width, h))
                }
            }
        }
        if (spend.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Muted(spend.first().first.format(chartDayFormat), Modifier.weight(1f))
                Muted("Today")
            }
        }
        Muted(
            if (logged == 0) {
                "Log spending with the daily Money quest to see it here."
            } else {
                "₹${total.roundToInt()} over $logged logged days"
            },
        )
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) = MutedText(text, modifier)
