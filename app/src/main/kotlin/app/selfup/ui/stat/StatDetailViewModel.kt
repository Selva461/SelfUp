package app.selfup.ui.stat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.Quest
import app.selfup.core.Stat
import app.selfup.core.StatProgress
import app.selfup.data.LifeRepository
import app.selfup.data.XpEventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatDetailState(
    val progress: StatProgress,
    val recent: List<XpEventEntity> = emptyList(),
    val feeding: List<Quest> = emptyList(),
)

class StatDetailViewModel(repository: LifeRepository, stat: Stat) : ViewModel() {
    val state: StateFlow<StatDetailState> = combine(
        repository.observeStats(listOf(stat)),
        repository.observeRecentXp(stat),
        repository.observeQuestsFeeding(stat),
    ) { progress, recent, feeding ->
        StatDetailState(progress.single(), recent, feeding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatDetailState(StatProgress(stat, 0)))
}
