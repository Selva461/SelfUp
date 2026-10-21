package app.selfup.core

public data class StatProgress(
    val stat: Stat,
    val totalXp: Long,
) {
    val progress: LevelProgress = Leveling.progress(totalXp)
    val level: Int get() = progress.level
    val rank: Rank get() = Rank.forLevel(level)

    public companion object {
        /** One entry per stat in [stats], in that order; stats with no XP yet start at level 1. */
        public fun of(totals: Map<Stat, Long>, stats: List<Stat> = Stat.launchSet): List<StatProgress> =
            stats.map { StatProgress(it, totals[it] ?: 0) }

        /** Overall level is the sum of the shown stats' levels. */
        public fun overallLevel(stats: List<StatProgress>): Int = stats.sumOf { it.level }
    }
}
