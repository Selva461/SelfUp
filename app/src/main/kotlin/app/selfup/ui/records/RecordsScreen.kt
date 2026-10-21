package app.selfup.ui.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.selfup.core.Metric
import app.selfup.core.RecordKind
import app.selfup.core.Stat
import app.selfup.data.RecordSummary
import app.selfup.gym.GymStatus
import app.selfup.ui.components.GlassChip
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.LargeTitle
import app.selfup.ui.components.pressScale
import app.selfup.ui.glass.frosted
import app.selfup.ui.theme.color
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val groups = listOf(
    "Lifts" to listOf(RecordKind.BENCH, RecordKind.SQUAT, RecordKind.DEADLIFT, RecordKind.OVERHEAD_PRESS),
    "Bodyweight" to listOf(RecordKind.PULL_UPS, RecordKind.PUSH_UPS, RecordKind.PLANK),
    "Running" to listOf(RecordKind.RUN),
    "Body" to listOf(RecordKind.BODY_WEIGHT),
)

private val testDayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun RecordsScreen(
    state: RecordsState,
    contentPadding: PaddingValues,
    gym: GymStatus,
    onConnectGym: () -> Unit,
    onOpen: (RecordKind) -> Unit,
) {
    val byKind = state.summaries.associateBy { it.kind }
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
        item(key = "title") { LargeTitle("Records") }
        if (gym != GymStatus.NotInstalled) {
            item(key = "gym") {
                GymCard(gym, imported = state.summaries.sumOf { s -> s.entries.count { it.imported } }, onConnect = onConnectGym)
            }
        }
        item(key = "test-day") { TestDayCard(state.nextTestDay, tested = state.summaries.count { it.entries.isNotEmpty() }, total = RecordKind.entries.size) }
        groups.forEach { (name, kinds) ->
            item(key = "h-$name") { GroupHeader(name) }
            item(key = "g-$name") {
                GlassGroup {
                    kinds.forEachIndexed { i, kind ->
                        if (i > 0) GroupSeparator(start = 16.dp)
                        RecordRow(kind, byKind[kind], onClick = { onOpen(kind) })
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                "Lifts show an estimated 1RM (Epley), so you never have to max out to see progress. " +
                    "Beat a best for +15 XP.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun TestDayCard(next: LocalDate?, tested: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .frosted()
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("NEXT TEST DAY", style = app.selfup.ui.theme.SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next?.format(testDayFormat) ?: "–", style = MaterialTheme.typography.titleLarge)
            Text(
                "First Saturday of every month. Retest 3 records.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$tested/$total", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text("tested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The link with the Gym app. There is nothing to press once connected: SelfUp pulls on its
 * own, so the card only says when it last did.
 */
@Composable
private fun GymCard(status: GymStatus, imported: Int, onConnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .frosted()
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (status is GymStatus.Connected) {
                    val dot = if (status.failed) MaterialTheme.colorScheme.error else Stat.STRENGTH.color
                    Canvas(Modifier.size(7.dp)) { drawCircle(dot) }
                }
                Text("GYM", style = app.selfup.ui.theme.SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                when (status) {
                    is GymStatus.Connected -> "Connected"
                    else -> "Connect Gym"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                when (status) {
                    GymStatus.NeedsUpdate -> "Update Gym to its latest version, then come back here."
                    is GymStatus.NeedsPermission -> "Lifts, runs and weigh-ins you log in Gym land here on their own and tick off goal tasks."
                    is GymStatus.Connected -> when {
                        status.failed -> "Couldn't read Gym just now. SelfUp will try again by itself."
                        status.lastSync == null -> "Bringing in your training…"
                        else -> buildString {
                            append("Updated ").append(relative(status.lastSync))
                            if (imported > 0) append(" · ").append(if (imported == 1) "1 entry" else "$imported entries").append(" from Gym")
                        }
                    }
                    GymStatus.NotInstalled -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (status) {
            GymStatus.NeedsUpdate -> GlassChip("Update", onClick = onConnect, accent = MaterialTheme.colorScheme.primary)
            is GymStatus.NeedsPermission -> GlassChip("Connect", onClick = onConnect, accent = MaterialTheme.colorScheme.primary)
            else -> Unit
        }
    }
}

private val syncTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val syncDateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun relative(at: Instant): String {
    val now = Instant.now()
    val minutes = Duration.between(at, now).toMinutes()
    val zone = ZoneId.systemDefault()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() -> "today at ${at.atZone(zone).format(syncTimeFormat)}"
        else -> at.atZone(zone).format(syncDateFormat)
    }
}

@Composable
private fun RecordRow(kind: RecordKind, summary: RecordSummary?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val headline = kind.metrics.first()
    val best = summary?.bests?.get(headline)
    val latest = summary?.latest
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressScale(interaction, 0.985f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kind.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    latest == null -> "Not tested yet. Tap to log a baseline"
                    headline == Metric.BODY_WEIGHT -> "Latest ${RecordFormat.entry(latest)}"
                    else -> "Last ${RecordFormat.entry(latest)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        summary?.headline?.takeIf { it.size >= 2 }?.let { Sparkline(it, kind.stat.color, inverted = headline.better == Metric.Better.LOWER) }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                best?.let { RecordFormat.metric(headline, it) } ?: latest?.let { RecordFormat.entry(it) } ?: "Log",
                style = MaterialTheme.typography.titleSmall,
                color = if (latest == null) MaterialTheme.colorScheme.primary else kind.stat.color,
            )
            if (best != null) {
                Text("best", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A tiny trend line; [inverted] flips it for metrics where lower is better, so up always means progress. */
@Composable
fun Sparkline(values: List<Double>, color: Color, inverted: Boolean = false, modifier: Modifier = Modifier.size(width = 56.dp, height = 24.dp)) {
    val points = values.takeLast(12)
    Canvas(modifier) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (points.size - 1)
        fun y(v: Double): Float {
            val t = ((v - min) / span).toFloat()
            return size.height * if (inverted) t else 1f - t
        }
        val path = Path()
        points.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width, y(points.last())))
    }
}
