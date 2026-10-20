package app.selfup.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

public data class Quest(
    val id: Long = 0,
    val title: String,
    val difficulty: Difficulty,
    val stats: Set<Stat>,
    val schedule: Schedule,
    val input: CompletionInput = CompletionInput.Tap,
    val reminder: LocalTime? = null,
    /** Set on a Penalty Quest: the id of the daily quest whose miss created it. */
    val penaltyFor: Long? = null,
) {
    init {
        require(title.isNotBlank()) { "A quest needs a title" }
        require(stats.isNotEmpty()) { "A quest needs at least one stat" }
    }

    val type: QuestType
        get() = when {
            input != CompletionInput.Tap -> QuestType.LOGGING
            schedule is Schedule.Recurring -> QuestType.DAILY
            else -> QuestType.ONE_TIME
        }

    /** Recurring quests are the ones that can be missed and penalized. */
    val isPenalizable: Boolean get() = schedule is Schedule.Recurring && penaltyFor == null
}

public enum class QuestType { DAILY, ONE_TIME, LOGGING }

public sealed interface Schedule {
    public fun isDueOn(date: LocalDate): Boolean

    /** Repeats on [days]; every day by default. */
    public data class Recurring(val days: Set<DayOfWeek> = DayOfWeek.entries.toSet()) : Schedule {
        init {
            require(days.isNotEmpty()) { "A recurring quest needs at least one day" }
        }

        override fun isDueOn(date: LocalDate): Boolean = date.dayOfWeek in days
    }

    /** Shown every day until completed, or until [expiresOn] has passed. */
    public data class OneTime(val expiresOn: LocalDate? = null) : Schedule {
        override fun isDueOn(date: LocalDate): Boolean = expiresOn == null || !date.isAfter(expiresOn)
    }
}

/** How a quest is completed. Anything but [Tap] makes it a logging quest. */
public sealed interface CompletionInput {
    public data object Tap : CompletionInput

    /** A number such as rupees spent. The last value is prefilled so entry stays under 10 s. */
    public data class Number(val unit: String) : CompletionInput

    public data object YesNo : CompletionInput
}
