package app.selfup.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** One question in the guided report. */
public sealed interface ReportItem {
    /** Stable id, for putting the question off ("Not yet") for a while. */
    public val key: String

    /** "What did you eat?", asked until the answer is "that's all". */
    public data object Meal : ReportItem {
        override val key: String = "meal"
    }

    /** A logging quest such as today's spending: asks for the number. */
    public data class Amount(val quest: Quest) : ReportItem {
        override val key: String get() = "quest:${quest.id}"
    }

    /** A quest only you can vouch for: done or not yet. */
    public data class Confirm(val quest: Quest) : ReportItem {
        override val key: String get() = "quest:${quest.id}"
    }
}

/**
 * The guided report: instead of hunting through tabs, the System asks one question after
 * another (meals, then numbers like today's spending, then each open quest) and moves on as
 * soon as one is answered. This decides which questions are due right now.
 */
public object GuidedReport {
    /** Meals are asked for once this long has passed since the last one logged. */
    public val MEAL_GAP: Duration = Duration.ofHours(3)

    /** No meal questions before this. */
    public val MEALS_FROM: LocalTime = LocalTime.of(7, 0)

    /** A logging quest with no reminder is asked for from this time, so a day's total is a day's. */
    public val AMOUNTS_FROM: LocalTime = LocalTime.of(18, 0)

    /** How long "Not yet" puts a question off. */
    public val SNOOZE: Duration = Duration.ofHours(1)

    /** How long "Nothing since" or "That's all" puts the meal question off. */
    public val MEAL_SNOOZE: Duration = Duration.ofHours(3)

    /**
     * The questions due now, in order. [open] is today's open quests with how each is
     * checked; [snoozed] holds keys put off; [force] (the user asked for the report) ignores
     * both the snoozes and the times.
     */
    public fun due(
        open: List<Pair<Quest, AutoCheck?>>,
        lastMeal: Instant?,
        now: Instant,
        boundary: DayBoundary,
        snoozed: Set<String>,
        force: Boolean = false,
    ): List<ReportItem> {
        val today = boundary.questDay(now)
        fun allowed(item: ReportItem) = force || item.key !in snoozed
        return buildList {
            val mealsOpen = !now.isBefore(ReminderPlanner.instantOn(today, MEALS_FROM, boundary))
            val hungry = lastMeal == null || lastMeal.isBefore(boundary.startOf(today)) || Duration.between(lastMeal, now) >= MEAL_GAP
            if ((force || (mealsOpen && hungry)) && allowed(ReportItem.Meal)) add(ReportItem.Meal)
            open.filter { (quest, check) -> check == null && quest.input is CompletionInput.Number }.forEach { (quest, _) ->
                val from = ReminderPlanner.instantOn(today, quest.reminder ?: AMOUNTS_FROM, boundary)
                val item = ReportItem.Amount(quest)
                if ((force || !now.isBefore(from)) && allowed(item)) add(item)
            }
            open.filter { (quest, check) -> check == null && quest.input !is CompletionInput.Number }.forEach { (quest, _) ->
                val item = ReportItem.Confirm(quest)
                if (allowed(item)) add(item)
            }
        }
    }
}
