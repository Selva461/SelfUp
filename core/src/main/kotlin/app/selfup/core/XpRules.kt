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
     * XP for a quest the System scored rather than took on trust: the full reward only at a
     * performance of 1, scaled down below that (see [Performance]). A failing performance
     * costs XP, all from the quest's first stat.
     */
    public fun scoredDeltas(quest: Quest, performance: Double): Map<Stat, Int> {
        val total = Performance.xp(completionXp(quest.difficulty), performance)
        return if (total >= 0) split(total, quest.stats) else mapOf(quest.stats.minBy { it.ordinal } to total)
    }

    /**
     * The XP change for a poor result worth -[amount], clamped like a missed-quest penalty so
     * it never takes a stat below the floor of its current level.
     */
    public fun lossDelta(currentTotalXp: Long, amount: Int): Int {
        require(amount >= 0) { "A loss is given as a positive amount, was $amount" }
        val floor = Leveling.floorXp(Leveling.levelFor(currentTotalXp))
        val headroom = (currentTotalXp - floor).coerceAtLeast(0)
        return -minOf(amount.toLong(), headroom).toInt()
    }

    /** [delta] as it should land on a stat at [currentTotalXp]: gains in full, losses clamped by [lossDelta]. */
    public fun settle(currentTotalXp: Long, delta: Int): Int =
        if (delta >= 0) delta else lossDelta(currentTotalXp, -delta)

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
