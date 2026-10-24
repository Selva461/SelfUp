package app.selfup.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The NeetCode 150 topics, in its roadmap order, with how many problems the list has in each. */
public enum class DsaTopic(public val key: String, public val displayName: String, public val roadmap: Int) {
    ARRAYS("arrays", "Arrays & Hashing", 9),
    TWO_POINTERS("two_pointers", "Two Pointers", 5),
    SLIDING_WINDOW("sliding_window", "Sliding Window", 6),
    STACK("stack", "Stack", 7),
    BINARY_SEARCH("binary_search", "Binary Search", 7),
    LINKED_LIST("linked_list", "Linked List", 11),
    TREES("trees", "Trees", 15),
    TRIES("tries", "Tries", 3),
    HEAP("heap", "Heap / Priority Queue", 7),
    BACKTRACKING("backtracking", "Backtracking", 9),
    GRAPHS("graphs", "Graphs", 13),
    ADVANCED_GRAPHS("advanced_graphs", "Advanced Graphs", 6),
    DP_1D("dp_1d", "1-D Dynamic Programming", 12),
    DP_2D("dp_2d", "2-D Dynamic Programming", 11),
    GREEDY("greedy", "Greedy", 8),
    INTERVALS("intervals", "Intervals", 6),
    MATH("math", "Math & Geometry", 8),
    BITS("bits", "Bit Manipulation", 7),
    ;

    public companion object {
        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String): DsaTopic = byKey[key] ?: ARRAYS

        public val roadmapTotal: Int get() = entries.sumOf { it.roadmap }
    }
}

public enum class DsaDifficulty(public val key: String, public val displayName: String, public val xp: Int, public val targetMinutes: Int) {
    EASY("easy", "Easy", 10, 15),
    MEDIUM("medium", "Medium", 20, 25),
    HARD("hard", "Hard", 30, 40),
    ;

    public companion object {
        public fun fromKey(key: String): DsaDifficulty = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** How an attempt went. Only solving it alone moves a problem along its re-solve ladder. */
public enum class DsaOutcome(public val key: String, public val displayName: String, public val credit: Double) {
    ALONE("alone", "Solved alone", 1.0),
    HINT("hint", "Needed a hint", 0.6),
    SOLUTION("solution", "Read the solution", 0.3),
    ;

    public companion object {
        public fun fromKey(key: String): DsaOutcome = entries.firstOrNull { it.key == key } ?: SOLUTION
    }
}

public data class DsaAttempt(val day: LocalDate, val outcome: DsaOutcome, val minutes: Int?)

/** Where a problem stands after replaying its attempts. */
public data class DsaState(
    /** Re-solves passed alone in a row, 0 to [Dsa.MASTERED_AT]. */
    val stage: Int,
    val due: LocalDate?,
    val attempts: Int,
    val lastOutcome: DsaOutcome?,
) {
    val mastered: Boolean get() = stage >= Dsa.MASTERED_AT
    val solvedAlone: Boolean get() = stage > 0 || lastOutcome == DsaOutcome.ALONE

    public fun isDue(today: LocalDate): Boolean = !mastered && due != null && !due.isAfter(today)
}

/**
 * The DSA tracker: problems are re-solved from scratch on a widening ladder (3, 7, then 14
 * days), the common interview-prep advice, so a pattern sticks rather than being
 * recognised once. Solving alone climbs a rung; a hint repeats the rung; reading the
 * solution starts the ladder again. Passing the 14-day re-solve masters the problem.
 *
 * XP is scored, not granted: an attempt's performance is its outcome's credit, cut when it
 * runs past the difficulty's time target, and a re-solve pays half a first solve.
 */
public object Dsa {
    /** Days to the next re-solve after reaching each stage (0 = not yet solved alone). */
    public val LADDER: List<Long> = listOf(3, 3, 7, 14)
    public const val MASTERED_AT: Int = 3

    public fun replay(attempts: List<DsaAttempt>): DsaState {
        var stage = 0
        var due: LocalDate? = null
        attempts.sortedBy { it.day }.forEach { a ->
            stage = when (a.outcome) {
                DsaOutcome.ALONE -> {
                    // Re-solving early doesn't climb: only a solve once it's due proves recall.
                    val onTime = due == null || !a.day.isBefore(due)
                    if (onTime) (stage + 1).coerceAtMost(MASTERED_AT) else stage
                }
                DsaOutcome.HINT -> stage
                DsaOutcome.SOLUTION -> 0
            }
            due = if (stage >= MASTERED_AT) null else a.day.plusDays(LADDER[stage])
        }
        return DsaState(stage, due, attempts.size, attempts.maxByOrNull { it.day }?.outcome)
    }

    /** How well an attempt went, 0 to 1. */
    public fun performance(difficulty: DsaDifficulty, outcome: DsaOutcome, minutes: Int?): Double {
        val time = if (minutes == null || minutes <= 0) 1.0 else Performance.atMost(minutes.toDouble(), difficulty.targetMinutes.toDouble())
        // Time only trims a solve: at worst to two-thirds of its credit.
        return outcome.credit * (2.0 / 3 + time / 3)
    }

    /** XP for an attempt; [firstSolve] is false for a re-solve, which pays half. */
    public fun xp(difficulty: DsaDifficulty, performance: Double, firstSolve: Boolean): Int {
        val full = if (firstSolve) difficulty.xp else difficulty.xp / 2
        return Performance.xp(full, performance)
    }

    public fun daysOverdue(state: DsaState, today: LocalDate): Long =
        state.due?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) } ?: 0
}
