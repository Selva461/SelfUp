package app.selfup.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.Concept
import app.selfup.core.ConceptState
import app.selfup.core.SkillPack
import app.selfup.core.StatChange
import app.selfup.core.StudyPlanner
import app.selfup.core.StudySession
import app.selfup.core.packs.Packs
import app.selfup.data.SkillResult
import app.selfup.data.SkillsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SkillsState(
    val packs: List<SkillPack> = Packs.all,
    val states: Map<String, ConceptState> = emptyMap(),
    val today: LocalDate = LocalDate.MIN,
) {
    fun state(concept: Concept): ConceptState = states[concept.id] ?: ConceptState()

    fun session(pack: SkillPack): StudySession = StudyPlanner.session(pack, states, today)
}

sealed interface SkillsEvent {
    data class Result(val result: SkillResult) : SkillsEvent
    data class LeveledUp(val changes: List<StatChange>) : SkillsEvent
}

class SkillsViewModel(private val skills: SkillsRepository) : ViewModel() {
    val state: StateFlow<SkillsState> = skills.observeStates()
        .map { SkillsState(states = it, today = skills.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkillsState(today = skills.today()))

    private val _events = Channel<SkillsEvent>(Channel.BUFFERED)
    val events: Flow<SkillsEvent> = _events.receiveAsFlow()

    fun read(conceptId: String) = record { skills.read(conceptId) }

    fun quiz(conceptId: String, correct: Int, total: Int) = record { skills.quiz(conceptId, correct, total) }

    fun applied(conceptId: String) = record { skills.applied(conceptId) }

    fun taught(conceptId: String) = record { skills.taught(conceptId) }

    private fun record(block: suspend () -> SkillResult) {
        viewModelScope.launch {
            val result = block()
            _events.send(SkillsEvent.Result(result))
            val leveled = result.changes.filter { it.leveledUp }
            if (leveled.isNotEmpty()) _events.send(SkillsEvent.LeveledUp(leveled))
        }
    }
}
