package app.selfup.ui.system

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.selfup.ui.components.NoticeMark
import app.selfup.ui.components.pressScale
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.glow

class SystemTab(val route: String, val label: String, val icon: ImageVector)

/** Height of the tab bar, so screens can pad their last row clear of it. */
val SystemTabBarHeight = 64.dp

/**
 * The tab bar as a System window strip: dark glass, a lit frame, and a marker that springs
 * to the selected tab, which glows.
 */
@Composable
fun SystemTabBar(
    tabs: List<SystemTab>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val index = tabs.indexOfFirst { it.route == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .height(SystemTabBarHeight)
            .systemPanel(tint = SystemColors.Pane.copy(alpha = 0.78f), glow = Glow.Soft, brackets = true, layer = { it.chrome }),
    ) {
        val itemWidth = maxWidth / tabs.size
        val markerX by animateDpAsState(
            targetValue = itemWidth * index,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "marker",
        )
        // The selected tab's light: a bright bar on the top edge with a glow falling under it.
        Box(
            Modifier
                .offset(x = markerX)
                .width(itemWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(SystemColors.Blue.copy(alpha = 0.22f), Color.Transparent))),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height(2.dp)
                    .background(SystemColors.BlueBright),
            )
        }
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val isSelected = tab.route == selected
                val tint by animateColorAsState(
                    if (isSelected) SystemColors.BlueBright else SystemColors.TextMuted,
                    label = "tint",
                )
                Column(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .semantics { this.selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(tab.route)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        tab.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(shadow = if (isSelected) glow(radius = 8f) else null),
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A square framed button for the screen's main action, beside the tab bar. */
@Composable
fun SystemIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SystemColors.BlueBright,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(SystemTabBarHeight)
            .pressScale(interaction, 0.94f)
            .systemPanel(tint = SystemColors.Blue.copy(alpha = 0.22f), glow = Glow.Full, layer = { it.chrome })
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/** Feedback as a small System notice: the "!" mark, the message, and an optional action. */
@Composable
fun SystemSnackbar(data: SnackbarData) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .systemPanel(tint = SystemColors.Pane.copy(alpha = 0.85f), glow = Glow.Soft, layer = { it.chrome })
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoticeMark(size = 20.dp)
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = SystemColors.Text,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        data.visuals.actionLabel?.let { label ->
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(PanelShape)
                    .clickable(role = Role.Button, onClick = data::performAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = SystemColors.Blue)
            }
        }
    }
}
