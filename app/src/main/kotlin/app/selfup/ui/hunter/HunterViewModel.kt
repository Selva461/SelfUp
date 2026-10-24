package app.selfup.ui.hunter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.Achievement
import app.selfup.core.Achievements
import app.selfup.core.RestRefusal
import app.selfup.core.Reward
import app.selfup.data.BuyResult
import app.selfup.data.HunterOverview
import app.selfup.data.HunterRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface HunterEvent {
    data class Unlocked(val achievements: List<Achievement>) : HunterEvent
    data class Bought(val reward: Reward, val balance: Int) : HunterEvent
    data class TooPoor(val reward: Reward, val short: Int) : HunterEvent
    data object DailyLimit : HunterEvent
    data class RestDeclared(val day: LocalDate) : HunterEvent
    data class RestRefused(val refusal: RestRefusal) : HunterEvent
    data class RestCancelled(val day: LocalDate, val ok: Boolean) : HunterEvent
}

class HunterViewModel(private val repository: HunterRepository) : ViewModel() {
    private val _events = Channel<HunterEvent>(Channel.BUFFERED)
    val events: Flow<HunterEvent> = _events.receiveAsFlow()

    val state: StateFlow<HunterOverview?> = repository.observe()
        // Any XP change can earn an achievement; unlock it as soon as the facts show it.
        .onEach { overview ->
            if (Achievements.newlyEarned(overview.facts, overview.unlocked.keys).isNotEmpty()) {
                val unlocked = repository.checkAchievements()
                if (unlocked.isNotEmpty()) _events.send(HunterEvent.Unlocked(unlocked))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun buy(reward: Reward) {
        viewModelScope.launch {
            when (val result = repository.buy(reward.id)) {
                is BuyResult.Bought -> {
                    _events.send(HunterEvent.Bought(result.reward, result.balance))
                    if (result.unlocked.isNotEmpty()) _events.send(HunterEvent.Unlocked(result.unlocked))
                }
                is BuyResult.TooPoor -> _events.send(HunterEvent.TooPoor(reward, result.short))
                BuyResult.DailyLimit -> _events.send(HunterEvent.DailyLimit)
                BuyResult.NotFound -> Unit
            }
        }
    }

    fun saveReward(reward: Reward) {
        viewModelScope.launch { repository.saveReward(reward) }
    }

    fun deleteReward(id: Long) {
        viewModelScope.launch { repository.deleteReward(id) }
    }

    fun equip(achievementId: String?) {
        viewModelScope.launch { repository.equipTitle(achievementId) }
    }

    fun declareRest(day: LocalDate) {
        viewModelScope.launch {
            val refusal = repository.declareRest(day)
            _events.send(if (refusal == null) HunterEvent.RestDeclared(day) else HunterEvent.RestRefused(refusal))
        }
    }

    fun cancelRest(day: LocalDate) {
        viewModelScope.launch { _events.send(HunterEvent.RestCancelled(day, repository.cancelRest(day))) }
    }
}
