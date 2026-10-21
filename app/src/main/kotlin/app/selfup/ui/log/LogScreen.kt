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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.selfup.core.DayBoundary
import app.selfup.core.GasLevels
import app.selfup.core.HealthDay
import app.selfup.core.HealthLog
import app.selfup.core.Stat
import app.selfup.ui.theme.color
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Bedtime used to flag late meals (the spec's "last meal 2+ hours before bed"). */
private val Bedtime: LocalTime = LocalTime.MIDNIGHT

private val gasLabels = mapOf(1 to "None", 2 to "Mild", 3 to "Some", 4 to "Bad", 5 to "Severe")

@Composable
fun LogScreen(
    state: LogState,
    boundary: DayBoundary,
    contentPadding: PaddingValues,
    onMeal: (String) -> Unit,
    onGas: (Int) -> Unit,
    onShare: () -> Unit,
) {
    var typingFood by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionTitle("Ate just now") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.quickFoods.forEach { food -> AssistChip(onClick = { onMeal(food) }, label = { Text(food) }) }
                AssistChip(onClick = { typingFood = true }, label = { Text("Other…") })
            }
        }

        item { SectionTitle("Gas right now") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GasLevels.forEach { level ->
                    FilledTonalButton(
                        onClick = { onGas(level) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$level", style = MaterialTheme.typography.titleMedium)
                            Text(gasLabels.getValue(level), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Spending · last 14 days") }
        item { SpendChart(state.spend) }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Last 7 days", Modifier.weight(1f))
                TextButton(onClick = onShare) { Text("Share for doctor") }
            }
        }
        items(state.days, key = { it.day.toEpochDay() }) { day -> DayRow(day, boundary) }
        item {
            Muted("This is a record to discuss with a doctor, not a diagnosis.")
        }
    }

    if (typingFood) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { typingFood = false },
            title = { Text("What did you eat?") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMeal(text)
                        typingFood = false
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Log") }
            },
            dismissButton = { TextButton(onClick = { typingFood = false }) { Text("Cancel") } },
        )
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
private fun DayRow(day: HealthDay, boundary: DayBoundary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(day.day.format(dayFormat), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val gas = day.averageGas?.let { "Gas avg ${"%.1f".format(it)} · max ${day.maxGas}" } ?: "No gas logged"
            Muted(gas)
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
            )
        }
    }
}

@Composable
private fun SpendChart(spend: List<Pair<LocalDate, Double>>) {
    val max = spend.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val total = spend.sumOf { it.second }
    val logged = spend.count { it.second > 0 }
    val bar = Stat.MONEY.color
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                drawRoundRect(track, Offset(x, 0f), Size(width, size.height), CornerRadius(width / 3))
                val h = (value / max * size.height).toFloat()
                if (h > 0f) {
                    drawRoundRect(bar, Offset(x, size.height - h), Size(width, h), CornerRadius(width / 3))
                }
            }
        }
        Muted(
            if (logged == 0) {
                "Log spending with the daily Money quest to see it here."
            } else {
                "₹${total.roundToInt()} over $logged logged days · ₹${(total / logged).roundToInt()} a day"
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 6.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
