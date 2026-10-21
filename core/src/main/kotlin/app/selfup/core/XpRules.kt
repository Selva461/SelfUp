package app.selfup.core

public object XpRules {
    public const val XP_PER_DIFFICULTY: Int = 10
    public const val MISSED_DAILY_PENALTY: Int = 5

    public fun completionXp(difficulty: Difficulty): Int = difficulty.value * XP_PER_DIFFICULTY

    /**
     * Splits [total] evenly across [stats]. The remainder goes one point at a time to the
     * stats earliest in declaration order, so the parts always add up to [total]
     * (20 XP over three stats is 7 + 7 + 6, not 6.67 each).
     */
    public fun split(total: Int, stats: Set<Stat>): Map<Stat, Int> {
        require(stats.isNotEmpty()) { "A quest needs at least one stat" }
        require(total >= 0) { "XP to split must not be negative, was $total" }
        val ordered = stats.sortedBy { it.ordinal }
        val base = total / ordered.size
        val remainder = total % ordered.size
        return ordered.withIndex().associate { (i, stat) -> stat to base + if (i < remainder) 1 else 0 }
    }

    public fun completionDeltas(quest: Quest): Map<Stat, Int> =
        split(completionXp(quest.difficulty), quest.stats)

    /**
     * The (non-positive) XP change for missing a daily quest. It is clamped so the stat
     * never falls below the floor of its current level: penalties sting but never de-level.
     */
    public fun penaltyDelta(currentTotalXp: Long): Int {
        val floor = Leveling.floorXp(Leveling.levelFor(currentTotalXp))
        val headroom = (currentTotalXp - floor).coerceAtLeast(0)
        return -minOf(MISSED_DAILY_PENALTY.toLong(), headroom).toInt()
    }

    /** Applies [deltas] to [totals] and reports each stat's level before and after. */
    public fun apply(totals: Map<Stat, Long>, deltas: Map<Stat, Int>): List<StatChange> =
        deltas.entries.sortedBy { it.key.ordinal }.map { (stat, delta) ->
            val before = totals[stat] ?: 0
            val after = (before + delta).coerceAtLeast(0)
            StatChange(
                stat = stat,
                delta = delta,
                levelBefore = Leveling.levelFor(before),
                levelAfter = Leveling.levelFor(after),
            )
        }
}

public data class StatChange(
    val stat: Stat,
    val delta: Int,
    val levelBefore: Int,
    val levelAfter: Int,
) {
    val leveledUp: Boolean get() = levelAfter > levelBefore
}
