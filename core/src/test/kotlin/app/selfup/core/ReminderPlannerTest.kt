package app.selfup.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    // 2026-10-19 is a Monday.
    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun quest(
        reminder: LocalTime? = LocalTime.of(22, 30),
        schedule: Schedule = Schedule.Recurring(),
    ) = Quest(
        id = 1,
        title = "Log spending",
        difficulty = Difficulty.EASY,
        stats = setOf(Stat.MONEY),
        schedule = schedule,
        reminder = reminder,
    )

    @Test
    fun `fires later today when still ahead`() {
        assertEquals(
            at("2026-10-19", "22:30"),
            ReminderPlanner.nextFire(quest(), at("2026-10-19", "20:00"), boundary, doneToday = false),
        )
    }

    @Test
    fun `moves to tomorrow once passed or done`() {
        val tomorrow = at("2026-10-20", "22:30")
        assertEquals(tomorrow, ReminderPlanner.nextFire(quest(), at("2026-10-19", "22:31"), boundary, doneToday = false))
        assertEquals(tomorrow, ReminderPlanner.nextFire(quest(), at("2026-10-19", "20:00"), boundary, doneToday = true))
    }

    @Test
    fun `skips days the quest is not due`() {
        val weekends = quest(schedule = Schedule.Recurring(setOf(DayOfWeek.SATURDAY)))
        assertEquals(
            at("2026-10-24", "22:30"),
            ReminderPlanner.nextFire(weekends, at("2026-10-19", "20:00"), boundary, doneToday = false),
        )
    }

    @Test
    fun `after-midnight reminders belong to the previous quest day`() {
        val late = quest(reminder = LocalTime.of(0, 30))
        // Monday's 00:30 reminder is early Tuesday morning.
        assertEquals(
            at("2026-10-20", "00:30"),
            ReminderPlanner.nextFire(late, at("2026-10-19", "20:00"), boundary, doneToday = false),
        )
        // At 00:10 Tuesday it is still Monday's quest day, so Monday's reminder is next.
        assertEquals(
            at("2026-10-20", "00:30"),
            ReminderPlanner.nextFire(late, at("2026-10-20", "00:10"), boundary, doneToday = false),
        )
    }

    @Test
    fun `no reminder, finished, or expired means null`() {
        val now = at("2026-10-19", "20:00")
        assertNull(ReminderPlanner.nextFire(quest(reminder = null), now, boundary, doneToday = false))
        assertNull(ReminderPlanner.nextFire(quest(), now, boundary, doneToday = false, finished = true))
        val expired = quest(schedule = Schedule.OneTime(expiresOn = LocalDate.parse("2026-10-19")))
        assertNull(ReminderPlanner.nextFire(expired, at("2026-10-19", "23:00"), boundary, doneToday = false))
    }
}
