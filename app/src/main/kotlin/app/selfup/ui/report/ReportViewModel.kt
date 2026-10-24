package app.selfup.ui.report

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.GuidedReport
import app.selfup.core.HealthLog
import app.selfup.core.Quest
import app.selfup.core.ReportItem
import app.selfup.data.FocusEventKind
import app.selfup.data.HealthRepository
import app.selfup.data.LifeRepository
import app.selfup.focus.FocusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** The guided report while it is open. */
data class ReportState(
    val items: List<ReportItem>,
    val index: Int = 0,
    val quickFoods: List<String> = HealthLog.defaultFoods,
    val lastMeal: Instant? = null,
    /** Meals logged at the current meal question. */
    val mealsNow: List<String> = emptyList(),
    val answered: Int = 0,
    /** Quests completed from this report; their completion may still be saving. */
    val completed: Set<Long> = emptySet(),
    /** Quests still open once every question is answered; null until then. */
    val openAfter: List<Quest>? = null,
) {
    val current: ReportItem? get() = items.getOrNull(index)
    val finished: Boolean get() = openAfter != null
}

/**
 * Walks through the guided report one question at a time (see [GuidedReport]). Every answer
 * is logged, "Not yet" included. Quest completions go through [complete] so XP, level-ups and
 * undo work the same as a tap on the quest.
 */
class ReportViewModel(
    private val life: LifeRepository,
    private val health: HealthRepository,
    private val focus: FocusRepository,
    private val prefs: SharedPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow<ReportState?>(null)

    /** Null while the report is closed. */
    val state: StateFlow<ReportState?> = _state.asStateFlow()

    /**
     * Opens the report when questions are due. [force] (the user asked for it) asks every
     * question regardless of snoozes and times, and opens straight to the verdict when there
     * is nothing to ask.
     */
    fun open(force: Boolean = false) {
        if (_state.value != null) return
        viewModelScope.launch {
            val now = life.clock.instant()
            val open = life.observeDay(life.today()).first().filter { !it.isDone }.map { it.quest to it.check }
            val todayMeals = health.observeMeals(1).first()
            val items = GuidedReport.due(open, todayMeals.maxOfOrNull { it.at }, now, life.dayBoundary, snoozed(now), force)
            if (items.isEmpty() && !force) return@launch
            if (_state.value != null) return@launch
            _state.value = ReportState(
                items = items,
                quickFoods = HealthLog.quickFoods(health.observeMeals(FOOD_HISTORY_DAYS).first()),
                lastMeal = todayMeals.maxOfOrNull { it.at },
            )
            if (items.isEmpty()) finish()
        }
    }

    fun logMeal(food: String) {
        val name = food.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            health.logMeal(name)
            focus.record(FocusEventKind.REPORTED)
            _state.value = _state.value?.let { it.copy(mealsNow = it.mealsNow + name, lastMeal = life.clock.instant()) }
        }
    }

    /** "That's all" or "Nothing since": the meal question is done for a few hours. */
    fun mealsDone() {
        snooze(ReportItem.Meal.key, GuidedReport.MEAL_SNOOZE)
        next(answered = true)
    }

    /** The current quest was completed (through the game, by the caller). */
    fun answered(quest: Quest) {
        viewModelScope.launch { focus.record(FocusEventKind.REPORTED, quest.id) }
        _state.value = _state.value?.let { it.copy(completed = it.completed + quest.id) }
        next(answered = true)
    }

    /** "Not yet": put off for an hour, and on the record. */
    fun notYet() {
        val item = _state.value?.current ?: return
        snooze(item.key, GuidedReport.SNOOZE)
        val questId = (item as? ReportItem.Amount)?.quest?.id ?: (item as? ReportItem.Confirm)?.quest?.id
        viewModelScope.launch { focus.record(FocusEventKind.DODGED, questId) }
        next(answered = false)
    }

    fun close() {
        _state.value = null
    }

    private fun next(answered: Boolean) {
        val state = _state.value ?: return
        val index = state.index + 1
        _state.value = state.copy(index = index, mealsNow = emptyList(), answered = state.answered + if (answered) 1 else 0)
        if (index >= state.items.size) finish()
    }

    private fun finish() {
        viewModelScope.launch {
            val done = _state.value?.completed.orEmpty()
            val open = life.openToday().map { it.quest }.filter { it.id !in done }
            _state.value = _state.value?.copy(openAfter = open)
        }
    }

    private fun snoozed(now: Instant): Set<String> = prefs.all
        .filter { (key, value) -> key.startsWith(SNOOZE_PREFIX) && (value as? Long ?: 0L) > now.toEpochMilli() }
        .keys.map { it.removePrefix(SNOOZE_PREFIX) }.toSet()

    private fun snooze(key: String, length: Duration) {
        prefs.edit().putLong(SNOOZE_PREFIX + key, life.clock.instant().plus(length).toEpochMilli()).apply()
    }

    private companion object {
        const val SNOOZE_PREFIX = "snooze:"
        const val FOOD_HISTORY_DAYS = 30L
    }
}
