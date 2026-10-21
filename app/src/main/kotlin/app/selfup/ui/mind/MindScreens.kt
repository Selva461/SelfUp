package app.selfup.ui.mind

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.selfup.core.Metric
import app.selfup.core.MindGame
import app.selfup.core.MindRules
import app.selfup.data.MindRepository
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.pressScale
import app.selfup.ui.glass.frosted
import app.selfup.ui.records.Sparkline
import app.selfup.ui.skills.PrimaryButton
import app.selfup.ui.theme.SectionLabel

/** The Mind section of the Skills tab: today's session, then every game with its best. */
fun LazyListScope.mindSection(state: MindState, onSession: () -> Unit, onGame: (MindGame) -> Unit) {
    item(key = "mind-h") { GroupHeader("Mind") }
    item(key = "mind-session") { MindSessionCard(state, onSession) }
    item(key = "mind-games") {
        GlassGroup {
            state.summaries.forEachIndexed { i, summary ->
                if (i > 0) GroupSeparator(start = 16.dp)
                GameRow(summary.game, summary.best, summary.scores, summary.playedToday, onClick = { onGame(summary.game) })
            }
        }
    }
}

@Composable
private fun MindSessionCard(state: MindState, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .frosted(tint = MindAccent.copy(alpha = 0.08f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("TODAY · MIND", style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (state.sessionDone) "Session done" else "10-minute session", style = MaterialTheme.typography.titleLarge)
        Text(
            state.sessionGames.joinToString(" · ") { it.displayName } +
                if (state.sessionDone) ". Play again to chase a best." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(if (state.sessionDone) "Play again" else "Start", MindAccent, onStart)
    }
}

@Composable
private fun GameRow(game: MindGame, best: Double?, scores: List<Double>, playedToday: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressScale(interaction, 0.985f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(game.displayName, style = MaterialTheme.typography.bodyLarge)
                if (playedToday) Box(Modifier.size(6.dp).background(MindAccent, RoundedCornerShape(percent = 50)))
            }
            Text(game.trains, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (scores.size >= 2) Sparkline(scores, MindAccent, inverted = game.better == Metric.Better.LOWER)
        Text(
            best?.let { MindFormat.score(game, it) } ?: "Not played",
            style = MaterialTheme.typography.labelLarge,
            color = if (best != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private sealed interface MindStep {
    data class Intro(val index: Int) : MindStep
    data class Play(val index: Int) : MindStep
    data class Result(val index: Int, val score: Double, val previousBest: Double?, val xp: Int) : MindStep
    data object Done : MindStep
}

/**
 * Plays [games] back to back: how-to, the game, then its score against your best. Each score
 * is saved as soon as its game ends, so leaving halfway keeps what was finished.
 */
@Composable
fun MindSessionScreen(
    games: List<MindGame>,
    state: MindState,
    onScored: (MindGame, Double) -> Unit,
    onClose: () -> Unit,
) {
    val latest by rememberUpdatedState(state)
    var step by remember { mutableStateOf<MindStep>(if (games.isEmpty()) MindStep.Done else MindStep.Intro(0)) }
    val results = remember { mutableStateListOf<Pair<MindGame, Double>>() }
    var played by remember { mutableIntStateOf(0) }

    fun finish(index: Int, score: Double) {
        val game = games[index]
        // Read the history before this score lands, so the preview matches what the ledger grants.
        val summary = latest.summary(game)
        val xp = MindRules.xpFor(game, score, summary.scores, summary.playedToday)
        onScored(game, score)
        results += game to score
        played = index + 1
        step = MindStep.Result(index, score, summary.best, xp)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close session") }
            SessionBar(played, games.size, Modifier.weight(1f))
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(160)))
            },
            label = "mind",
            modifier = Modifier.weight(1f).padding(bottom = 16.dp),
        ) { current ->
            when (current) {
                is MindStep.Intro -> IntroStep(games[current.index], state) { step = MindStep.Play(current.index) }
                is MindStep.Play -> {
                    val game = games[current.index]
                    val done: (Double) -> Unit = { finish(current.index, it) }
                    when (game) {
                        MindGame.DIGIT_SPAN -> DigitSpanGame(MindAccent, done)
                        MindGame.N_BACK -> NBackGame(startN = state.summary(game).latest?.toInt()?.coerceAtLeast(1) ?: 1, MindAccent, done)
                        MindGame.SPEED_MATH -> SpeedMathGame(MindAccent, done)
                        MindGame.GRID_RECALL -> GridRecallGame(MindAccent, done)
                        MindGame.REACTION -> ReactionGame(MindAccent, done)
                    }
                }
                is MindStep.Result -> ResultStep(
                    game = games[current.index],
                    score = current.score,
                    previousBest = current.previousBest,
                    xp = current.xp,
                    isLast = current.index == games.lastIndex,
                    onContinue = { step = if (current.index == games.lastIndex) MindStep.Done else MindStep.Intro(current.index + 1) },
                )
                MindStep.Done -> DoneStep(results, onClose)
            }
        }
    }
}

@Composable
private fun SessionBar(done: Int, total: Int, modifier: Modifier) {
    Row(modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(if (i < done) MindAccent else MindAccent.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)),
            )
        }
    }
}

@Composable
private fun IntroStep(game: MindGame, state: MindState, onStart: () -> Unit) {
    val best = state.summary(game).best
    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(game.trains.uppercase(), style = SectionLabel, color = MindAccent)
        Text(game.displayName, style = MaterialTheme.typography.headlineLarge)
        GlassGroup {
            Text(MindFormat.howTo(game), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            GroupSeparator(start = 16.dp)
            Row(Modifier.padding(16.dp)) {
                Text("Your best", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(best?.let { MindFormat.score(game, it) } ?: "First time", style = MaterialTheme.typography.titleMedium, color = MindAccent)
            }
        }
        Box(Modifier.weight(1f))
        PrimaryButton("Start", MindAccent, onStart)
    }
}

@Composable
private fun ResultStep(game: MindGame, score: Double, previousBest: Double?, xp: Int, isLast: Boolean, onContinue: () -> Unit) {
    val newBest = previousBest != null && game.isBetter(score, previousBest)
    Column(
        Modifier.fillMaxSize().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(game.displayName.uppercase(), style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(MindFormat.score(game, score), style = MaterialTheme.typography.displayMedium, color = if (newBest) MindAccent else MaterialTheme.colorScheme.onSurface)
        Text(
            when {
                previousBest == null -> "Your first score. Beat it next time."
                newBest -> "New best. It was ${MindFormat.score(game, previousBest)}."
                else -> "Best is ${MindFormat.score(game, previousBest)}."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (xp > 0) {
            Text(
                "+$xp ${MindRepository.STAT.displayName}",
                style = MaterialTheme.typography.labelLarge,
                color = MindAccent,
                modifier = Modifier
                    .background(MindAccent.copy(alpha = 0.14f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f))
        PrimaryButton(if (isLast) "Finish" else "Next game", MindAccent, onContinue)
    }
}

@Composable
private fun DoneStep(results: List<Pair<MindGame, Double>>, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session done", style = MaterialTheme.typography.headlineLarge)
        GlassGroup {
            results.forEachIndexed { i, (game, score) ->
                if (i > 0) GroupSeparator(start = 16.dp)
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(game.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(MindFormat.score(game, score), style = MaterialTheme.typography.titleMedium, color = MindAccent)
                }
            }
        }
        Text(
            "Same time tomorrow. Scores move slowly; the trend over weeks is what counts.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
        Box(Modifier.weight(1f))
        PrimaryButton("Done", MindAccent, onClose)
    }
}
