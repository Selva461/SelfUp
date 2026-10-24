package app.selfup.ui.dsa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.DsaDifficulty
import app.selfup.core.DsaOutcome
import app.selfup.core.DsaTopic
import app.selfup.data.DsaOverview
import app.selfup.data.DsaRepository
import app.selfup.data.DsaResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DsaViewModel(private val repository: DsaRepository) : ViewModel() {
    private val _results = Channel<DsaResult>(Channel.BUFFERED)
    val results: Flow<DsaResult> = _results.receiveAsFlow()

    val state: StateFlow<DsaOverview?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(title: String, topic: DsaTopic, difficulty: DsaDifficulty, url: String?, outcome: DsaOutcome, minutes: Int?) {
        viewModelScope.launch { _results.send(repository.add(title, topic, difficulty, url, outcome, minutes)) }
    }

    fun attempt(problemId: Long, outcome: DsaOutcome, minutes: Int?) {
        viewModelScope.launch { _results.send(repository.attempt(problemId, outcome, minutes)) }
    }

    fun undo(attemptId: Long) {
        viewModelScope.launch { repository.undo(attemptId) }
    }
}
