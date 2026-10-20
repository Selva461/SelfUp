package app.selfup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.Quest
import app.selfup.core.StatChange
import app.selfup.core.StatProgress
import app.selfup.data.CompletionResult
import app.selfup.data.DayCloseReport
import app.selfup.data.DayQuest
import app.selfup.data.LifeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayState(
    val day: LocalDate? = null,
    val stats: List<StatProgress> = emptyList(),
    val quests: List<DayQuest> = emptyList(),
) {
    val loaded: Boolean get() = day != null
    val overallLevel: Int get() = StatProgress.overallLevel(stats)
    val doneCount: Int get() = quests.count { it.isDone }
}

sealed interface GameEvent {
    /** XP was granted; the UI offers an undo. */
    data class Completed(val quest: Quest, val changes: List<StatChange>) : GameEvent

    /** One or more stats gained a level from a single completion. */
    data class LeveledUp(val changes: List<StatChange>) : GameEvent

    /** Yesterday's misses were penalized and make-up quests added. */
    data class DayClosed(val report: DayCloseReport) : GameEvent
}

/** Today's quests and stats, shared by Home and Quests so both react to the same events. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(private val repository: LifeRepository) : ViewModel() {
    private val _events = Channel<GameEvent>(Channel.BUFFERED)
    val events: Flow<GameEvent> = _events.receiveAsFlow()

    val state: StateFlow<TodayState> = repository.observeToday()
        // Settle penalties for finished days before showing the new one.
        .onEach { _ ->
            val report = repository.closeFinishedDays()
            if (!report.isEmpty) _events.send(GameEvent.DayClosed(report))
        }
        .flatMapLatest { day ->
            combine(repository.observeStats(), repository.observeDay(day)) { stats, quests ->
                TodayState(day, stats, quests)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun complete(quest: Quest, value: Double? = null) {
        viewModelScope.launch {
            val result = repository.complete(quest.id, value)
            if (result is CompletionResult.Completed) {
                val levelUps = result.changes.filter { it.leveledUp }
                if (levelUps.isNotEmpty()) _events.send(GameEvent.LeveledUp(levelUps))
                _events.send(GameEvent.Completed(quest, result.changes))
            }
        }
    }

    fun undo(questId: Long) {
        viewModelScope.launch { repository.undoCompletion(questId) }
    }

    suspend fun lastLoggedValue(questId: Long): Double? = repository.lastLoggedValue(questId)

    fun save(quest: Quest) {
        viewModelScope.launch { repository.saveQuest(quest) }
    }

    fun delete(questId: Long) {
        viewModelScope.launch { repository.deleteQuest(questId) }
    }

    /** Every quest, for the manage list, independent of whether it is due today. */
    val allQuests: StateFlow<List<Quest>> = repository.observeAllQuests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
