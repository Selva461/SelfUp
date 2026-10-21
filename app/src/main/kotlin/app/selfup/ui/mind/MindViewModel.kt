package app.selfup.ui.mind

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.MindGame
import app.selfup.core.MindRules
import app.selfup.core.StatChange
import app.selfup.data.MindRepository
import app.selfup.data.MindResult
import app.selfup.data.MindSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Mind's own accent: violet, so it reads apart from the Technical blue its XP feeds. */
val MindAccent = Color(0xFFB79CFF)

data class MindState(
    val summaries: List<MindSummary> = MindGame.entries.map { MindSummary(it, emptyList(), playedToday = false) },
    val sessionGames: List<MindGame> = emptyList(),
) {
    fun summary(game: MindGame): MindSummary = summaries.first { it.game == game }

    val sessionDone: Boolean get() = sessionGames.isNotEmpty() && sessionGames.all { summary(it).playedToday }
}

sealed interface MindEvent {
    data class Result(val result: MindResult) : MindEvent
    data class LeveledUp(val changes: List<StatChange>) : MindEvent
}

class MindViewModel(private val mind: MindRepository) : ViewModel() {
    val state: StateFlow<MindState> = mind.observeSummaries()
        .map { MindState(it, MindRules.sessionGames(mind.today().toEpochDay())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MindState(sessionGames = MindRules.sessionGames(mind.today().toEpochDay())))

    private val _events = Channel<MindEvent>(Channel.BUFFERED)
    val events: Flow<MindEvent> = _events.receiveAsFlow()

    fun record(game: MindGame, score: Double) {
        viewModelScope.launch {
            val result = mind.record(game, score)
            _events.send(MindEvent.Result(result))
            val leveled = result.changes.filter { it.leveledUp }
            if (leveled.isNotEmpty()) _events.send(MindEvent.LeveledUp(leveled))
        }
    }
}

object MindFormat {
    fun score(game: MindGame, value: Double): String = when (game) {
        MindGame.DIGIT_SPAN -> "${value.toInt()} digits"
        MindGame.N_BACK -> if (value < 1) "Not yet" else "${value.toInt()}-back"
        MindGame.SPEED_MATH -> "${value.toInt()} per min"
        MindGame.GRID_RECALL -> "${value.toInt()} cells"
        MindGame.REACTION -> "${value.toInt()} ms"
    }

    fun howTo(game: MindGame): String = when (game) {
        MindGame.DIGIT_SPAN -> "Digits flash one at a time. Type them back in order. Each success adds a digit; two misses in a row ends the run."
        MindGame.N_BACK -> "Letters appear one by one. Tap Match when a letter is the same as the one N steps back. Pass a block at 80% and N goes up."
        MindGame.SPEED_MATH -> "Solve as many as you can in 60 seconds. Answers check themselves as you type. Every 5 in a row makes it harder."
        MindGame.GRID_RECALL -> "Some squares light up. Tap them back in any order. Each success adds a square; two misses in a row ends the run."
        MindGame.REACTION -> "Wait for green, then tap as fast as you can. Five taps; your score is the middle time. Tapping early doesn't count."
    }
}
