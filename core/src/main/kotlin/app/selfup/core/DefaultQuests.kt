package app.selfup.core

import java.time.DayOfWeek

/** Quests seeded on first launch, taken from the real weekly routine. All are editable. */
public object DefaultQuests {
    /** Nights followed by a workday. Friday and Saturday nights run late by design. */
    private val workNights = setOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
    )

    public val all: List<Quest> = listOf(
        Quest(
            title = "Finish a gym session",
            difficulty = Difficulty.MEDIUM,
            stats = setOf(Stat.STRENGTH),
            schedule = Schedule.Recurring(),
        ),
        Quest(
            title = "Log every meal today",
            difficulty = Difficulty.EASY,
            stats = setOf(Stat.NUTRITION),
            schedule = Schedule.Recurring(),
            input = CompletionInput.YesNo,
        ),
        Quest(
            title = "Finish last meal 2+ hours before bed",
            difficulty = Difficulty.MEDIUM,
            stats = setOf(Stat.NUTRITION),
            schedule = Schedule.Recurring(),
        ),
        Quest(
            title = "Phone off by 23:00",
            difficulty = Difficulty.MEDIUM,
            stats = setOf(Stat.LIFE),
            schedule = Schedule.Recurring(),
        ),
        Quest(
            title = "In bed by 00:00",
            difficulty = Difficulty.EASY,
            stats = setOf(Stat.LIFE),
            schedule = Schedule.Recurring(workNights),
        ),
        Quest(
            title = "Spend 30 minutes building or learning",
            difficulty = Difficulty.MEDIUM,
            stats = setOf(Stat.TECHNICAL),
            schedule = Schedule.Recurring(),
        ),
        Quest(
            title = "Log today's spending",
            difficulty = Difficulty.EASY,
            stats = setOf(Stat.MONEY),
            schedule = Schedule.Recurring(),
            input = CompletionInput.Number(unit = "₹"),
        ),
    )
}
