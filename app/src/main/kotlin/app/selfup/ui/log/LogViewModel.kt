package app.selfup.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.HealthDay
import app.selfup.core.HealthLog
import app.selfup.data.HealthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LogState(
    val quickFoods: List<String> = HealthLog.defaultFoods,
    val days: List<HealthDay> = emptyList(),
    val spend: List<Pair<LocalDate, Double>> = emptyList(),
)

sealed interface LogEvent {
    data class MealLogged(val id: Long, val food: String) : LogEvent
}

class LogViewModel(private val health: HealthRepository) : ViewModel() {
    val state: StateFlow<LogState> = combine(
        health.observeMeals(HISTORY_DAYS),
        health.observeDailySpend(SPEND_DAYS),
    ) { meals, spend ->
        val today = health.today()
        val days = (0 until SHOWN_DAYS).map { today.minusDays(it) }
        LogState(
            quickFoods = HealthLog.quickFoods(meals),
            days = HealthLog.byDay(meals, health.dayBoundary, days),
            spend = spend,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogState())

    private val _events = Channel<LogEvent>(Channel.BUFFERED)
    val events: Flow<LogEvent> = _events.receiveAsFlow()

    fun logMeal(food: String) {
        if (food.isBlank()) return
        viewModelScope.launch { _events.send(LogEvent.MealLogged(health.logMeal(food), food.trim())) }
    }

    fun undo(event: LogEvent) {
        viewModelScope.launch {
            when (event) {
                is LogEvent.MealLogged -> health.deleteMeal(event.id)
            }
        }
    }

    suspend fun exportCsv(): String = health.exportCsv()

    private companion object {
        /** Meal history that ranks the quick-food chips. */
        const val HISTORY_DAYS = 30L
        const val SHOWN_DAYS = 7L
        const val SPEND_DAYS = 14L
    }
}
