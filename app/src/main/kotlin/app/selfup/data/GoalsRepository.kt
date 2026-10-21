package app.selfup.data

import androidx.room.withTransaction
import app.selfup.core.DayBoundary
import app.selfup.core.Goal
import app.selfup.core.GoalMeasure
import app.selfup.core.GoalPlanner
import app.selfup.core.GoalRules
import app.selfup.core.GoalStatus
import app.selfup.core.Mastery
import app.selfup.core.Metric
import app.selfup.core.PlanAction
import app.selfup.core.PlanTask
import app.selfup.core.RecordEntry
import app.selfup.core.RecordMath
import app.selfup.core.SkillRules
import app.selfup.core.packs.Packs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** A goal as the screens show it: where it stands and what today asks. */
data class GoalView(
    val goal: Goal,
    val state: String,
    /** The best value since the goal started (latest, for trend measures like body weight). */
    val current: Double?,
    val status: GoalStatus,
    val progress: Double,
    val expected: Double?,
    val tasks: List<TodayTask>,
    /** Behind: the deadline today's pace would hit. Ahead: a target worth raising to. */
    val projectedDeadline: LocalDate?,
    val raisedTarget: Double?,
) {
    val title: String get() = GoalPlanner.title(goal)
    val isActive: Boolean get() = state == GoalState.ACTIVE
}

data class TodayTask(val task: PlanTask, val done: Boolean)

data class GoalsOverview(val goals: List<GoalView>, val today: LocalDate) {
    val active: List<GoalView> get() = goals.filter { it.isActive }
    val queued: List<GoalView> get() = goals.filter { it.state == GoalState.QUEUED }
    val finished: List<GoalView> get() = goals.filter { it.state == GoalState.REACHED }
    val todayTasks: List<TodayTask> get() = active.flatMap { it.tasks }
}

sealed interface AddGoalResult {
    data class Active(val id: Long) : AddGoalResult
    data class Queued(val id: Long) : AddGoalResult
}

/**
 * Goals and their plans. Nothing here is logged twice: a task is done when the thing it asks
 * for shows up in records, skill events or mind scores for today.
 */
class GoalsRepository(
    private val db: SelfUpDatabase,
    private val clock: Clock,
    private val dayBoundary: DayBoundary,
) {
    private val goals = db.goalDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    fun observe(): Flow<GoalsOverview> = combine(
        goals.observeAll(),
        db.recordDao().observeAll(),
        db.skillDao().observeAll(),
        db.mindDao().observeAll(),
    ) { goalRows, records, skills, minds ->
        val today = today()
        val data = Measurements(
            records = records.map { it.toDomain() to dayBoundary.questDay(Instant.ofEpochMilli(it.atMillis)) },
            skills = skills,
            minds = minds,
        )
        GoalsOverview(goalRows.filter { it.state != GoalState.DROPPED }.map { view(it, data, today) }, today)
    }

    /** Adds a goal; it's active if fewer than three are, otherwise it waits in the queue. */
    suspend fun add(measure: GoalMeasure, target: Double, deadline: LocalDate): AddGoalResult = db.withTransaction {
        val today = today()
        val active = goals.withState(GoalState.ACTIVE).size
        val baseline = freshBaseline(measure, today)
        val state = if (GoalRules.canAdd(active)) GoalState.ACTIVE else GoalState.QUEUED
        val id = goals.insert(
            GoalEntity(
                measure = measure.key,
                target = target,
                baseline = baseline,
                startEpochDay = today.toEpochDay(),
                deadlineEpochDay = deadline.toEpochDay(),
                state = state,
                createdAtMillis = clock.millis(),
            ),
        )
        if (state == GoalState.ACTIVE) AddGoalResult.Active(id) else AddGoalResult.Queued(id)
    }

    suspend fun moveDeadline(id: Long, deadline: LocalDate) = edit(id) { it.copy(deadlineEpochDay = deadline.toEpochDay()) }

    suspend fun changeTarget(id: Long, target: Double) = edit(id) { it.copy(target = target) }

    suspend fun markReached(id: Long) = finish(id, GoalState.REACHED)

    suspend fun drop(id: Long) = finish(id, GoalState.DROPPED)

    private suspend fun edit(id: Long, change: (GoalEntity) -> GoalEntity) = db.withTransaction {
        goals.get(id)?.let { goals.update(change(it)) }
    }

    /** Ends a goal and starts the oldest queued one, which begins today. */
    private suspend fun finish(id: Long, state: String) = db.withTransaction {
        val row = goals.get(id) ?: return@withTransaction
        goals.update(row.copy(state = state))
        if (row.state != GoalState.ACTIVE) return@withTransaction
        val next = goals.withState(GoalState.QUEUED).firstOrNull() ?: return@withTransaction
        val today = today()
        val length = next.deadlineEpochDay - next.startEpochDay
        val measure = GoalMeasure.fromKey(next.measure)
        goals.update(
            next.copy(
                state = GoalState.ACTIVE,
                startEpochDay = today.toEpochDay(),
                deadlineEpochDay = today.toEpochDay() + length,
                baseline = freshBaseline(measure, today),
            ),
        )
    }

    /** The best value ever recorded for [measure], to suggest a target. */
    suspend fun best(measure: GoalMeasure): Double? = when (measure) {
        is GoalMeasure.Record -> {
            val entries = db.recordDao().history(measure.kind.key).map { it.toDomain() }
            if (measure.metric.better == null) {
                entries.lastOrNull()?.let { RecordMath.metrics(it)[measure.metric] }
            } else {
                RecordMath.bests(entries)[measure.metric]
            }
        }
        is GoalMeasure.Skill -> freshBaseline(measure, today())
        is GoalMeasure.Mind -> db.mindDao().forGame(measure.game.key).map { it.score }
            .reduceOrNull { a, b -> if (measure.game.isBetter(b, a)) b else a }
    }

    /** A measurement recent enough to count as the baseline, so the first test can be skipped. */
    private suspend fun freshBaseline(measure: GoalMeasure, today: LocalDate): Double? {
        val since = today.minusDays(GoalRules.BASELINE_FRESH_DAYS)
        return when (measure) {
            is GoalMeasure.Record -> db.recordDao().history(measure.kind.key)
                .filter { !dayBoundary.questDay(Instant.ofEpochMilli(it.atMillis)).isBefore(since) }
                .lastOrNull()
                ?.let { RecordMath.metrics(it.toDomain())[measure.metric] }
            is GoalMeasure.Skill -> {
                val states = db.skillDao().all().groupBy { it.conceptId }
                    .mapValues { (_, events) -> SkillRules.replay(events.map { it.toDomain() }) }
                solidCount(measure, states.mapValues { it.value.mastery }).toDouble()
            }
            is GoalMeasure.Mind -> db.mindDao().forGame(measure.game.key)
                .lastOrNull { it.epochDay >= since.toEpochDay() }
                ?.score
        }
    }

    private class Measurements(
        val records: List<Pair<RecordEntry, LocalDate>>,
        val skills: List<SkillEventEntity>,
        val minds: List<MindScoreEntity>,
    ) {
        val mastery: Map<String, Mastery> by lazy {
            skills.groupBy { it.conceptId }.mapValues { (_, events) -> SkillRules.replay(events.map { it.toDomain() }).mastery }
        }
    }

    private fun view(row: GoalEntity, data: Measurements, today: LocalDate): GoalView {
        val measure = GoalMeasure.fromKey(row.measure)
        val start = LocalDate.ofEpochDay(row.startEpochDay)
        val values = valuesSince(measure, start, data)
        // No stored baseline: the first measurement since the start is it.
        val baseline = row.baseline ?: values.firstOrNull()
        val goal = Goal(
            id = row.id,
            measure = measure,
            target = row.target,
            start = start,
            deadline = LocalDate.ofEpochDay(row.deadlineEpochDay),
            baseline = baseline,
        )
        val current = current(goal, values) ?: baseline
        val status = GoalRules.status(goal, current, today)
        val tasks = if (row.state == GoalState.ACTIVE) {
            GoalPlanner.tasks(goal, current, today).map { TodayTask(it, isDone(it, measure, data, today)) }
        } else {
            emptyList()
        }
        return GoalView(
            goal = goal,
            state = row.state,
            current = current,
            status = status,
            progress = GoalRules.progress(goal, current),
            expected = GoalRules.expected(goal, today),
            tasks = tasks,
            projectedDeadline = if (status == GoalStatus.BEHIND) GoalRules.projectedDeadline(goal, current, today) else null,
            raisedTarget = if (status == GoalStatus.AHEAD) GoalRules.raisedTarget(goal, current, today) else null,
        )
    }

    /** The measure's values since [start], oldest first. A skill has one value: today's count. */
    private fun valuesSince(measure: GoalMeasure, start: LocalDate, data: Measurements): List<Double> = when (measure) {
        is GoalMeasure.Record -> data.records
            .filter { (entry, day) -> entry.kind == measure.kind && !day.isBefore(start) }
            .mapNotNull { (entry, _) -> RecordMath.metrics(entry)[measure.metric] }
        is GoalMeasure.Skill -> listOf(solidCount(measure, data.mastery).toDouble())
        is GoalMeasure.Mind -> data.minds
            .filter { it.game == measure.game.key && it.epochDay >= start.toEpochDay() }
            .map { it.score }
    }

    private fun current(goal: Goal, values: List<Double>): Double? {
        if (values.isEmpty()) return null
        return when (goal.measure.better) {
            null -> values.last()
            Metric.Better.HIGHER -> values.max()
            Metric.Better.LOWER -> values.min()
        }
    }

    private fun isDone(task: PlanTask, measure: GoalMeasure, data: Measurements, today: LocalDate): Boolean = when (val action = task.action) {
        is PlanAction.LogRecord -> data.records.any { (entry, day) -> entry.kind == action.kind && day == today }
        is PlanAction.Study -> {
            val ids = Packs.pack(action.packId)?.concepts?.map { it.id }?.toSet().orEmpty()
            data.skills.any { it.epochDay == today.toEpochDay() && it.kind == SkillEventKind.QUIZ && it.conceptId in ids }
        }
        PlanAction.MindSession -> {
            val game = (measure as? GoalMeasure.Mind)?.game
            data.minds.any { it.epochDay == today.toEpochDay() && (game == null || it.game == game.key) }
        }
    }

    private fun solidCount(measure: GoalMeasure.Skill, mastery: Map<String, Mastery>): Int =
        measure.pack.concepts.count { (mastery[it.id] ?: Mastery.UNSEEN).level >= Mastery.SOLID.level }
}
