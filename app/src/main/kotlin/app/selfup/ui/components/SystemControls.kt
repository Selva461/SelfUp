@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.selfup.ui.system.Glow
import app.selfup.ui.system.PanelShape
import app.selfup.ui.system.systemPanel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.glow
import kotlin.math.cos
import kotlin.math.sin

/**
 * The System's action button: a lit, framed bar with the label in tracked capitals.
 * [filled] is the one main action on a screen; otherwise it's outlined.
 */
@Composable
fun SystemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = SystemColors.Blue,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val tone = if (enabled) accent else SystemColors.TextMuted
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interaction)
            .systemPanel(
                accent = tone,
                tint = if (filled && enabled) accent.copy(alpha = 0.22f) else SystemColors.Pane.copy(alpha = 0.5f),
                glow = if (enabled && filled) Glow.Full else if (enabled) Glow.Soft else Glow.None,
                brackets = false,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(shadow = if (enabled) glow(accent, 10f) else null),
            color = if (enabled) (if (filled) SystemColors.Text else accent) else SystemColors.TextMuted.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** A text-only action with a full 48dp touch target. */
@Composable
fun SystemTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SystemColors.Blue,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(PanelShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) color else SystemColors.TextMuted.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A square check that lights up and draws its tick. The fill overshoots a little, which
 * reads as satisfying rather than mechanical.
 */
@Composable
fun SystemCheck(checked: Boolean, accent: Color, size: Dp = 24.dp) {
    val fill by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
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
        val s = this.size.minDimension
        val line = 1.5.dp.toPx()
        val f = fill.coerceIn(0f, 1.1f)
        // Bloom behind the box once it's lit.
        if (f > 0f) {
            drawRect(accent.copy(alpha = 0.18f * f.coerceAtMost(1f)), topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()), size = Size(s + 6.dp.toPx(), s + 6.dp.toPx()))
        }
        drawRect(accent.copy(alpha = 0.6f + 0.4f * f.coerceAtMost(1f)), topLeft = Offset(line / 2, line / 2), size = Size(s - line, s - line), style = Stroke(line))
        if (f > 0f) {
            val inset = s * (0.5f - 0.5f * f.coerceAtMost(1f)) + 3.dp.toPx() * f.coerceAtMost(1f)
            drawRect(accent.copy(alpha = 0.9f), topLeft = Offset(inset, inset), size = Size(s - inset * 2, s - inset * 2))
        }
        if (tick > 0f) {
            tickPath.reset()
            tickPath.moveTo(s * 0.27f, s * 0.52f)
            tickPath.lineTo(s * 0.44f, s * 0.68f)
            tickPath.lineTo(s * 0.74f, s * 0.35f)
            measure.setPath(tickPath, false)
            partial.reset()
            measure.getSegment(0f, measure.length * tick.coerceIn(0f, 1f), partial, true)
            drawPath(partial, SystemColors.Void, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter))
        }
    }
}

/** The reward for a quest, written the way the System writes it. */
@Composable
fun XpTag(xp: Int, accent: Color, dimmed: Boolean = false) {
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "xp")
    Text(
        "+$xp XP",
        style = MaterialTheme.typography.labelMedium,
        color = accent.copy(alpha = alpha),
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.45f * alpha), PanelShape)
            .background(accent.copy(alpha = 0.08f * alpha), PanelShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A thin lit bar, like the HP and MP bars in the status window. */
@Composable
fun SystemBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, light: Color = color) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 60f), label = "bar")
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(color.copy(alpha = 0.14f))
        drawRect(color.copy(alpha = 0.35f), style = Stroke(1f))
        val w = size.width * animated
        if (w > 0f) {
            drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), light), endX = w), size = Size(w, size.height))
            // A hot tip where the bar ends.
            drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset((w - 2.dp.toPx()).coerceAtLeast(0f), 0f), size = Size(2.dp.toPx().coerceAtMost(w), size.height))
        }
    }
}

/**
 * A HUD dial: the circle cut into segments, lit up to [fraction]. Used wherever a ring
 * shows progress (stat level, goal, mastery, quiz score).
 */
@Composable
fun SystemDial(fraction: Float, color: Color, size: Dp, stroke: Dp, modifier: Modifier = Modifier, segments: Int = 40) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 50f), label = "dial")
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val r = (this.size.minDimension - s) / 2
        val step = 360f / segments
        val gap = 2.6f
        val lit = animated * segments
        for (i in 0 until segments) {
            val on = i < lit
            val partialAlpha = if (on && i + 1 > lit) (lit - i) else 1f
            drawArc(
                color = if (on) color.copy(alpha = 0.95f * partialAlpha) else color.copy(alpha = 0.14f),
                startAngle = -90f + i * step + gap / 2,
                sweepAngle = step - gap,
                useCenter = false,
                topLeft = Offset(s / 2, s / 2),
                size = Size(r * 2, r * 2),
                style = Stroke(s, cap = StrokeCap.Butt),
            )
        }
        // A thin inner rule, the dial's bezel.
        drawCircle(color.copy(alpha = 0.25f), radius = r - s / 2 - 3.dp.toPx(), style = Stroke(1f))
        if (animated > 0f) {
            val angle = Math.toRadians((animated * 360f - 90f).toDouble())
            val tip = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), center = tip, radius = s * 1.8f), radius = s * 1.8f, center = tip)
        }
    }
}

/** A secondary screen: back button and title in the System's lettering. */
@Composable
fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { SystemTopBar(title, onBack) },
        content = content,
    )
}

@Composable
fun SystemTopBar(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SystemColors.BlueBright)
            }
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(shadow = glow(radius = 10f), letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing),
                color = SystemColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
                    .semantics { heading() },
            )
        }
        Rule(fadeTo = Alignment.End, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp).fillMaxWidth(0.7f))
    }
}

/** A bottom sheet that rises as a System window: dark glass, a lit top edge, a short handle. */
@Composable
fun SystemSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        containerColor = Color(0xFF040E1A),
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Rule(modifier = Modifier.fillMaxWidth())
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 14.dp)
                        .width(40.dp)
                        .height(3.dp)
                        .background(SystemColors.Blue.copy(alpha = 0.6f)),
                )
            }
        },
        content = content,
    )
}

/** A sheet's title row: the boxed System title. */
@Composable
fun SheetTitle(text: String, accent: Color = SystemColors.Blue) {
    WindowTitle(text, accent)
}

/**
 * A modal System window with a confirm and cancel action, in place of the stock alert dialog.
 */
@Composable
fun SystemDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String = "Cancel",
    accent: Color = SystemColors.Blue,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        SystemWindow(title = title, accent = accent, notice = true, spacing = 16.dp) {
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SystemButton(dismissText, onDismiss, Modifier.weight(1f), accent = accent, filled = false)
                SystemButton(confirmText, onConfirm, Modifier.weight(1f), accent = accent, enabled = confirmEnabled)
            }
        }
    }
}

/** One selectable option, framed and lit when selected. */
@Composable
fun SystemChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = SystemColors.Blue,
) {
    val interaction = remember { MutableInteractionSource() }
    val textColor by animateColorAsState(if (selected) SystemColors.Text else SystemColors.TextMuted, label = "choice")
    Box(
        modifier
            .heightIn(min = 44.dp)
            .pressScale(interaction, 0.95f)
            .background(if (selected) accent.copy(alpha = 0.20f) else SystemColors.Pane.copy(alpha = 0.6f), PanelShape)
            .border(1.dp, if (selected) accent else SystemColors.Line, PanelShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(shadow = if (selected) glow(accent, 8f) else null),
            color = textColor,
            maxLines = 1,
        )
    }
}

/** A row of equal-width options where exactly one is chosen. */
@Composable
fun <T> SystemSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = SystemColors.Blue,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            SystemChoice(label(option), option == selected, { onSelect(option) }, Modifier.weight(1f), accent)
        }
    }
}

/** A labelled block inside a sheet or form. */
@Composable
fun FormSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = SystemColors.TextMuted)
        content()
    }
}

/** Fills the remaining height in a column, for pushing a button to the bottom. */
@Composable
fun ColumnScope.Fill() = Spacer(Modifier.weight(1f))

/** The screen's main call to action: a filled [SystemButton] in [accent]. */
@Composable
fun PrimaryButton(text: String, accent: Color, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) =
    SystemButton(text, onClick, modifier, accent = accent, enabled = enabled)
