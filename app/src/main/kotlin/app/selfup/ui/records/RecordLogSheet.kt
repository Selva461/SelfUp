@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.selfup.core.RecordEntry
import app.selfup.core.RecordInput
import app.selfup.core.RecordKind
import app.selfup.core.RecordMath
import app.selfup.data.RecordSummary
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.glass.hairline
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.Surfaces
import app.selfup.ui.theme.color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val entryDateFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Logs one entry in a few seconds: every field starts at the last value, steppers nudge
 * it (hold to repeat), and the result shows live, including whether it beats the best.
 */
@Composable
fun RecordLogSheet(
    kind: RecordKind,
    summary: RecordSummary?,
    onSave: (weightKg: Double?, reps: Int?, seconds: Int?, distanceKm: Double?) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val last = summary?.latest
    var weight by remember(kind) { mutableDoubleStateOf(last?.weightKg ?: defaultWeight(kind)) }
    var reps by remember(kind) { mutableIntStateOf(last?.reps ?: if (kind.input == RecordInput.REPS) 10 else 5) }
    var seconds by remember(kind) { mutableIntStateOf(last?.seconds ?: if (kind == RecordKind.RUN) 30 * 60 else 60) }
    var distance by remember(kind) { mutableDoubleStateOf(last?.distanceKm ?: 5.0) }
    val accent = kind.stat.color

    val draft = runCatching {
        when (kind.input) {
            RecordInput.WEIGHT_REPS -> RecordEntry(kind, Instant.EPOCH, weightKg = weight, reps = reps)
            RecordInput.REPS -> RecordEntry(kind, Instant.EPOCH, reps = reps)
            RecordInput.SECONDS -> RecordEntry(kind, Instant.EPOCH, seconds = seconds)
            RecordInput.DISTANCE_TIME -> RecordEntry(kind, Instant.EPOCH, distanceKm = distance, seconds = seconds)
            RecordInput.WEIGHT -> RecordEntry(kind, Instant.EPOCH, weightKg = weight)
        }
    }.getOrNull()
    val history = summary?.entries?.map { it.entry }.orEmpty()
    val beats = draft?.let { RecordMath.personalBests(history, it) }.orEmpty()

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(kind.displayName, style = MaterialTheme.typography.headlineSmall)

            Preview(kind, draft, beats.isNotEmpty(), summary)

            when (kind.input) {
                RecordInput.WEIGHT_REPS -> {
                    Stepper("Weight", "${RecordFormat.number(weight)} kg", accent,
                        onMinus = { weight = (weight - 2.5).coerceAtLeast(2.5) }, onPlus = { weight += 2.5 })
                    Stepper("Reps", "$reps", accent,
                        onMinus = { reps = (reps - 1).coerceAtLeast(1) }, onPlus = { reps = (reps + 1).coerceAtMost(RecordMath.MAX_REPS) })
                }
                RecordInput.REPS -> Stepper("Reps", "$reps", accent,
                    onMinus = { reps = (reps - 1).coerceAtLeast(1) }, onPlus = { reps += 1 })
                RecordInput.SECONDS -> Stepper("Time held", RecordFormat.duration(seconds), accent,
                    onMinus = { seconds = (seconds - 5).coerceAtLeast(5) }, onPlus = { seconds += 5 })
                RecordInput.DISTANCE_TIME -> {
                    Stepper("Distance", "${RecordFormat.number(distance)} km", accent,
                        onMinus = { distance = tenths(distance - 0.1).coerceAtLeast(0.1) }, onPlus = { distance = tenths(distance + 0.1) })
                    Stepper("Time · minutes", RecordFormat.duration(seconds), accent,
                        onMinus = { seconds = (seconds - 60).coerceAtLeast(60) }, onPlus = { seconds += 60 })
                    Stepper("Time · seconds", RecordFormat.duration(seconds), accent,
                        onMinus = { seconds = (seconds - 5).coerceAtLeast(5) }, onPlus = { seconds += 5 })
                }
                RecordInput.WEIGHT -> Stepper("Weight", "${RecordFormat.number(weight)} kg", accent,
                    onMinus = { weight = tenths(weight - 0.1).coerceAtLeast(20.0) }, onPlus = { weight = tenths(weight + 0.1) })
            }

            SaveButton(enabled = draft != null, accent = accent) {
                close {
                    when (kind.input) {
                        RecordInput.WEIGHT_REPS -> onSave(weight, reps, null, null)
                        RecordInput.REPS -> onSave(null, reps, null, null)
                        RecordInput.SECONDS -> onSave(null, null, seconds, null)
                        RecordInput.DISTANCE_TIME -> onSave(null, null, seconds, distance)
                        RecordInput.WEIGHT -> onSave(weight, null, null, null)
                    }
                }
            }

            val recent = summary?.entries?.takeLast(5)?.reversed().orEmpty()
            if (recent.isNotEmpty()) {
                Column {
                    GroupHeader("Recent")
                    GlassGroup(Modifier.padding(top = 6.dp)) {
                        recent.forEachIndexed { i, logged ->
                            if (i > 0) GroupSeparator(start = 16.dp)
                            Row(
                                Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(RecordFormat.entry(logged.entry), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    logged.entry.at.atZone(ZoneId.systemDefault()).format(entryDateFormat),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { onDelete(logged.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rounds to 0.1 so repeated steps don't drift (5.000000001 km). */
private fun tenths(value: Double): Double = Math.round(value * 10) / 10.0

private fun defaultWeight(kind: RecordKind): Double = when (kind) {
    RecordKind.DEADLIFT, RecordKind.SQUAT -> 60.0
    RecordKind.OVERHEAD_PRESS -> 30.0
    RecordKind.BODY_WEIGHT -> 75.0
    else -> 40.0
}

/** The number this entry produces, and whether it's a new best. */
@Composable
private fun Preview(kind: RecordKind, draft: RecordEntry?, isBest: Boolean, summary: RecordSummary?) {
    val headline = kind.metrics.first()
    val value = draft?.let { RecordMath.metrics(it)[headline] }
    val best = summary?.bests?.get(headline)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(headline.displayName.uppercase(), style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value?.let { RecordFormat.metric(headline, it) } ?: "–", style = MaterialTheme.typography.displayMedium, color = kind.stat.color)
            val context = when {
                best != null -> "Best ${RecordFormat.metric(headline, best)}"
                summary?.entries.isNullOrEmpty() -> "Your first entry sets the baseline"
                else -> null
            }
            context?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        AnimatedVisibility(isBest, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Text(
                "NEW BEST",
                style = SectionLabel,
                color = Color.Black,
                modifier = Modifier
                    .background(kind.stat.color, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** A value with − and + buttons. Holding a button repeats, faster the longer it's held. */
@Composable
internal fun Stepper(label: String, value: String, accent: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surfaces.Fill, RoundedCornerShape(18.dp))
            .hairline(RoundedCornerShape(18.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "Decrease $label", accent, onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        StepButton("+", "Increase $label", accent, onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, accent: Color, onStep: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(52.dp)
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        step()
                        val repeat = scope.launch {
                            delay(400)
                            var interval = 120L
                            while (true) {
                                step()
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                delay(interval)
                                interval = (interval - 10).coerceAtLeast(40)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = accent)
    }
}

@Composable
private fun SaveButton(enabled: Boolean, accent: Color, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (enabled) accent else accent.copy(alpha = 0.3f), RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("Save", style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}
