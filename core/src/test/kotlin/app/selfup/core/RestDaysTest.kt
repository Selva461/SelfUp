package app.selfup.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestDaysTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)
    private val today = LocalDate.of(2026, 10, 24)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun `today can be declared in the morning but not after noon`() {
        assertNull(RestDays.refusal(today, at(today, 11, 59), boundary, emptySet()))
        assertEquals(RestRefusal.TOO_LATE, RestDays.refusal(today, at(today, 12, 0), boundary, emptySet()))
    }

    @Test
    fun `after midnight is still the late part of yesterday's quest day`() {
        // 01:00 on the 25th belongs to the 24th, which is long past noon.
        assertEquals(RestRefusal.TOO_LATE, RestDays.refusal(today, at(today.plusDays(1), 1), boundary, emptySet()))
        assertNull(RestDays.refusal(today.plusDays(1), at(today.plusDays(1), 1), boundary, emptySet()))
    }

    @Test
    fun `past days and days too far ahead are refused`() {
        val now = at(today, 9)
        assertEquals(RestRefusal.PAST, RestDays.refusal(today.minusDays(1), now, boundary, emptySet()))
        assertNull(RestDays.refusal(today.plusDays(14), now, boundary, emptySet()))
        assertEquals(RestRefusal.TOO_FAR, RestDays.refusal(today.plusDays(15), now, boundary, emptySet()))
    }

    @Test
    fun `each month allows three`() {
        val now = at(today, 9)
        val taken = setOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 16))
        assertEquals(RestRefusal.MONTH_FULL, RestDays.refusal(today.plusDays(1), now, boundary, taken))
        // November is a fresh month.
        assertNull(RestDays.refusal(LocalDate.of(2026, 11, 1), now, boundary, taken))
        assertEquals(RestRefusal.ALREADY, RestDays.refusal(LocalDate.of(2026, 10, 16), now, boundary, taken))
    }

    @Test
    fun `a rest day can be called off only while it could still be declared`() {
        assertTrue(RestDays.canCancel(today.plusDays(1), at(today, 22), boundary))
        assertTrue(RestDays.canCancel(today, at(today, 10), boundary))
        assertFalse(RestDays.canCancel(today, at(today, 13), boundary))
    }
}
