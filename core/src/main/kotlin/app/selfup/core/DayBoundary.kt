package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Maps a moment to the "quest day" it belongs to.
 *
 * The day rolls over at [resetTime], not at midnight. The default is 04:00 because bedtime
 * is around 00:00 (later on Fridays): with a midnight reset, logging dinner or spending at
 * 00:10 would land on the wrong day and the real day would be penalized as missed.
 */
public class DayBoundary(
    public val resetTime: LocalTime = DEFAULT_RESET,
    public val zone: ZoneId = ZoneId.systemDefault(),
) {
    public fun questDay(instant: Instant): LocalDate {
        val local = instant.atZone(zone)
        val date = local.toLocalDate()
        return if (local.toLocalTime().isBefore(resetTime)) date.minusDays(1) else date
    }

    /** The first reset strictly after [instant]. */
    public fun nextReset(instant: Instant): Instant {
        val day = questDay(instant)
        return ZonedDateTime.of(day.plusDays(1), resetTime, zone).toInstant()
    }

    public companion object {
        public val DEFAULT_RESET: LocalTime = LocalTime.of(4, 0)
    }
}
