package app.selfup.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.ai.GroqPlanner
import app.selfup.ai.JudgeReply
import app.selfup.core.FoodQuality
import app.selfup.core.HealthDay
import app.selfup.core.HealthLog
import app.selfup.core.Nutrition
import app.selfup.core.StatChange
import app.selfup.core.WorkJudge
import app.selfup.data.HealthRepository
import app.selfup.data.WorkImport
import app.selfup.data.WorkLogEntity
import app.selfup.data.WorkRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LogState(
    val quickFoods: List<String> = HealthLog.defaultFoods,
    val days: List<HealthDay> = emptyList(),
    val spend: List<Pair<LocalDate, Double>> = emptyList(),
    /** Recent things built, newest first. */
    val works: List<WorkLogEntity> = emptyList(),
) {
    /** The quality a new log of [food] would get, for the chips and the dialog. */
    fun qualityOf(food: String): FoodQuality = Nutrition.qualityFor(food, days.flatMap { it.meals })
}

sealed interface LogEvent {
    data class MealLogged(val id: Long, val food: String, val quality: FoodQuality) : LogEvent
    data class WorkAdded(val title: String, val xp: Int, val changes: List<StatChange>) : LogEvent
    data class WorkRejected(val message: String) : LogEvent
}

class LogViewModel(
    private val health: HealthRepository,
    private val work: WorkRepository? = null,
    private val planner: GroqPlanner? = null,
) : ViewModel() {
    val state: StateFlow<LogState> = combine(
        health.observeMeals(HISTORY_DAYS),
        health.observeDailySpend(SPEND_DAYS),
        work?.observe() ?: flowOf(emptyList()),
    ) { meals, spend, works ->
        val today = health.today()
        val days = (0 until SHOWN_DAYS).map { today.minusDays(it) }
        LogState(
            quickFoods = HealthLog.quickFoods(meals),
            days = HealthLog.byDay(meals, health.dayBoundary, days),
            spend = spend,
            works = works.take(SHOWN_WORKS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogState())

    private val _events = Channel<LogEvent>(Channel.BUFFERED)
    val events: Flow<LogEvent> = _events.receiveAsFlow()

    private val _judging = MutableStateFlow(false)

    /** True while Groq is scoring a description. */
    val judging: StateFlow<Boolean> = _judging.asStateFlow()

    fun logMeal(food: String, quality: FoodQuality? = null) {
        if (food.isBlank()) return
        viewModelScope.launch {
            val id = health.logMeal(food, quality)
            _events.send(LogEvent.MealLogged(id, food.trim(), quality ?: state.value.qualityOf(food)))
        }
    }

    fun setQuality(id: Long, quality: FoodQuality) {
        viewModelScope.launch { health.setQuality(id, quality) }
    }

    /** Imports a work code Claude wrote. */
    fun importWork(code: String) {
        val repo = work ?: return
        viewModelScope.launch { _events.send(toEvent(repo.import(code))) }
    }

    /** Has Groq score [description], then files its verdict as Groq's. */
    fun judgeWork(description: String) {
        val repo = work ?: return
        val groq = planner ?: return
        if (description.isBlank() || _judging.value) return
        viewModelScope.launch {
            _judging.value = true
            try {
                when (val reply = groq.judge(description)) {
                    is JudgeReply.Failed -> _events.send(LogEvent.WorkRejected(reply.message))
                    is JudgeReply.Ok -> _events.send(toEvent(repo.import(reply.code, judge = WorkJudge.GROQ)))
                }
            } finally {
                _judging.value = false
            }
        }
    }

    private fun toEvent(result: WorkImport): LogEvent = when (result) {
        is WorkImport.Added -> LogEvent.WorkAdded(result.report.title, result.xp, result.changes)
        WorkImport.Duplicate -> LogEvent.WorkRejected("That work was already scored.")
        is WorkImport.Invalid -> LogEvent.WorkRejected("Line ${result.line}: ${result.message}")
    }

    fun undo(event: LogEvent) {
        viewModelScope.launch {
            when (event) {
                is LogEvent.MealLogged -> health.deleteMeal(event.id)
                else -> Unit
            }
        }
    }

    suspend fun exportCsv(): String = health.exportCsv()

    private companion object {
        /** Meal history that ranks the quick-food chips. */
        const val HISTORY_DAYS = 30L
        const val SHOWN_DAYS = 7L
        const val SPEND_DAYS = 14L
        const val SHOWN_WORKS = 5
    }
}
