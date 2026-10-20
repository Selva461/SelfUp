package app.selfup.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestTest {
    private val monday = LocalDate.of(2026, 10, 19)

    private fun quest(
        schedule: Schedule = Schedule.Recurring(),
        input: CompletionInput = CompletionInput.Tap,
        penaltyFor: Long? = null,
    ) = Quest(
        title = "Test",
        difficulty = Difficulty.MEDIUM,
        stats = setOf(Stat.STRENGTH),
        schedule = schedule,
        input = input,
        penaltyFor = penaltyFor,
    )

    @Test
    fun `type is derived from schedule and input`() {
        assertEquals(QuestType.DAILY, quest().type)
        assertEquals(QuestType.ONE_TIME, quest(schedule = Schedule.OneTime()).type)
        assertEquals(QuestType.LOGGING, quest(input = CompletionInput.Number("₹")).type)
    }

    @Test
    fun `quest requires a title and a stat`() {
        assertFailsWith<IllegalArgumentException> { quest().copy(title = " ") }
        assertFailsWith<IllegalArgumentException> { quest().copy(stats = emptySet()) }
    }

    @Test
    fun `recurring quests are due only on their days`() {
        val weekends = Schedule.Recurring(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        assertFalse(weekends.isDueOn(monday))
        assertTrue(weekends.isDueOn(monday.plusDays(5)))
        assertTrue(Schedule.Recurring().isDueOn(monday))
    }

    @Test
    fun `one-time quests show until they expire`() {
        assertTrue(Schedule.OneTime().isDueOn(monday.plusYears(1)))
        val penalty = Schedule.OneTime(expiresOn = monday)
        assertTrue(penalty.isDueOn(monday))
        assertFalse(penalty.isDueOn(monday.plusDays(1)))
    }

    @Test
    fun `only recurring non-penalty quests can be penalized`() {
        assertTrue(quest().isPenalizable)
        assertFalse(quest(schedule = Schedule.OneTime()).isPenalizable)
        assertFalse(quest(penaltyFor = 7).isPenalizable)
    }

    @Test
    fun `default quests are valid and cover the launch stats`() {
        val covered = DefaultQuests.all.flatMap { it.stats }.toSet()
        assertEquals(Stat.launchSet.toSet(), covered)
    }
}

class DayBoundaryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    @Test
    fun `just after midnight still counts as the previous day`() {
        assertEquals(LocalDate.parse("2026-10-19"), boundary.questDay(at("2026-10-20", "00:10")))
        assertEquals(LocalDate.parse("2026-10-19"), boundary.questDay(at("2026-10-20", "03:59")))
    }

    @Test
    fun `the day rolls over at the reset time`() {
        assertEquals(LocalDate.parse("2026-10-20"), boundary.questDay(at("2026-10-20", "04:00")))
        assertEquals(LocalDate.parse("2026-10-20"), boundary.questDay(at("2026-10-20", "23:59")))
    }

    @Test
    fun `next reset is the upcoming rollover`() {
        assertEquals(at("2026-10-20", "04:00"), boundary.nextReset(at("2026-10-20", "01:00")))
        assertEquals(at("2026-10-21", "04:00"), boundary.nextReset(at("2026-10-20", "04:00")))
    }

    @Test
    fun `midnight reset behaves like a calendar day`() {
        val midnight = DayBoundary(LocalTime.MIDNIGHT, zone)
        assertEquals(LocalDate.parse("2026-10-20"), midnight.questDay(at("2026-10-20", "00:00")))
        assertEquals(LocalDate.parse("2026-10-19"), midnight.questDay(at("2026-10-19", "23:59")))
    }
}
