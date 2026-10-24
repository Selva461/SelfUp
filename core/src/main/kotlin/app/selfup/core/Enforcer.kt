package app.selfup.core

import java.time.Instant
import java.time.LocalTime

/**
 * When the System comes back about quests still open. Nags sit on fixed times that get
 * closer together as the day runs out: every few hours in the morning, every half hour
 * late in the evening. Nothing fires between [LAST] and [FIRST], so sleep isn't broken.
 */
public object Enforcer {
    public val slots: List<LocalTime> = listOf(
        "09:00", "12:00", "15:00", "17:00", "19:00", "20:00",
        "21:00", "21:30", "22:00", "22:30", "23:00", "23:30",
    ).map(LocalTime::parse)

    public val FIRST: LocalTime = slots.first()
    public val LAST: LocalTime = slots.last()

    /** The first nag time strictly after [now]. */
    public fun next(now: Instant, boundary: DayBoundary): Instant {
        val local = now.atZone(boundary.zone)
        val date = local.toLocalDate()
        slots.forEach { slot ->
            val at = date.atTime(slot).atZone(boundary.zone).toInstant()
            if (at.isAfter(now)) return at
        }
        return date.plusDays(1).atTime(FIRST).atZone(boundary.zone).toInstant()
    }

    /**
     * The quests a nag can push: due, not done, and something you can still do today. Quests
     * settled from tomorrow's data (phone off at night, the day's meals) are left out.
     */
    public fun pushable(check: AutoCheck?): Boolean =
        check != AutoCheck.PHONE_OFF && check != AutoCheck.LAST_MEAL && check != AutoCheck.MEALS
}
