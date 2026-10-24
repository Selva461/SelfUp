package app.selfup.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DungeonTest {
    private val monday = LocalDate.of(2026, 10, 19)

    private fun week(build: (Int) -> DungeonDay) = (0..6).map { build(it) }

    @Test
    fun `weeks start on monday`() {
        assertEquals(monday, Dungeons.weekStart(LocalDate.of(2026, 10, 25)))
        assertEquals(monday, Dungeons.weekStart(monday))
    }

    @Test
    fun `every objective met is an S rank with the full reward`() {
        val days = week { DungeonDay(monday.plusDays(it.toLong()), clear = true, trained = true, studied = true, scrollMinutes = 5) }
        val result = Dungeons.progress(monday, days)
        assertEquals(DungeonRank.S, result.rank)
        assertEquals(Dungeons.FULL_XP, Dungeons.xp(result).values.sum())
        assertEquals(Dungeons.FULL_GOLD, Dungeons.gold(result))
        assertEquals(setOf(Stat.LIFE, Stat.STRENGTH, Stat.TECHNICAL), Dungeons.xp(result).keys)
    }

    @Test
    fun `low scrolling only counts once the usage log measured a day`() {
        val days = week { DungeonDay(monday.plusDays(it.toLong()), clear = true, trained = true, studied = true) }
        val result = Dungeons.progress(monday, days)
        assertFalse(result.objectives.any { it.objective == DungeonObjective.LOW_SCROLL })
        assertEquals(DungeonRank.S, result.rank)
    }

    @Test
    fun `a half week pays little and an idle week costs a little`() {
        val half = week { DungeonDay(monday.plusDays(it.toLong()), clear = it < 3, trained = it < 2, studied = it < 2, scrollMinutes = if (it < 3) 5 else 60) }
        val result = Dungeons.progress(monday, half)
        assertEquals(DungeonRank.D, result.rank)
        assertTrue(Dungeons.xp(result).values.sum() in 1..10)

        val idle = Dungeons.progress(monday, week { DungeonDay(monday.plusDays(it.toLong()), scrollMinutes = 90) })
        assertEquals(DungeonRank.E, idle.rank)
        assertEquals(mapOf(Stat.LIFE to -Performance.loss(Dungeons.FULL_XP)), Dungeons.xp(idle))
        assertEquals(0, Dungeons.gold(idle))
    }

    @Test
    fun `rest days lower the targets except low scrolling`() {
        val days = week { DungeonDay(monday.plusDays(it.toLong()), rested = it < 2, scrollMinutes = 0) }
        val targets = Dungeons.progress(monday, days).objectives.associate { it.objective to it.target }
        assertEquals(3, targets[DungeonObjective.CLEAR_DAYS])
        assertEquals(2, targets[DungeonObjective.TRAINING])
        assertEquals(5, targets[DungeonObjective.LOW_SCROLL])
    }

    @Test
    fun `days outside the week are ignored`() {
        val days = listOf(DungeonDay(monday.minusDays(1), clear = true), DungeonDay(monday.plusDays(7), clear = true))
        assertEquals(0, Dungeons.progress(monday, days).objectives.first().count)
    }
}
