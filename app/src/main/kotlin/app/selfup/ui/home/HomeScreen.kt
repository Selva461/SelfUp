package app.selfup.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.core.Quest
import app.selfup.core.Stat
import app.selfup.core.StreakState
import app.selfup.ui.TodayState
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.QuestRow
import app.selfup.ui.components.StatusWindow
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.WindowHeading
import app.selfup.ui.components.playerTitle
import app.selfup.ui.theme.SystemColors
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/** "12 days · 1 shield": the streak and the shields that protect it. */
private fun streakText(streak: StreakState): String {
    val days = if (streak.current == 1) "1 day" else "${streak.current} days"
    return when (streak.shields) {
        0 -> days
        1 -> "$days · 1 shield"
        else -> "$days · ${streak.shields} shields"
    }
}

/**
 * The Today tab, laid out like the System's first two windows: the status window, then the
 * daily quest with its goals and the penalty warning. Goal tasks follow.
 */
@Composable
fun HomeScreen(
    state: TodayState,
    contentPadding: PaddingValues,
    onComplete: (Quest) -> Unit,
    onEdit: (Quest) -> Unit,
    onStatClick: (Stat) -> Unit,
    extra: LazyListScope.() -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "status") {
            StatusWindow(
                stats = state.stats,
                overallLevel = state.overallLevel,
                fields = listOf(
                    "Title" to playerTitle(state.stats),
                    "Day" to (state.day?.format(dayFormat) ?: "–"),
                    "Quests" to "${state.doneCount} / ${state.quests.size}",
                    "Streak" to streakText(state.streak),
                ),
                onStatClick = { onStatClick(it.stat) },
            )
        }
        item(key = "daily") { DailyQuestWindow(state, onComplete, onEdit) }
        extra()
    }
}

/**
 * The daily quest window: today's quests as its goals, each with a [0/1] counter, and the
 * System's warning while any are left. Missed dailies really do cost XP, so the warning is honest.
 */
@Composable
private fun DailyQuestWindow(state: TodayState, onComplete: (Quest) -> Unit, onEdit: (Quest) -> Unit) {
    val left = state.quests.size - state.doneCount
    SystemWindow(title = "Quest Info", notice = true, contentPadding = 0.dp, spacing = 10.dp) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SystemLine(
                when {
                    !state.loaded -> "[Daily Quest]"
                    state.quests.isEmpty() -> "[No daily quest today]"
                    left == 0 -> "[Daily Quest complete]"
                    else -> "[Daily Quest: $left of ${state.quests.size} remaining]"
                },
            )
            if (state.quests.isNotEmpty()) WindowHeading("Goals")
        }
        if (state.loaded && state.quests.isEmpty()) {
            MutedText(
                "Nothing due today. Add a quest from the Quests tab.",
                Modifier.padding(horizontal = 18.dp),
            )
        }
        Column {
            state.quests.forEachIndexed { i, item ->
                if (i > 0) GroupSeparator(start = 54.dp)
                QuestRow(
                    quest = item.quest,
                    done = item.isDone,
                    onTap = { if (!item.isDone) onComplete(item.quest) },
                    onLongPress = { onEdit(item.quest) },
                )
            }
        }
        if (state.quests.isNotEmpty()) {
            SystemLine(
                if (left > 0) {
                    "WARNING: Failing to complete the daily quest will bring a penalty."
                } else {
                    "All goals met. Rewards granted."
                },
                color = if (left > 0) SystemColors.Danger else SystemColors.Blue,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}
