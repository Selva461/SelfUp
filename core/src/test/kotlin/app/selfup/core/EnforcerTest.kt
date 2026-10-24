package app.selfup.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnforcerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)
    private val day = LocalDate.of(2026, 10, 24)

    private fun at(time: String, date: LocalDate = day) = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    @Test
    fun `nags get closer together as the day runs out`() {
        assertEquals(at("09:00"), Enforcer.next(at("06:10"), boundary))
        assertEquals(at("12:00"), Enforcer.next(at("09:00"), boundary))
        assertEquals(at("21:30"), Enforcer.next(at("21:05"), boundary))
        assertEquals(at("23:30"), Enforcer.next(at("23:00"), boundary))
    }

    @Test
    fun `nothing fires overnight`() {
        assertEquals(at("09:00", day.plusDays(1)), Enforcer.next(at("23:30"), boundary))
        assertEquals(at("09:00", day.plusDays(1)), Enforcer.next(at("01:30", day.plusDays(1)), boundary))
    }

    @Test
    fun `only quests you can still do today are pushed`() {
        assertTrue(Enforcer.pushable(null))
        assertTrue(Enforcer.pushable(AutoCheck.GYM))
        assertFalse(Enforcer.pushable(AutoCheck.PHONE_OFF))
        assertFalse(Enforcer.pushable(AutoCheck.MEALS))
        assertFalse(Enforcer.pushable(AutoCheck.LAST_MEAL))
    }
}
