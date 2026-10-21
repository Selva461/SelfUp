package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LevelingTest {
    @Test
    fun `cost to advance is level times 100`() {
        assertEquals(100, Leveling.xpToAdvance(1))
        assertEquals(500, Leveling.xpToAdvance(5))
    }

    @Test
    fun `level floors are cumulative`() {
        assertEquals(0, Leveling.floorXp(1))
        assertEquals(100, Leveling.floorXp(2))
        assertEquals(300, Leveling.floorXp(3))
        assertEquals(1000, Leveling.floorXp(5))
        assertEquals(1500, Leveling.floorXp(6))
    }

    @Test
    fun `level is exact at every boundary`() {
        for (level in 1..200) {
            val floor = Leveling.floorXp(level)
            assertEquals(level, Leveling.levelFor(floor), "at floor of $level")
            if (floor > 0) assertEquals(level - 1, Leveling.levelFor(floor - 1), "just below $level")
        }
    }

    @Test
    fun `negative or zero xp is level 1`() {
        assertEquals(1, Leveling.levelFor(0))
        assertEquals(1, Leveling.levelFor(-40))
    }

    @Test
    fun `progress reports position inside the level`() {
        val p = Leveling.progress(350)
        assertEquals(3, p.level)
        assertEquals(50, p.xpIntoLevel)
        assertEquals(300, p.xpToAdvance)
        assertEquals(50f / 300f, p.fraction)
    }

    @Test
    fun `rank tiers follow level bands`() {
        assertEquals(Rank.NOVICE, Rank.forLevel(1))
        assertEquals(Rank.NOVICE, Rank.forLevel(9))
        assertEquals(Rank.APPRENTICE, Rank.forLevel(10))
        assertEquals(Rank.ADEPT, Rank.forLevel(34))
        assertEquals(Rank.EXPERT, Rank.forLevel(35))
        assertEquals(Rank.MASTER, Rank.forLevel(50))
        assertEquals(Rank.MASTER, Rank.forLevel(999))
    }
}
