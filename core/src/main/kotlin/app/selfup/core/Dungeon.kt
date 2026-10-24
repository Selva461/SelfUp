package app.selfup.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One of the weekly dungeon's objectives: a number of days this week that met a bar. */
public enum class DungeonObjective(
    public val key: String,
    public val title: String,
    /** What a day needs to count, for the window. */
    public val rule: String,
    public val target: Int,
    public val stat: Stat,
    /** Whether a rest day lowers the target (sick days shouldn't need training). */
    public val restLowers: Boolean,
) {
    CLEAR_DAYS("clear", "Clear days", "every daily quest done", 5, Stat.LIFE, restLowers = true),
    TRAINING("train", "Training days", "a gym session or strength quest done", 4, Stat.STRENGTH, restLowers = true),
    STUDY("study", "Study days", "a skill quiz, mind game, DSA problem or logged work", 4, Stat.TECHNICAL, restLowers = true),
    LOW_SCROLL("focus", "Low-scroll days", "${Dungeons.LOW_SCROLL_MINUTES} min or less in entertainment apps", 5, Stat.LIFE, restLowers = false),
    ;

    public companion object {
        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String): DungeonObjective? = byKey[key]
    }
}

/** What happened on one day of the week, as far as the dungeon cares. */
public data class DungeonDay(
    val date: LocalDate,
    val clear: Boolean = false,
    val trained: Boolean = false,
    val studied: Boolean = false,
    /** Entertainment minutes from the usage log, or null when the day wasn't measured. */
    val scrollMinutes: Int? = null,
    val rested: Boolean = false,
)

public data class ObjectiveProgress(val objective: DungeonObjective, val count: Int, val target: Int) {
    val fraction: Double get() = if (target <= 0) 1.0 else (count.toDouble() / target).coerceIn(0.0, 1.0)
    val met: Boolean get() = count >= target

    /** `clear=4/5`, for the stored detail. */
    val code: String get() = "${objective.key}=$count/$target"
}

public enum class DungeonRank(public val label: String) {
    S("S"), A("A"), B("B"), C("C"), D("D"), E("E");

    /** C or better is a clear. */
    public val cleared: Boolean get() = this <= C

    public companion object {
        public fun of(performance: Double): DungeonRank = when {
            Performance.isPerfect(performance) -> S
            performance >= 0.9 -> A
            performance >= 0.75 -> B
            performance >= 0.6 -> C
            !Performance.isFail(performance) -> D
            else -> E
        }
    }
}

/** A week's dungeon, in progress or settled. */
public data class DungeonWeek(val start: LocalDate, val objectives: List<ObjectiveProgress>) {
    val end: LocalDate get() = start.plusDays(6)

    /** The mean of each objective's progress, so every objective matters equally. */
    val performance: Double get() = if (objectives.isEmpty()) 0.0 else objectives.sumOf { it.fraction } / objectives.size
    val rank: DungeonRank get() = DungeonRank.of(performance)
    val detail: String get() = objectives.joinToString(" ") { it.code }
}

/**
 * The weekly dungeon: a boss quest over a Monday-to-Sunday week, aimed at the habits that
 * matter most (clean days, training, study and low scrolling). It is built from what the
 * System already measures, so nothing has to be ticked. The reward follows [Performance]:
 * the full prize only for every objective met, little for a half-hearted week, and a small
 * loss for a week that barely tried.
 */
public object Dungeons {
    public const val FULL_XP: Int = 60
    public const val FULL_GOLD: Int = 150
    public const val LOW_SCROLL_MINUTES: Int = 10

    public fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Progress for the week starting [start] from its [days] so far. The low-scroll objective
     * only joins once the usage log has measured a day, so a phone without usage access isn't
     * set an objective it can't show. Each rest day lowers the other targets by one (never below one).
     */
    public fun progress(start: LocalDate, days: List<DungeonDay>): DungeonWeek {
        val week = days.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) }
        val rests = week.count { it.rested }
        val measured = week.any { it.scrollMinutes != null }
        val objectives = DungeonObjective.entries
            .filter { it != DungeonObjective.LOW_SCROLL || measured }
            .map { objective ->
                val count = week.count { day ->
                    when (objective) {
                        DungeonObjective.CLEAR_DAYS -> day.clear
                        DungeonObjective.TRAINING -> day.trained
                        DungeonObjective.STUDY -> day.studied
                        DungeonObjective.LOW_SCROLL -> day.scrollMinutes?.let { it <= LOW_SCROLL_MINUTES } == true
                    }
                }
                val target = if (objective.restLowers) (objective.target - rests).coerceAtLeast(1) else objective.target
                ObjectiveProgress(objective, count, target)
            }
        return DungeonWeek(start, objectives)
    }

    /** XP for a settled week: split across the objectives' stats, a loss coming out of Life. */
    public fun xp(week: DungeonWeek): Map<Stat, Int> {
        val total = Performance.xp(FULL_XP, week.performance)
        val stats = week.objectives.map { it.objective.stat }.toSet().ifEmpty { setOf(Stat.LIFE) }
        return if (total >= 0) XpRules.split(total, stats) else mapOf(Stat.LIFE to total)
    }

    public fun gold(week: DungeonWeek): Int = Performance.xp(FULL_GOLD, week.performance).coerceAtLeast(0)
}
