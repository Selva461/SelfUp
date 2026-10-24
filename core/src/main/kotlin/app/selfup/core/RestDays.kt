package app.selfup.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Why a rest day can't be declared. */
public enum class RestRefusal(public val message: String) {
    PAST("That day is already over."),
    TOO_LATE("Rest for today has to be declared before noon. Plan it the day before."),
    TOO_FAR("Rest days can be planned up to two weeks ahead."),
    MONTH_FULL("All ${RestDays.PER_MONTH} rest days for that month are used."),
    ALREADY("That day is already a rest day."),
}

/**
 * Rest days, the System's version of Habitica's inn: for illness, travel or a planned day
 * off. A rest day's missed dailies cost no XP, spend no shield and leave no make-up quest,
 * and the streak holds without growing. Clearing it anyway still counts as a clear day.
 *
 * They are declared ahead, never after the fact: today only until noon, so a rest day
 * can't be used to dodge a penalty that is already coming. A few per month, so they stay
 * a safety valve rather than a habit.
 */
public object RestDays {
    public const val PER_MONTH: Int = 3

    /** How far into the quest day today can still be declared (04:00 reset + 8 h = noon). */
    public val DECLARE_TODAY_WITHIN: Duration = Duration.ofHours(8)

    public const val MAX_DAYS_AHEAD: Long = 14

    /** Why [day] can't be made a rest day at [now], or null when it can. [taken] is every rest day already declared. */
    public fun refusal(day: LocalDate, now: Instant, boundary: DayBoundary, taken: Set<LocalDate>): RestRefusal? {
        val today = boundary.questDay(now)
        return when {
            day in taken -> RestRefusal.ALREADY
            day.isBefore(today) -> RestRefusal.PAST
            day == today && Duration.between(boundary.startOf(today), now) >= DECLARE_TODAY_WITHIN -> RestRefusal.TOO_LATE
            day.isAfter(today.plusDays(MAX_DAYS_AHEAD)) -> RestRefusal.TOO_FAR
            usedIn(YearMonth.from(day), taken) >= PER_MONTH -> RestRefusal.MONTH_FULL
            else -> null
        }
    }

    /** Whether a declared rest day can still be called off: only until it would be too late to declare it. */
    public fun canCancel(day: LocalDate, now: Instant, boundary: DayBoundary): Boolean {
        val today = boundary.questDay(now)
        return day.isAfter(today) || (day == today && Duration.between(boundary.startOf(today), now) < DECLARE_TODAY_WITHIN)
    }

    public fun usedIn(month: YearMonth, taken: Set<LocalDate>): Int = taken.count { YearMonth.from(it) == month }

    public fun leftIn(month: YearMonth, taken: Set<LocalDate>): Int = (PER_MONTH - usedIn(month, taken)).coerceAtLeast(0)
}
