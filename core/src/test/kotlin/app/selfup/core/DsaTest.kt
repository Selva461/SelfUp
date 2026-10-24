package app.selfup.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DsaTest {
    private val day0 = LocalDate.of(2026, 10, 1)
    private fun at(days: Long, outcome: DsaOutcome) = DsaAttempt(day0.plusDays(days), outcome, null)

    @Test
    fun `the roadmap is the 150`() {
        assertEquals(150, DsaTopic.roadmapTotal)
    }

    @Test
    fun `solving alone climbs 3, 7 then 14 days to mastery`() {
        val first = Dsa.replay(listOf(at(0, DsaOutcome.ALONE)))
        assertEquals(1, first.stage)
        assertEquals(day0.plusDays(3), first.due)
        val second = Dsa.replay(listOf(at(0, DsaOutcome.ALONE), at(3, DsaOutcome.ALONE)))
        assertEquals(day0.plusDays(10), second.due)
        val third = Dsa.replay(listOf(at(0, DsaOutcome.ALONE), at(3, DsaOutcome.ALONE), at(10, DsaOutcome.ALONE)))
        assertTrue(third.mastered)
        assertNull(third.due)
    }

    @Test
    fun `a hint repeats the rung and the solution restarts the ladder`() {
        val hint = Dsa.replay(listOf(at(0, DsaOutcome.ALONE), at(3, DsaOutcome.HINT)))
        assertEquals(1, hint.stage)
        assertEquals(day0.plusDays(6), hint.due)
        val reset = Dsa.replay(listOf(at(0, DsaOutcome.ALONE), at(3, DsaOutcome.ALONE), at(10, DsaOutcome.SOLUTION)))
        assertEquals(0, reset.stage)
        assertEquals(day0.plusDays(13), reset.due)
    }

    @Test
    fun `re-solving before it is due does not climb`() {
        val early = Dsa.replay(listOf(at(0, DsaOutcome.ALONE), at(1, DsaOutcome.ALONE)))
        assertEquals(1, early.stage)
        assertTrue(early.isDue(day0.plusDays(4)))
        assertFalse(early.isDue(day0.plusDays(3)))
    }

    @Test
    fun `xp is scored by outcome and time, half on a re-solve`() {
        assertEquals(20, Dsa.xp(DsaDifficulty.MEDIUM, Dsa.performance(DsaDifficulty.MEDIUM, DsaOutcome.ALONE, 20), firstSolve = true))
        assertEquals(10, Dsa.xp(DsaDifficulty.MEDIUM, Dsa.performance(DsaDifficulty.MEDIUM, DsaOutcome.ALONE, null), firstSolve = false))
        // Twice the time target: 5/6 of full credit.
        val slow = Dsa.performance(DsaDifficulty.MEDIUM, DsaOutcome.ALONE, 50)
        assertEquals(5.0 / 6, slow, 1e-9)
        // Reading the solution is a fail and costs a little.
        assertEquals(-2, Dsa.xp(DsaDifficulty.MEDIUM, Dsa.performance(DsaDifficulty.MEDIUM, DsaOutcome.SOLUTION, null), firstSolve = true))
    }
}
