package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)
    private val day = LocalDate.of(2026, 10, 19)
    private val insta = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    private fun at(time: String, date: LocalDate = day): Instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
    private fun on(pkg: String, time: String, date: LocalDate = day) = UsageEvent(pkg, at(time, date), UsageKind.RESUMED)
    private fun off(pkg: String, time: String, date: LocalDate = day) = UsageEvent(pkg, at(time, date), UsageKind.PAUSED)

    @Test
    fun `foreground time adds up sessions per app and ignores other apps`() {
        val events = listOf(
            on(insta, "10:00"), off(insta, "10:12"),
            on("com.whatsapp", "10:12"), off("com.whatsapp", "10:40"),
            on(yt, "21:00"), off(yt, "21:20"),
            on(insta, "22:00"), off(insta, "22:03"),
        )
        val ms = ScreenTime.foregroundMillis(events, boundary.startOf(day), boundary.startOf(day.plusDays(1)))
        assertEquals(15 * 60_000L, ms[insta])
        assertEquals(20 * 60_000L, ms[yt])
        assertEquals(35, ScreenTime.minutes(ms))
    }

    @Test
    fun `sessions open at either edge are clipped to the window`() {
        val start = at("12:00")
        val end = at("13:00")
        val events = listOf(off(insta, "12:10"), on(yt, "12:50"))
        val ms = ScreenTime.foregroundMillis(events, start, end)
        assertEquals(10 * 60_000L, ms[insta])
        assertEquals(10 * 60_000L, ms[yt])
    }

    @Test
    fun `cutoff is read from phone off and bedtime quest titles`() {
        assertEquals(LocalTime.of(23, 0), ScreenTime.cutoff("Phone off by 23:00"))
        assertEquals(LocalTime.MIDNIGHT, ScreenTime.cutoff("In bed by 00:00"))
        assertEquals(LocalTime.of(22, 30), ScreenTime.cutoff("phone down by 22.30"))
        assertNull(ScreenTime.cutoff("Finish a gym session"))
        assertNull(ScreenTime.cutoff("Phone off by 25:00"))
    }

    @Test
    fun `phone stayed off only if nothing opened after the cutoff that quest day`() {
        val night = LocalTime.of(23, 0)
        val quiet = listOf(on(insta, "22:30"), off(insta, "22:55"), on("launcher", "07:00", day.plusDays(1)))
        assertTrue(ScreenTime.stayedOff(quiet, day, night, boundary))

        val late = quiet + on("launcher", "01:10", day.plusDays(1))
        assertFalse(ScreenTime.stayedOff(late, day, night, boundary))

        // A midnight cutoff belongs to the same quest day, so 23:30 use is still fine.
        assertTrue(ScreenTime.stayedOff(listOf(on(insta, "23:30")), day, LocalTime.MIDNIGHT, boundary))
    }

    @Test
    fun `apps lock once the budget is spent unless a grace unlock is running`() {
        val now = at("20:00")
        assertFalse(ScreenTime.shouldBlock(29, now, null))
        assertTrue(ScreenTime.shouldBlock(30, now, null))
        assertFalse(ScreenTime.shouldBlock(45, now, graceUntil = at("20:03")))
        assertTrue(ScreenTime.shouldBlock(45, now, graceUntil = at("19:59")))
    }

    @Test
    fun `a day summary covers every app, first and last use, and late opens`() {
        val events = listOf(
            on("launcher", "07:00"), off("launcher", "07:01"),
            on(insta, "08:00"), on(insta, "08:01"), off(insta, "08:10"),
            on("com.whatsapp", "12:00"), off("com.whatsapp", "12:05"),
            on(insta, "23:30"), off(insta, "23:50"),
            on("launcher", "01:00", day.plusDays(1)), off("launcher", "01:02", day.plusDays(1)),
        )
        val summary = ScreenTime.summarize(
            events,
            boundary.startOf(day),
            boundary.startOf(day.plusDays(1)),
            lateFrom = ReminderPlanner.instantOn(day, ScreenTime.LATE_FROM, boundary),
            entertainment = setOf(insta),
        )
        assertEquals(AppUse(30 * 60_000L, 2, entertainment = true), summary.apps[insta])
        assertEquals(AppUse(3 * 60_000L, 2, entertainment = false), summary.apps["launcher"])
        assertEquals(30 * 60_000L, summary.entertainmentMillis)
        assertEquals(38 * 60_000L, summary.totalMillis)
        assertEquals(5, summary.opens)
        assertEquals(2, summary.lateOpens)
        assertEquals(at("07:00"), summary.firstUse)
        assertEquals(at("01:02", day.plusDays(1)), summary.lastUse)
    }

    @Test
    fun `screen time pays in full only near zero and costs XP far over`() {
        assertEquals(1.0, ScreenTime.budgetPerformance(0))
        assertEquals(1.0, ScreenTime.budgetPerformance(ScreenTime.FREE_MINUTES))
        assertEquals(0.8, ScreenTime.budgetPerformance(10), 1e-9)
        assertEquals(0.0, ScreenTime.budgetPerformance(45))
        assertEquals(10, Performance.xp(ScreenTime.UNDER_BUDGET_XP, ScreenTime.budgetPerformance(3)))
        assertEquals(5, Performance.xp(ScreenTime.UNDER_BUDGET_XP, ScreenTime.budgetPerformance(10)))
        assertEquals(-1, Performance.xp(ScreenTime.UNDER_BUDGET_XP, ScreenTime.budgetPerformance(25)))
    }

    @Test
    fun `phone off is perfect untouched and loses for each open and minute after the cutoff`() {
        val cutoff = LocalTime.of(23, 0)
        val next = day.plusDays(1)
        val clean = listOf(on(insta, "22:00"), off(insta, "22:50"))
        assertEquals(1.0, ScreenTime.offPerformance(clean, day, cutoff, boundary).performance)

        val peek = clean + listOf(on("com.whatsapp", "23:30"), off("com.whatsapp", "23:33"))
        val result = ScreenTime.offPerformance(peek, day, cutoff, boundary)
        assertEquals(1, result.opens)
        assertEquals(3.0, result.minutes, 1e-9)
        assertEquals(1 - ScreenTime.OPEN_COST - 3.0 / ScreenTime.LATE_MINUTES_TO_FAIL, result.performance, 1e-9)

        val scroll = clean + listOf(on(insta, "00:10", next), off(insta, "01:00", next))
        assertEquals(0.0, ScreenTime.offPerformance(scroll, day, cutoff, boundary).performance)
    }
}
