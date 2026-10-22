package app.selfup.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.selfup.core.ScreenTime
import app.selfup.focus.ScreenToday
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemBar
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.theme.SystemColors

/** The screen-time window on Today: entertainment minutes against the budget, and the lock's state. */
fun LazyListScope.screenTimeSection(state: ScreenToday?, onAllowUsage: () -> Unit, onEnableBlocker: () -> Unit) {
    if (state == null) return
    item(key = "screen-time") { ScreenTimeWindow(state, onAllowUsage, onEnableBlocker) }
}

@Composable
fun ScreenTimeWindow(state: ScreenToday, onAllowUsage: () -> Unit, onEnableBlocker: () -> Unit) {
    val accent = if (state.spent) SystemColors.Danger else SystemColors.Blue
    SystemWindow(title = "Screen Time", accent = accent) {
        if (!state.hasAccess) {
            SystemLine("[Screen time is not linked]")
            MutedText(
                "Give SelfUp usage access and the System checks \"Phone off by 23:00\" by itself, " +
                    "pays +${ScreenTime.UNDER_BUDGET_XP} Life XP for a day under ${ScreenTime.DAILY_BUDGET_MINUTES} minutes of " +
                    "entertainment, and can lock those apps once the budget is spent.",
            )
            SystemButton("Allow usage access", onClick = onAllowUsage)
            return@SystemWindow
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Entertainment", style = MaterialTheme.typography.titleSmall, color = SystemColors.Text)
            Text("${state.minutes} / ${state.budget} min", style = MaterialTheme.typography.titleSmall, color = accent)
        }
        SystemBar(state.minutes.toFloat() / state.budget, accent)
        when {
            !state.blockerOn -> {
                MutedText("Turn on the app lock to block Instagram, YouTube and games once the budget is spent.")
                SystemButton("Turn on app lock", onClick = onEnableBlocker, filled = false)
            }
            state.spent -> SystemLine("[Budget spent. Entertainment apps are locked until reset.]", color = SystemColors.Danger, style = MaterialTheme.typography.labelMedium)
            else -> SystemLine("[App lock armed: ${state.budget - state.minutes} min left today]", style = MaterialTheme.typography.labelMedium)
        }
    }
}
