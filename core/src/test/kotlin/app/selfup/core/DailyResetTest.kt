package app.selfup.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyResetTest {
    private val monday = LocalDate.of(2026, 10, 19)

    private fun quest(
        id: Long,
        stats: Set<Stat> = setOf(Stat.STRENGTH),
        schedule: Schedule = Schedule.Recurring(),
        penaltyFor: Long? = null,
    ) = Quest(
        id = id,
        title = "Quest $id",
        difficulty = Difficulty.EASY,
        stats = stats,
        schedule = schedule,
        penaltyFor = penaltyFor,
    )

    @Test
    fun `missed covers only due, incomplete, existing daily quests`() {
        val quests = listOf(
            quest(1),
            quest(2),
            quest(3, schedule = Schedule.Recurring(setOf(DayOfWeek.SATURDAY))),
            quest(4, schedule = Schedule.OneTime()),
            quest(5, schedule = Schedule.OneTime(monday), penaltyFor = 1),
            quest(6),
        )
        val missed = DailyReset.missed(quests, monday, completedIds = setOf(2), existedOn = { it.id != 6L })
        assertEquals(listOf(1L), missed.map { it.id })
    }

    @Test
    fun `penalties clamp against running totals`() {
        // Strength has 8 XP of headroom above the level 2 floor (100).
        val totals = mapOf(Stat.STRENGTH to 108L, Stat.MONEY to 50L)
        val missed = listOf(quest(1), quest(2, stats = setOf(Stat.STRENGTH, Stat.MONEY)))
        val penalties = DailyReset.penalties(totals, missed)

        assertEquals(
            listOf(
                Penalty(1, Stat.STRENGTH, -5),
                Penalty(2, Stat.STRENGTH, -3),
                Penalty(2, Stat.MONEY, -5),
            ),
            penalties,
        )
    }

    @Test
    fun `repeated misses never cross a level floor`() {
        val missed = List(50) { quest(it.toLong()) }
        val total = 305L + DailyReset.penalties(mapOf(Stat.STRENGTH to 305L), missed).sumOf { it.delta }
        assertEquals(300L, total)
    }

    @Test
    fun `penalty quest is a medium one-day quest on the same stats`() {
        val missed = quest(9, stats = setOf(Stat.LIFE, Stat.MONEY))
        val penalty = DailyReset.penaltyQuest(missed, monday)
        assertEquals(Difficulty.MEDIUM, penalty.difficulty)
        assertEquals(missed.stats, penalty.stats)
        assertEquals(Schedule.OneTime(expiresOn = monday), penalty.schedule)
        assertEquals(9L, penalty.penaltyFor)
        assertEquals(false, penalty.isPenalizable)
    }
}
