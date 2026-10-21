package app.selfup.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.GoalMeasure
import app.selfup.core.ImportedGoal
import app.selfup.core.Metric
import app.selfup.core.MindGame
import app.selfup.core.RecordKind
import app.selfup.core.packs.Packs
import app.selfup.data.AddGoalResult
import app.selfup.data.GoalsOverview
import app.selfup.data.GoalsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface GoalsEvent {
    data class Added(val title: String, val queued: Boolean) : GoalsEvent
    data class Imported(val active: Int, val queued: Int) : GoalsEvent
}

class GoalsViewModel(private val goals: GoalsRepository) : ViewModel() {
    val state: StateFlow<GoalsOverview> = goals.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsOverview(emptyList(), goals.today()))

    private val _events = Channel<GoalsEvent>(Channel.BUFFERED)
    val events: Flow<GoalsEvent> = _events.receiveAsFlow()

    fun today(): LocalDate = goals.today()

    suspend fun best(measure: GoalMeasure): Double? = goals.best(measure)

    fun add(template: GoalTemplate, target: Double, deadline: LocalDate) {
        viewModelScope.launch {
            val result = goals.add(template.measure, target, deadline)
            _events.send(GoalsEvent.Added(template.name, queued = result is AddGoalResult.Queued))
        }
    }

    fun import(imported: List<ImportedGoal>) {
        viewModelScope.launch {
            val results = goals.import(imported)
            val queued = results.count { it is AddGoalResult.Queued }
            _events.send(GoalsEvent.Imported(active = results.size - queued, queued = queued))
        }
    }

    fun moveDeadline(id: Long, deadline: LocalDate) = launch { goals.moveDeadline(id, deadline) }

    fun changeTarget(id: Long, target: Double) = launch { goals.changeTarget(id, target) }

    fun markReached(id: Long) = launch { goals.markReached(id) }

    fun drop(id: Long) = launch { goals.drop(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * A goal the add sheet offers: its measure, how the target steps, and a sensible first target
 * from your best so far. Body weight has no better direction, so it suggests no change.
 */
data class GoalTemplate(
    val name: String,
    val group: String,
    val measure: GoalMeasure,
    val step: Double,
    val fallback: Double,
    /** Added to (or, for lower-is-better, taken from) your best to suggest a target. */
    val stretch: Double,
    val min: Double = step,
    val max: Double = Double.MAX_VALUE,
) {
    fun suggest(best: Double?): Double {
        val base = best ?: fallback
        val lower = measure.better == Metric.Better.LOWER
        val raw = when {
            measure.better == null -> base - stretch
            lower -> base - stretch
            else -> base + stretch
        }
        return (Math.round(raw / step) * step).coerceIn(min, max)
    }

    companion object {
        val all: List<GoalTemplate> = buildList {
            listOf(RecordKind.BENCH to 60.0, RecordKind.SQUAT to 80.0, RecordKind.DEADLIFT to 100.0, RecordKind.OVERHEAD_PRESS to 40.0)
                .forEach { (kind, fallback) ->
                    add(GoalTemplate(kind.displayName, "Lift", GoalMeasure.Record(kind, Metric.EST_1RM), step = 2.5, fallback = fallback, stretch = 10.0))
                }
            add(GoalTemplate("Pull-ups", "Body", GoalMeasure.Record(RecordKind.PULL_UPS, Metric.REPS), step = 1.0, fallback = 5.0, stretch = 5.0))
            add(GoalTemplate("Push-ups", "Body", GoalMeasure.Record(RecordKind.PUSH_UPS, Metric.REPS), step = 1.0, fallback = 20.0, stretch = 15.0))
            add(GoalTemplate("Plank", "Body", GoalMeasure.Record(RecordKind.PLANK, Metric.HOLD), step = 5.0, fallback = 60.0, stretch = 60.0))
            add(GoalTemplate("Body weight", "Body", GoalMeasure.Record(RecordKind.BODY_WEIGHT, Metric.BODY_WEIGHT), step = 0.5, fallback = 70.0, stretch = 2.0, min = 30.0))
            add(GoalTemplate("Run distance", "Run", GoalMeasure.Record(RecordKind.RUN, Metric.DISTANCE), step = 0.5, fallback = 3.0, stretch = 5.0, max = 50.0))
            add(GoalTemplate("Run pace", "Run", GoalMeasure.Record(RecordKind.RUN, Metric.PACE), step = 0.05, fallback = 7.0, stretch = 0.75, min = 3.0))
            Packs.all.forEach { pack ->
                add(GoalTemplate(pack.name, "Skill", GoalMeasure.Skill(pack.id), step = 1.0, fallback = 0.0, stretch = 10.0, min = 1.0, max = pack.concepts.size.toDouble()))
            }
            MindGame.entries.forEach { game ->
                val (step, fallback, stretch) = when (game) {
                    MindGame.DIGIT_SPAN -> Triple(1.0, 5.0, 2.0)
                    MindGame.N_BACK -> Triple(1.0, 1.0, 1.0)
                    MindGame.SPEED_MATH -> Triple(1.0, 15.0, 10.0)
                    MindGame.GRID_RECALL -> Triple(1.0, 5.0, 3.0)
                    MindGame.REACTION -> Triple(5.0, 300.0, 40.0)
                }
                add(GoalTemplate(game.displayName, "Mind", GoalMeasure.Mind(game), step = step, fallback = fallback, stretch = stretch, min = if (game == MindGame.REACTION) 120.0 else 1.0))
            }
        }

        val groups: List<String> get() = all.map { it.group }.distinct()
    }
}
