package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** When a quest's reminder should next fire. */
public object ReminderPlanner {
    private const val LOOKAHEAD_DAYS = 8L

    /**
     * The first reminder strictly after [now], on a quest day the quest is due and not
     * yet done. Returns null when the quest has no reminder or will never be due again.
     *
     * [doneToday] covers the current quest day; [finished] means a one-time quest is
     * already complete for good.
     */
    public fun nextFire(
        quest: Quest,
        now: Instant,
        boundary: DayBoundary,
        doneToday: Boolean,
        finished: Boolean = false,
    ): Instant? {
        val time = quest.reminder ?: return null
        if (finished) return null
        val today = boundary.questDay(now)
        for (offset in 0..LOOKAHEAD_DAYS) {
            val day = today.plusDays(offset)
            if (offset == 0L && doneToday) continue
            if (!quest.schedule.isDueOn(day)) continue
            val fireAt = instantOn(day, time, boundary)
            if (fireAt.isAfter(now)) return fireAt
        }
        return null
    }

    /**
     * A clock time on a quest day. Times before the reset (e.g. 01:00 with a 04:00 reset)
     * belong to the night after the quest day's calendar date.
     */
    public fun instantOn(day: LocalDate, time: LocalTime, boundary: DayBoundary): Instant {
        val date = if (time.isBefore(boundary.resetTime)) day.plusDays(1) else day
        return ZonedDateTime.of(date, time, boundary.zone).toInstant()
    }
}
