package app.selfup.core

import java.time.LocalDate

/** Rules for closing out a finished quest day. */
public object DailyReset {
    /**
     * Daily quests that were due on [day] and not completed. [existedOn] says whether a
     * quest already existed that day, so a quest created later is never penalized for it.
     */
    public fun missed(
        quests: List<Quest>,
        day: LocalDate,
        completedIds: Set<Long>,
        existedOn: (Quest) -> Boolean,
    ): List<Quest> = quests.filter {
        it.isPenalizable && it.schedule.isDueOn(day) && it.id !in completedIds && existedOn(it)
    }

    /**
     * The XP each stat loses for [missed], applied one quest at a time against running
     * totals so the floor clamp holds even when several misses hit the same stat.
     */
    public fun penalties(totals: Map<Stat, Long>, missed: List<Quest>): List<Penalty> {
        val running = totals.toMutableMap()
        return missed.flatMap { quest ->
            quest.stats.sortedBy { it.ordinal }.map { stat ->
                val current = running[stat] ?: 0
                val delta = XpRules.penaltyDelta(current)
                running[stat] = current + delta
                Penalty(quest.id, stat, delta)
            }
        }
    }

    /** The make-up quest offered on [nextDay] for missing [missed]. */
    public fun penaltyQuest(missed: Quest, nextDay: LocalDate): Quest = Quest(
        title = "Make up: ${missed.title}",
        difficulty = Difficulty.MEDIUM,
        stats = missed.stats,
        schedule = Schedule.OneTime(expiresOn = nextDay),
        penaltyFor = missed.id,
    )
}

public data class Penalty(val questId: Long, val stat: Stat, val delta: Int)
