package app.selfup.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidedReportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)
    private val day = LocalDate.of(2026, 10, 24)
    private fun at(time: String, date: LocalDate = day) = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private val spending = Quest(
        id = 7, title = "Log today's spending", difficulty = Difficulty.EASY, stats = setOf(Stat.MONEY),
        schedule = Schedule.Recurring(), input = CompletionInput.Number(unit = "₹"), reminder = LocalTime.of(22, 30),
    )
    private val study = Quest(id = 6, title = "Spend 30 minutes building or learning", difficulty = Difficulty.MEDIUM, stats = setOf(Stat.TECHNICAL), schedule = Schedule.Recurring())
    private val gym = Quest(id = 1, title = "Finish a gym session", difficulty = Difficulty.MEDIUM, stats = setOf(Stat.STRENGTH), schedule = Schedule.Recurring())
    private val open = listOf(spending to null, study to null, gym to AutoCheck.GYM)

    @Test
    fun `asks meals, then amounts once due, then each quest only you can vouch for`() {
        val noon = GuidedReport.due(open, lastMeal = at("08:30"), now = at("12:00"), boundary, emptySet())
        assertEquals(listOf(ReportItem.Meal, ReportItem.Confirm(study)), noon)
        val night = GuidedReport.due(open, lastMeal = at("21:00"), now = at("22:45"), boundary, emptySet())
        assertEquals(listOf(ReportItem.Amount(spending), ReportItem.Confirm(study)), night)
        // After midnight is still the same quest day, so the 22:30 reminder has passed.
        val late = GuidedReport.due(open, lastMeal = at("23:30"), now = at("00:30", day.plusDays(1)), boundary, emptySet())
        assertTrue(ReportItem.Amount(spending) in late)
    }

    @Test
    fun `snoozed questions wait unless the report was asked for`() {
        val snoozed = setOf("meal", "quest:6")
        assertEquals(emptyList(), GuidedReport.due(open, lastMeal = null, now = at("12:00"), boundary, snoozed))
        val forced = GuidedReport.due(open, lastMeal = null, now = at("12:00"), boundary, snoozed, force = true)
        assertEquals(listOf(ReportItem.Meal, ReportItem.Amount(spending), ReportItem.Confirm(study)), forced)
    }

    @Test
    fun `no meal question early in the morning`() {
        assertEquals(listOf(ReportItem.Confirm(study)), GuidedReport.due(open, lastMeal = null, now = at("05:00"), boundary, emptySet()))
    }
}
