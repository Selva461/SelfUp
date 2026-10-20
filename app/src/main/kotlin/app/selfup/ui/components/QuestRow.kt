package app.selfup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.core.CompletionInput
import app.selfup.core.Quest
import app.selfup.core.XpRules
import app.selfup.ui.theme.Ink
import app.selfup.ui.theme.color

/**
 * One quest, as a row inside a [GlassGroup]. Tap completes it (logging quests open a
 * number entry first), long-press edits it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestRow(
    quest: Quest,
    done: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = quest.stats.sortedBy { it.ordinal }
    val accent = stats.first().color
    val interaction = remember { MutableInteractionSource() }
    val titleColor by animateColorAsState(
        if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        label = "title",
    )
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onClick = onTap,
                onLongClick = onLongPress,
                onLongClickLabel = "Edit quest",
            )
            .pressScale(interaction, 0.985f)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .semantics { contentDescription = if (done) "${quest.title}, done" else quest.title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SpringCheck(done, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                quest.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                stats.forEach { Dot(it.color) }
                Text(
                    questSubtitle(quest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        XpPill(XpRules.completionXp(quest.difficulty), accent, dimmed = done)
    }
}

/** Stat names and markers, without the XP (the pill shows that). */
fun questSubtitle(quest: Quest): String {
    val parts = buildList {
        add(quest.stats.sortedBy { it.ordinal }.joinToString(" · ") { it.displayName })
        if (quest.input is CompletionInput.Number) add("Log")
        if (quest.penaltyFor != null) add("Make-up")
    }
    return parts.joinToString("  ·  ")
}

@Composable
private fun Dot(color: Color, size: Dp = 6.dp) {
    Box(
        Modifier
            .size(size)
            .background(color, CircleShape),
    )
}

@Composable
fun XpPill(xp: Int, accent: Color, dimmed: Boolean = false) {
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "pill")
    Text(
        "+$xp",
        style = MaterialTheme.typography.labelMedium,
        color = accent.copy(alpha = alpha),
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f * alpha), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * A round check that fills with a spring and draws its tick, like Reminders. The fill
 * overshoots a little, which reads as satisfying rather than mechanical.
 */
@Composable
fun SpringCheck(checked: Boolean, accent: Color, size: Dp = 26.dp) {
    val fill by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "fill",
    )
    val tick by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        label = "tick",
    )
    val tickPath = remember { Path() }
    val measure = remember { PathMeasure() }
    val partial = remember { Path() }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val stroke = 2.dp.toPx()
        drawCircle(accent.copy(alpha = 0.55f + 0.45f * fill.coerceIn(0f, 1f)), radius = r - stroke / 2, style = Stroke(stroke))
        if (fill > 0f) drawCircle(accent, radius = (r * fill).coerceAtMost(r * 1.08f))
        if (tick > 0f) {
            tickPath.reset()
            tickPath.moveTo(r * 0.55f, r * 1.02f)
            tickPath.lineTo(r * 0.88f, r * 1.34f)
            tickPath.lineTo(r * 1.47f, r * 0.70f)
            measure.setPath(tickPath, false)
            partial.reset()
            measure.getSegment(0f, measure.length * tick.coerceIn(0f, 1f), partial, true)
            drawPath(partial, Ink, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
