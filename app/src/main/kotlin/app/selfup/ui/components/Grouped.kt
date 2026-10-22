package app.selfup.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.ui.system.Glow
import app.selfup.ui.system.PanelShape
import app.selfup.ui.system.systemPanel
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.glow

val GroupShape = PanelShape

/** A list of rows in a framed pane. Quieter than a [SystemWindow]: outlined, no brackets. */
@Composable
fun SystemGroup(modifier: Modifier = Modifier, accent: Color = SystemColors.Blue, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().systemPanel(GroupShape, accent = accent, glow = Glow.Soft), content = content)
}

/** The line between rows of a group, inset to line up with the row text. */
@Composable
fun GroupSeparator(start: Dp = 54.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(SystemColors.Line, SystemColors.Line.copy(alpha = 0.04f)))),
    )
}

/**
 * A section header: a short lit bar and the label in tracked capitals, like the headings
 * inside a System window. Trailing content (a count or a link) sits on the right.
 */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = 2.dp, end = 2.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(SystemColors.Blue),
        )
        Text(
            text.uppercase(),
            style = SectionLabel.copy(shadow = glow(radius = 8f)),
            color = SystemColors.BlueBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing()
    }
}

/** A screen's title: tracked capitals with a blue bloom, a muted line above, a fading rule below. */
@Composable
fun LargeTitle(title: String, eyebrow: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 2.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (eyebrow != null) {
            Text(eyebrow.uppercase(), style = SectionLabel, color = SystemColors.TextMuted)
        }
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.headlineLarge.copy(shadow = glow()),
            color = SystemColors.Text,
            modifier = Modifier.semantics { heading() },
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(SystemColors.Blue, SystemColors.Blue.copy(alpha = 0f)))),
        )
    }
}

/** Sinks slightly while pressed and springs back, so taps feel physical. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A framed button-chip for quick choices, such as logging a meal in one tap. */
@Composable
fun SystemChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val color = accent ?: SystemColors.Blue
    Box(
        modifier
            .heightIn(min = 44.dp)
            .pressScale(interaction, 0.94f)
            .systemPanel(PanelShape, accent = color, glow = if (accent != null) Glow.Soft else Glow.None)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = accent ?: SystemColors.Text)
    }
}

/** Secondary copy: muted, small. */
@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = SystemColors.TextMuted,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A small framed tag such as TEST, DUE or PENALTY. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.10f), PanelShape)
            .border(1.dp, color.copy(alpha = 0.55f), PanelShape)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** A small square with the System's "!" notice mark. */
@Composable
fun NoticeMark(color: Color = SystemColors.Blue, size: Dp = 22.dp) {
    Box(
        Modifier
            .size(size)
            .background(color.copy(alpha = 0.12f), PanelShape)
            .border(1.dp, color, PanelShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", style = MaterialTheme.typography.titleSmall.copy(shadow = glow(color, 10f)), color = color)
    }
}
