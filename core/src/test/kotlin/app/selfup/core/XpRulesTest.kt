package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XpRulesTest {
    @Test
    fun `completion xp is difficulty times 10`() {
        assertEquals(10, XpRules.completionXp(Difficulty.EASY))
        assertEquals(20, XpRules.completionXp(Difficulty.MEDIUM))
        assertEquals(30, XpRules.completionXp(Difficulty.HARD))
    }

    @Test
    fun `split is even and conserves the total`() {
        assertEquals(mapOf(Stat.STRENGTH to 10, Stat.LIFE to 10), XpRules.split(20, setOf(Stat.LIFE, Stat.STRENGTH)))

        val three = XpRules.split(20, setOf(Stat.MONEY, Stat.TECHNICAL, Stat.STRENGTH))
        assertEquals(mapOf(Stat.STRENGTH to 7, Stat.TECHNICAL to 7, Stat.MONEY to 6), three)
        assertEquals(20, three.values.sum())
    }

    @Test
    fun `split rejects an empty stat set`() {
        assertFailsWith<IllegalArgumentException> { XpRules.split(10, emptySet()) }
    }

    @Test
    fun `penalty takes 5 xp when there is room`() {
        assertEquals(-5, XpRules.penaltyDelta(130))
    }

    @Test
    fun `penalty never crosses the level floor`() {
        assertEquals(-3, XpRules.penaltyDelta(103))
        assertEquals(0, XpRules.penaltyDelta(100))
        assertEquals(0, XpRules.penaltyDelta(0))
    }

    @Test
    fun `penalty never lowers a level across the whole curve`() {
        for (xp in 0L..5_000L) {
            val after = xp + XpRules.penaltyDelta(xp)
            assertEquals(Leveling.levelFor(xp), Leveling.levelFor(after), "at $xp xp")
        }
    }

    @Test
    fun `apply reports level ups`() {
        val changes = XpRules.apply(
            totals = mapOf(Stat.STRENGTH to 95L, Stat.MONEY to 10L),
            deltas = mapOf(Stat.MONEY to 10, Stat.STRENGTH to 10),
        )
        val strength = changes.single { it.stat == Stat.STRENGTH }
        assertEquals(1, strength.levelBefore)
        assertEquals(2, strength.levelAfter)
        assertTrue(strength.leveledUp)
        assertFalse(changes.single { it.stat == Stat.MONEY }.leveledUp)
        assertEquals(listOf(Stat.STRENGTH, Stat.MONEY), changes.map { it.stat })
    }

    @Test
    fun `apply treats missing stats as zero`() {
        val change = XpRules.apply(emptyMap(), mapOf(Stat.LIFE to 100)).single()
        assertEquals(1, change.levelBefore)
        assertEquals(2, change.levelAfter)
    }

    @Test
    fun `overall level sums shown stats`() {
        val stats = StatProgress.of(mapOf(Stat.STRENGTH to 300L, Stat.MONEY to 100L))
        assertEquals(Stat.launchSet, stats.map { it.stat })
        // Strength 3, Money 2, three others at 1.
        assertEquals(8, StatProgress.overallLevel(stats))
    }
}
