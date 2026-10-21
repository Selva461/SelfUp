package app.selfup.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The shape of one record entry, which decides the logging sheet. */
public enum class RecordInput { WEIGHT_REPS, REPS, SECONDS, DISTANCE_TIME, WEIGHT }

/** Something measurable about the body. [key] is persisted, like [Stat.key]. */
public enum class RecordKind(
    public val key: String,
    public val displayName: String,
    public val input: RecordInput,
    public val stat: Stat,
) {
    BENCH("bench", "Bench press", RecordInput.WEIGHT_REPS, Stat.STRENGTH),
    SQUAT("squat", "Squat", RecordInput.WEIGHT_REPS, Stat.STRENGTH),
    DEADLIFT("deadlift", "Deadlift", RecordInput.WEIGHT_REPS, Stat.STRENGTH),
    OVERHEAD_PRESS("ohp", "Overhead press", RecordInput.WEIGHT_REPS, Stat.STRENGTH),
    PULL_UPS("pull_ups", "Pull-ups", RecordInput.REPS, Stat.STRENGTH),
    PUSH_UPS("push_ups", "Push-ups", RecordInput.REPS, Stat.STRENGTH),
    PLANK("plank", "Plank", RecordInput.SECONDS, Stat.STRENGTH),
    RUN("run", "Run", RecordInput.DISTANCE_TIME, Stat.STRENGTH),
    BODY_WEIGHT("body_weight", "Body weight", RecordInput.WEIGHT, Stat.NUTRITION),
    ;

    /** The metrics an entry of this kind produces; the first is the headline number. */
    public val metrics: List<Metric>
        get() = when (input) {
            RecordInput.WEIGHT_REPS -> listOf(Metric.EST_1RM)
            RecordInput.REPS -> listOf(Metric.REPS)
            RecordInput.SECONDS -> listOf(Metric.HOLD)
            RecordInput.DISTANCE_TIME -> listOf(Metric.DISTANCE, Metric.PACE)
            RecordInput.WEIGHT -> listOf(Metric.BODY_WEIGHT)
        }

    public companion object {
        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String): RecordKind =
            byKey[key] ?: throw IllegalArgumentException("Unknown record key: $key")
    }
}

/**
 * A number derived from entries. [better] is null for metrics that are tracked as a trend
 * rather than beaten, such as body weight during a recomposition.
 */
public enum class Metric(public val displayName: String, public val unit: String, public val better: Better?) {
    EST_1RM("Estimated 1RM", "kg", Better.HIGHER),
    REPS("Max reps", "reps", Better.HIGHER),
    HOLD("Longest hold", "s", Better.HIGHER),
    DISTANCE("Longest run", "km", Better.HIGHER),
    PACE("Best pace", "min/km", Better.LOWER),
    BODY_WEIGHT("Body weight", "kg", null),
    ;

    public enum class Better { HIGHER, LOWER }

    public fun isBetter(candidate: Double, best: Double): Boolean = when (better) {
        Better.HIGHER -> candidate > best + EPSILON
        Better.LOWER -> candidate < best - EPSILON
        null -> false
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}

/** One logged measurement. Only the fields [kind]'s input needs are set. */
public data class RecordEntry(
    val kind: RecordKind,
    val at: Instant,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distanceKm: Double? = null,
) {
    init {
        when (kind.input) {
            RecordInput.WEIGHT_REPS -> {
                require(weightKg != null && weightKg > 0) { "${kind.displayName} needs a weight" }
                require(reps != null && reps in 1..RecordMath.MAX_REPS) { "Reps must be 1 to ${RecordMath.MAX_REPS}" }
            }
            RecordInput.REPS -> require(reps != null && reps > 0) { "${kind.displayName} needs reps" }
            RecordInput.SECONDS -> require(seconds != null && seconds > 0) { "${kind.displayName} needs a time" }
            RecordInput.DISTANCE_TIME -> {
                require(distanceKm != null && distanceKm > 0) { "A run needs a distance" }
                require(seconds != null && seconds > 0) { "A run needs a time" }
            }
            RecordInput.WEIGHT -> require(weightKg != null && weightKg > 0) { "Body weight needs a weight" }
        }
    }
}

/** A metric that a new entry beat. [previous] is the best before it. */
public data class PersonalBest(val metric: Metric, val previous: Double, val value: Double)

public object RecordMath {
    /** Above this many reps a 1RM estimate means little, so entry stops there. */
    public const val MAX_REPS: Int = 30

    /** Bonus XP for beating a personal best, given to the record's stat once per entry. */
    public const val PR_XP: Int = 15

    /** Runs shorter than this don't set pace records; a fast 200 m isn't a running pace. */
    public const val MIN_PACE_KM: Double = 1.0

    /**
     * Estimated one-rep max by the Epley formula, weight × (1 + reps / 30). A single is
     * the weight itself. The estimate is most reliable at 10 reps or fewer.
     */
    public fun estimatedOneRepMax(weightKg: Double, reps: Int): Double {
        require(reps in 1..MAX_REPS) { "Reps must be 1 to $MAX_REPS" }
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }

    /** Minutes per km. */
    public fun pace(distanceKm: Double, seconds: Int): Double = seconds / 60.0 / distanceKm

    public fun metrics(entry: RecordEntry): Map<Metric, Double> = when (entry.kind.input) {
        RecordInput.WEIGHT_REPS -> mapOf(Metric.EST_1RM to estimatedOneRepMax(entry.weightKg!!, entry.reps!!))
        RecordInput.REPS -> mapOf(Metric.REPS to entry.reps!!.toDouble())
        RecordInput.SECONDS -> mapOf(Metric.HOLD to entry.seconds!!.toDouble())
        RecordInput.DISTANCE_TIME -> buildMap {
            put(Metric.DISTANCE, entry.distanceKm!!)
            if (entry.distanceKm >= MIN_PACE_KM) put(Metric.PACE, pace(entry.distanceKm, entry.seconds!!))
        }
        RecordInput.WEIGHT -> mapOf(Metric.BODY_WEIGHT to entry.weightKg!!)
    }

    /** The best value of each beatable metric across [entries]. */
    public fun bests(entries: List<RecordEntry>): Map<Metric, Double> {
        val bests = mutableMapOf<Metric, Double>()
        entries.forEach { entry ->
            metrics(entry).forEach { (metric, value) ->
                if (metric.better == null) return@forEach
                val best = bests[metric]
                if (best == null || metric.isBetter(value, best)) bests[metric] = value
            }
        }
        return bests
    }

    /**
     * Metrics that [entry] beats, compared with [history] (earlier entries of the same kind).
     * A first entry is a baseline, not a record: there was nothing to beat.
     */
    public fun personalBests(history: List<RecordEntry>, entry: RecordEntry): List<PersonalBest> {
        require(history.all { it.kind == entry.kind }) { "History must be the same record kind" }
        val bests = bests(history)
        return metrics(entry).mapNotNull { (metric, value) ->
            val best = bests[metric] ?: return@mapNotNull null
            if (metric.isBetter(value, best)) PersonalBest(metric, best, value) else null
        }
    }

    /** XP for logging [entry]: a bonus when it sets at least one personal best. */
    public fun xpFor(personalBests: List<PersonalBest>): Int = if (personalBests.isEmpty()) 0 else PR_XP
}

/**
 * Test day is the first Saturday of each month: no office, so there is time to retest
 * records properly. Goals use these as checkpoints.
 */
public object TestDay {
    public fun isTestDay(date: LocalDate): Boolean = date == firstSaturday(date)

    /** The next Test day on or after [from]. */
    public fun next(from: LocalDate): LocalDate {
        val thisMonth = firstSaturday(from)
        return if (!from.isAfter(thisMonth)) thisMonth else firstSaturday(from.plusMonths(1))
    }

    /** A one-time quest shown on [date] only. */
    public fun quest(date: LocalDate): Quest {
        require(isTestDay(date)) { "$date is not a Test day" }
        return Quest(
            title = "Test day: retest 3 records",
            difficulty = Difficulty.HARD,
            stats = setOf(Stat.STRENGTH),
            schedule = Schedule.OneTime(expiresOn = date),
        )
    }

    private fun firstSaturday(date: LocalDate): LocalDate =
        date.withDayOfMonth(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
}
