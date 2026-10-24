package app.selfup.core

/**
 * The System's voice: a drill instructor, not a cheerleader. It calls out what was done and
 * what wasn't, bluntly and with the numbers, and gives the next order. It goes after the
 * behaviour (the dodging, the scrolling, the excuses), never the person's worth.
 *
 * Lines are picked by [seed], so the same moment always reads the same and tests are stable.
 */
public object Drill {
    private fun List<String>.pick(seed: Long): String = this[Math.floorMod(seed, size.toLong()).toInt()]

    private fun plural(count: Int, one: String, many: String = one + "s"): String = if (count == 1) "1 $one" else "$count $many"

    /**
     * Days failed in a row, newest first in [days]: a day with misses that no shield or rest
     * day excused. Stops at the first clean day.
     */
    public fun failRun(days: List<DayOutcomeRow>): Int = days.takeWhile { it.failed }.size

    /** The lock over an app once the budget is gone. */
    public fun lockdown(app: String, usedMinutes: Int, budget: Int, seed: Long): String = listOf(
        "$usedMinutes minutes on $app. The limit was $budget. You were warned. It's shut.",
        "You picked $app over your quests again. Locked until reset.",
        "$usedMinutes minutes burned and nothing to show for it. Get out.",
        "This is where your day went. Not any more. Go do your quests.",
    ).pick(seed)

    /** The last warning before the lock. */
    public fun finalWarning(app: String, minutesLeft: Int, seed: Long): String = listOf(
        "${plural(minutesLeft, "minute")} left, then $app locks. Close it yourself.",
        "${plural(minutesLeft, "minute")} before lockdown. Every minute here is a quest you aren't doing.",
        "You're about to burn the rest of today's budget on $app. Shut it.",
    ).pick(seed)

    /** A quest's reminder. [dodges] is how many times it was put off today already. */
    public fun order(dodges: Int, seed: Long): String = when {
        dodges <= 0 -> listOf(
            "Do it now. Not later. Now.",
            "This is not a request.",
            "No excuses. Start.",
        ).pick(seed)
        dodges == 1 -> listOf(
            "You already put this off once. Do it.",
            "Second time asking. There won't be a nice third.",
        ).pick(seed)
        else -> listOf(
            "Put off $dodges times today. This is exactly how you lose days. Move.",
            "\"Later\" $dodges times. Later never comes for you. Now.",
        ).pick(seed)
    }

    /**
     * The nag over quests still open. [level] counts the nags already shown today, and
     * [failRun] the days failed in a row before today.
     */
    public fun nag(open: Int, level: Int, failRun: Int, seed: Long): String {
        val quests = plural(open, "quest")
        val line = when {
            level <= 0 -> listOf(
                "$quests still open. Stop scrolling and start.",
                "$quests waiting on you. Pick one. Do it now.",
            ).pick(seed)
            level == 1 -> listOf(
                "Still $quests open. You've been told twice. Do one now.",
                "Nothing moved since the last warning. $quests open.",
            ).pick(seed)
            level == 2 -> listOf(
                "$quests open and the clock keeps moving. This is how you failed before.",
                "Third warning. $quests open. You are choosing to fail today.",
            ).pick(seed)
            else -> listOf(
                "$quests open. The day is almost gone and you've done nothing on them. Get up.",
                "Warning ${level + 1}. $quests open. Every excuse is logged.",
            ).pick(seed)
        }
        return if (failRun >= 2) "You failed $failRun days running. $line" else line
    }

    /** What closing yesterday found, or null for a clean day. */
    public fun verdict(missed: Int, xpLost: Int, streakLost: Int, failRun: Int, seed: Long): String? {
        if (missed == 0 && streakLost <= 1) return null
        return buildList {
            if (missed > 0) add("You failed ${plural(missed, "quest")} yesterday. −$xpLost XP. Penalty quests are on today's list.")
            if (failRun >= 2) add(listOf("That's $failRun days running. Pathetic effort.", "$failRun days in a row. That's not bad luck, that's a habit.").pick(seed))
            if (streakLost > 1) add("Your $streakLost-day streak is gone because you couldn't be bothered.")
            add(listOf("Today you fix it.", "Clear today. No excuses.").pick(seed))
        }.joinToString(" ")
    }

    /** The close of the guided report. */
    public fun reportDone(done: Int, open: Int, seed: Long): String = when {
        open == 0 -> listOf(
            "All reported. That's the minimum. Same again tomorrow.",
            "Done. Don't expect praise for doing what you said you would.",
        ).pick(seed)
        done == 0 -> listOf(
            "Nothing done. ${plural(open, "quest")} still open. That's weak. Go fix it.",
            "Zero done, ${plural(open, "quest")} open. You're wasting the day.",
        ).pick(seed)
        else -> listOf(
            "$done done, $open still open. Not good enough. Finish them.",
            "$done done. $open left. Half a job is still a failed day.",
        ).pick(seed)
    }
}

/** One closed day, as [Drill.failRun] needs it. */
public data class DayOutcomeRow(val missed: Int, val excused: Boolean) {
    public val failed: Boolean get() = missed > 0 && !excused
}
