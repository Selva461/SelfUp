package app.selfup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.ui.system.Glow
import app.selfup.ui.system.PanelShape
import app.selfup.ui.system.systemPanel
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.glow

/**
 * A System window, the way the Solo Leveling System pops one up: a framed pane of deep
 * blue glass with bright corner brackets and a boxed title centred on the top edge
 * (STATUS, QUEST INFO, NOTIFICATION).
 */
@Composable
fun SystemWindow(
    title: String?,
    modifier: Modifier = Modifier,
    accent: Color = SystemColors.Blue,
    notice: Boolean = false,
    contentPadding: Dp = 18.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .systemPanel(accent = accent, glow = Glow.Full)
            .padding(horizontal = contentPadding, vertical = contentPadding - 2.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title != null) WindowTitle(title, accent, notice)
        content()
    }
}

/** The boxed title with a rule running out to each side, centred like the System's headers. */
@Composable
fun WindowTitle(title: String, accent: Color = SystemColors.Blue, notice: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Rule(accent, fadeTo = Alignment.Start, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .background(accent.copy(alpha = 0.08f), PanelShape)
                .border(1.dp, accent.copy(alpha = 0.8f), PanelShape)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (notice) NoticeMark(accent, size = 20.dp)
            Text(
                title.uppercase(),
                style = SectionLabel.copy(shadow = glow(accent, 12f)),
                color = if (accent == SystemColors.Blue) SystemColors.BlueBright else accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        Rule(accent, fadeTo = Alignment.End, modifier = Modifier.weight(1f))
    }
}

/** A 1px line that fades out toward [fadeTo]. */
@Composable
fun Rule(accent: Color = SystemColors.Blue, fadeTo: Alignment.Horizontal? = null, modifier: Modifier = Modifier.fillMaxWidth()) {
    val solid = accent.copy(alpha = 0.7f)
    val clear = accent.copy(alpha = 0f)
    val brush = when (fadeTo) {
        Alignment.Start -> Brush.horizontalGradient(listOf(clear, solid))
        Alignment.End -> Brush.horizontalGradient(listOf(solid, clear))
        else -> Brush.horizontalGradient(listOf(clear, solid, clear))
    }
    Box(modifier.height(1.dp).background(brush))
}

/** A centred sub-heading inside a window, like the underlined GOAL in a quest window. */
@Composable
fun WindowHeading(text: String, accent: Color = SystemColors.Blue) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text.uppercase(),
            style = SectionLabel.copy(shadow = glow(accent, 10f)),
            color = SystemColors.Text,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .fillMaxWidth(0.22f)
                .height(1.dp)
                .background(accent.copy(alpha = 0.8f)),
        )
    }
}

/** A centred line of System copy, in the bracketed voice of its notices. */
@Composable
fun SystemLine(text: String, modifier: Modifier = Modifier, color: Color = SystemColors.Text) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(shadow = glow(radius = 8f)),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
