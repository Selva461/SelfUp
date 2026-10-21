package app.selfup.core

import app.selfup.core.packs.Packs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** What a goal measures. Each one resolves to a single number from the app's own data. */
public sealed interface GoalMeasure {
    public val stat: Stat
    public val unit: String

    /** Persisted form, such as "record:bench:EST_1RM", "skill:java" or "mind:digit_span". */
    public val key: String

    /** Direction when the baseline doesn't settle it (body weight can go either way). */
    public val better: Metric.Better?

    public data class Record(val kind: RecordKind, val metric: Metric) : GoalMeasure {
        init {
            require(metric in kind.metrics) { "${kind.displayName} has no ${metric.displayName}" }
        }

        override val stat: Stat get() = kind.stat
        override val unit: String get() = metric.unit
        override val key: String get() = "record:${kind.key}:${metric.name}"
        override val better: Metric.Better? get() = metric.better
    }

    /** Concepts of a pack at Solid or above. */
    public data class Skill(val packId: String) : GoalMeasure {
        public val pack: SkillPack get() = requireNotNull(Packs.pack(packId)) { "Unknown pack $packId" }
        override val stat: Stat get() = pack.stat
        override val unit: String get() = "concepts"
        override val key: String get() = "skill:$packId"
        override val better: Metric.Better get() = Metric.Better.HIGHER
    }

    public data class Mind(val game: MindGame) : GoalMeasure {
        override val stat: Stat get() = Stat.TECHNICAL
        override val unit: String get() = game.unit
        override val key: String get() = "mind:${game.key}"
        override val better: Metric.Better get() = game.better
    }

    public companion object {
        public fun fromKey(key: String): GoalMeasure {
            val parts = key.split(':')
            return when (parts.first()) {
                "record" -> Record(RecordKind.fromKey(parts[1]), Metric.valueOf(parts[2]))
                "skill" -> Skill(parts[1])
                "mind" -> Mind(MindGame.fromKey(parts[1]))
                else -> throw IllegalArgumentException("Unknown goal measure: $key")
            }
        }
    }
}

/** A number to reach by a date. [baseline] is null until the first test is taken. */
public data class Goal(
    val id: Long = 0,
    val measure: GoalMeasure,
    val target: Double,
    val start: LocalDate,
    val deadline: LocalDate,
    val baseline: Double? = null,
    /** A written plan (from a plan code) that replaces the built-in training tasks. */
    val plan: WeeklyPlan? = null,
) {
    init {
        require(deadline.isAfter(start)) { "The deadline must be after the start" }
    }

    /** Whether the goal is to push the number up; from the baseline when known. */
    public val rising: Boolean
        get() = baseline?.let { target >= it } ?: (measure.better != Metric.Better.LOWER)

    public fun reached(value: Double): Boolean = if (rising) value >= target else value <= target
}

public enum class GoalStatus(public val displayName: String) {
    NEEDS_BASELINE("Needs a first test"),
    AHEAD("Ahead"),
    ON_TRACK("On track"),
    BEHIND("Behind"),
    REACHED("Reached"),
    MISSED("Deadline passed"),
}

public object GoalRules {
    /** Being good at everything at once is how these apps get abandoned; more goals wait in a queue. */
    public const val MAX_ACTIVE: Int = 3
    public const val TASKS_PER_DAY: Int = 2

    /** Every 4th week is a test week. */
    public const val CHECKPOINT_EVERY: Int = 4

    /** How far from the expected line still counts as on track, as a share of the whole gap. */
    public const val TOLERANCE: Double = 0.10

    /** A record this recent can stand in for the baseline test. */
    public const val BASELINE_FRESH_DAYS: Long = 14

    public fun canAdd(activeCount: Int): Boolean = activeCount < MAX_ACTIVE

    public fun totalWeeks(goal: Goal): Int = ceil(days(goal.start, goal.deadline) / 7.0).toInt().coerceAtLeast(1)

    public fun weekIndex(goal: Goal, date: LocalDate): Int = (days(goal.start, date) / 7).toInt().coerceAtLeast(0)

    /** Checkpoint weeks: every 4th week, and always the final week. */
    public fun isCheckpointWeek(goal: Goal, date: LocalDate): Boolean {
        val week = weekIndex(goal, date)
        return (week + 1) % CHECKPOINT_EVERY == 0 || week == totalWeeks(goal) - 1
    }

    /** Where the number should be on [date] if progress were a straight line from baseline to target. */
    public fun expected(goal: Goal, date: LocalDate): Double? {
        val baseline = goal.baseline ?: return null
        val fraction = (days(goal.start, date).toDouble() / days(goal.start, goal.deadline)).coerceIn(0.0, 1.0)
        return baseline + (goal.target - baseline) * fraction
    }

    /** Share of the gap closed, 0 to 1. */
    public fun progress(goal: Goal, current: Double?): Double {
        val baseline = goal.baseline ?: return 0.0
        if (current == null) return 0.0
        val gap = goal.target - baseline
        if (abs(gap) < 1e-9) return 1.0
        return ((current - baseline) / gap).coerceIn(0.0, 1.0)
    }

    public fun status(goal: Goal, current: Double?, today: LocalDate): GoalStatus {
        val baseline = goal.baseline ?: return GoalStatus.NEEDS_BASELINE
        val value = current ?: baseline
        if (goal.reached(value)) return GoalStatus.REACHED
        if (today.isAfter(goal.deadline)) return GoalStatus.MISSED
        val expected = requireNotNull(expected(goal, today))
        val tolerance = abs(goal.target - baseline) * TOLERANCE
        val lead = if (goal.rising) value - expected else expected - value
        return when {
            lead > tolerance -> GoalStatus.AHEAD
            lead < -tolerance -> GoalStatus.BEHIND
            else -> GoalStatus.ON_TRACK
        }
    }

    /**
     * When behind: the deadline that the pace so far would reach, or null when there's been no
     * progress to extrapolate from. Never earlier than the current deadline.
     */
    public fun projectedDeadline(goal: Goal, current: Double?, today: LocalDate): LocalDate? {
        val baseline = goal.baseline ?: return null
        val value = current ?: return null
        val elapsed = days(goal.start, today)
        val gained = if (goal.rising) value - baseline else baseline - value
        if (elapsed <= 0 || gained <= 0) return null
        val remaining = abs(goal.target - value)
        val projected = today.plusDays(ceil(remaining / (gained / elapsed)).toLong())
        return maxOf(projected, goal.deadline)
    }

    /** When ahead: a new target the same pace would reach by the deadline, rounded to the measure's step. */
    public fun raisedTarget(goal: Goal, current: Double?, today: LocalDate): Double? {
        val baseline = goal.baseline ?: return null
        val value = current ?: return null
        val elapsed = days(goal.start, today)
        if (elapsed <= 0) return null
        val rate = (value - baseline) / elapsed
        val raised = round(goal.measure, value + rate * days(today, goal.deadline))
        return raised.takeIf { if (goal.rising) it > goal.target else it < goal.target }
    }

    public fun round(measure: GoalMeasure, value: Double): Double = when {
        measure is GoalMeasure.Record && measure.kind.input == RecordInput.WEIGHT_REPS -> (value / 2.5).roundToInt() * 2.5
        measure is GoalMeasure.Record && measure.metric == Metric.PACE -> (value * 20).roundToInt() / 20.0
        measure is GoalMeasure.Record && measure.metric in setOf(Metric.DISTANCE, Metric.BODY_WEIGHT) -> (value * 10).roundToInt() / 10.0
        else -> value.roundToInt().toDouble()
    }

    private fun days(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
}

/** What a task asks for, so the screen can open the right place and see when it's done. */
public sealed interface PlanAction {
    public data class LogRecord(val kind: RecordKind) : PlanAction
    public data class Study(val packId: String) : PlanAction
    public data object MindSession : PlanAction
}

/** One thing to do today toward a goal. */
public data class PlanTask(
    val goalId: Long,
    val title: String,
    val detail: String,
    val action: PlanAction,
    /** A test of the goal's measure: the baseline, or a checkpoint. */
    val isTest: Boolean = false,
)

/**
 * Built-in plans. Each goal gets at most [GoalRules.TASKS_PER_DAY] tasks a day, fitted to a
 * weekday office schedule with the gym in the evening.
 */
public object GoalPlanner {
    /** Lift days for a lift goal: two sessions a week on the main lift. */
    public val LIFT_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)

    /** Run days: two short runs and a longer one at the weekend. */
    public val RUN_DAYS: Map<DayOfWeek, Double> = mapOf(DayOfWeek.TUESDAY to 0.3, DayOfWeek.THURSDAY to 0.3, DayOfWeek.SATURDAY to 0.4)

    /** Weekly running volume grows by at most this much (a common guideline, unverified as a hard rule). */
    public const val RUN_GROWTH: Double = 0.10

    /** Checkpoint retests happen on Saturday, when there's no office. */
    public val TEST_DAY: DayOfWeek = DayOfWeek.SATURDAY

    /** Reps the lift plan prescribes for its top set; Epley is most reliable at 10 or fewer. */
    public const val TOP_SET_REPS: Int = 5

    /** A lighter week every 4th week, at this share of the planned load. */
    public const val DELOAD: Double = 0.9

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM")

    public fun title(goal: Goal): String {
        val target = format(goal.measure, goal.target)
        val by = goal.deadline.format(dateFormat)
        return when (val m = goal.measure) {
            is GoalMeasure.Record -> when (m.metric) {
                Metric.EST_1RM -> "${m.kind.displayName} $target by $by"
                Metric.PACE -> "Run at $target by $by"
                Metric.DISTANCE -> "Run $target by $by"
                Metric.BODY_WEIGHT -> "Weigh $target by $by"
                else -> "${m.kind.displayName} $target by $by"
            }
            is GoalMeasure.Skill -> "${m.pack.name}: $target solid by $by"
            is GoalMeasure.Mind -> "${m.game.displayName} $target by $by"
        }
    }

    public fun format(measure: GoalMeasure, value: Double): String {
        val number = if (value == value.toInt().toDouble()) value.toInt().toString() else "%.1f".format(value)
        return when {
            measure is GoalMeasure.Record && measure.metric == Metric.PACE -> {
                val minutes = value.toInt()
                val seconds = ((value - minutes) * 60).roundToInt()
                "$minutes:${seconds.toString().padStart(2, '0')} min/km"
            }
            measure is GoalMeasure.Mind && measure.game == MindGame.N_BACK -> "$number-back"
            else -> "$number ${measure.unit}"
        }
    }

    /** Today's tasks for [goal], given its [current] value. */
    public fun tasks(goal: Goal, current: Double?, date: LocalDate): List<PlanTask> {
        if (date.isBefore(goal.start) || date.isAfter(goal.deadline)) return emptyList()
        val action = actionFor(goal.measure)
        if (goal.baseline == null) {
            return listOf(PlanTask(goal.id, "First test: ${testName(goal.measure)}", "Measure where you are now so the plan has a real start.", action, isTest = true))
        }
        val tasks = mutableListOf<PlanTask>()
        val checkpoint = GoalRules.isCheckpointWeek(goal, date)
        if (checkpoint && date.dayOfWeek == TEST_DAY) {
            val expected = GoalRules.expected(goal, date)?.let { format(goal.measure, GoalRules.round(goal.measure, it)) }
            tasks += PlanTask(goal.id, "Checkpoint: ${testName(goal.measure)}", "Week ${GoalRules.weekIndex(goal, date) + 1} test. On the line: $expected.", action, isTest = true)
        }
        val written = goal.plan
        tasks += if (written != null) {
            written.on(date.dayOfWeek).map { PlanTask(goal.id, it.title, it.detail, action) }
        } else {
            training(goal, current, date, checkpoint)
        }
        return tasks.take(GoalRules.TASKS_PER_DAY)
    }

    private fun training(goal: Goal, current: Double?, date: LocalDate, checkpoint: Boolean): List<PlanTask> {
        val m = goal.measure
        val action = actionFor(m)
        return when (m) {
            is GoalMeasure.Record -> when (m.kind.input) {
                RecordInput.WEIGHT_REPS -> {
                    if (date.dayOfWeek !in LIFT_DAYS) return emptyList()
                    // Aim the top set at where the line says you'll be by the end of this week.
                    val weekEnd = minOf(date.plusDays(6L - (date.dayOfWeek.value - 1)), goal.deadline)
                    val aim = requireNotNull(GoalRules.expected(goal, weekEnd))
                    val load = if (checkpoint) DELOAD else 1.0
                    val weight = GoalRules.round(m, aim / (1 + TOP_SET_REPS / 30.0) * load)
                    val detail = if (checkpoint) "Lighter week before the test." else "Log the top set when you're done."
                    listOf(PlanTask(goal.id, "${m.kind.displayName}: top set ${format(m, weight)} × $TOP_SET_REPS", detail, action))
                }
                RecordInput.DISTANCE_TIME -> {
                    val share = RUN_DAYS[date.dayOfWeek] ?: return emptyList()
                    val weekly = runWeeklyKm(goal, date)
                    val km = (weekly * share * 10).roundToInt() / 10.0
                    val pace = if (m.metric == Metric.PACE) GoalRules.expected(goal, date)?.let { " at ${format(m, GoalRules.round(m, it))}" } else null
                    val easy = if (date.dayOfWeek == DayOfWeek.SATURDAY) "Long run" else "Run"
                    listOf(PlanTask(goal.id, "$easy ${"%.1f".format(km)} km${pace.orEmpty()}", "This week: ${"%.1f".format(weekly)} km in total.", action))
                }
                RecordInput.REPS, RecordInput.SECONDS -> {
                    if (date.dayOfWeek !in LIFT_DAYS) return emptyList()
                    val aim = GoalRules.round(m, requireNotNull(GoalRules.expected(goal, date)))
                    val sets = if (checkpoint) 2 else 3
                    listOf(PlanTask(goal.id, "${m.kind.displayName}: $sets sets near ${format(m, aim)}", "Log your best set.", action))
                }
                RecordInput.WEIGHT -> if (date.dayOfWeek == DayOfWeek.MONDAY) {
                    listOf(PlanTask(goal.id, "Weigh in", "Same time, before breakfast.", action))
                } else {
                    emptyList()
                }
            }
            is GoalMeasure.Skill -> {
                val left = (goal.target - (current ?: goal.baseline ?: 0.0)).coerceAtLeast(0.0)
                listOf(PlanTask(goal.id, "${m.pack.name} session", "${left.toInt()} more concepts to make solid. Reviews first, then one new.", action))
            }
            is GoalMeasure.Mind -> listOf(PlanTask(goal.id, "Mind session", "10 minutes. ${m.game.displayName} is the one that counts for this goal.", action))
        }
    }

    /**
     * Weekly running volume: start from the baseline's longest run times two, grow by at most
     * [RUN_GROWTH] a week, and ease off in checkpoint weeks.
     */
    public fun runWeeklyKm(goal: Goal, date: LocalDate): Double {
        val m = goal.measure as GoalMeasure.Record
        val startKm = if (m.metric == Metric.DISTANCE) (goal.baseline ?: 3.0) * 2 else 10.0
        val week = GoalRules.weekIndex(goal, date)
        val grown = startKm * Math.pow(1 + RUN_GROWTH, week.toDouble())
        val capped = if (m.metric == Metric.DISTANCE) minOf(grown, goal.target * 2.5) else grown
        return if (GoalRules.isCheckpointWeek(goal, date)) capped * 0.8 else capped
    }

    private fun testName(measure: GoalMeasure): String = when (measure) {
        is GoalMeasure.Record -> when (measure.metric) {
            Metric.EST_1RM -> "${measure.kind.displayName.lowercase()} top set of 3 to 5 reps"
            Metric.PACE -> "timed 5 km"
            Metric.DISTANCE -> "longest easy run"
            Metric.BODY_WEIGHT -> "weigh in"
            else -> "${measure.kind.displayName.lowercase()} max"
        }
        is GoalMeasure.Skill -> "${measure.pack.name} session"
        is GoalMeasure.Mind -> measure.game.displayName.lowercase()
    }

    private fun actionFor(measure: GoalMeasure): PlanAction = when (measure) {
        is GoalMeasure.Record -> PlanAction.LogRecord(measure.kind)
        is GoalMeasure.Skill -> PlanAction.Study(measure.packId)
        is GoalMeasure.Mind -> PlanAction.MindSession
    }
}
