package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrillTest {
    @Test
    fun `a fail run counts unexcused failed days until the first clean one`() {
        val days = listOf(DayOutcomeRow(2, false), DayOutcomeRow(1, false), DayOutcomeRow(3, true), DayOutcomeRow(1, false))
        assertEquals(2, Drill.failRun(days))
        assertEquals(0, Drill.failRun(listOf(DayOutcomeRow(0, false), DayOutcomeRow(4, false))))
    }

    @Test
    fun `a clean day gets no verdict and a failed run is called out`() {
        assertNull(Drill.verdict(missed = 0, xpLost = 0, streakLost = 0, failRun = 0, seed = 1))
        val verdict = Drill.verdict(missed = 3, xpLost = 30, streakLost = 5, failRun = 3, seed = 0)!!
        assertTrue("3 quests" in verdict)
        assertTrue("3 days" in verdict)
        assertTrue("5-day streak" in verdict)
    }

    @Test
    fun `nags escalate and name the fail run`() {
        val first = Drill.nag(open = 2, level = 0, failRun = 0, seed = 0)
        val later = Drill.nag(open = 2, level = 4, failRun = 3, seed = 0)
        assertTrue(first.startsWith("2 quests"))
        assertTrue(later.startsWith("You failed 3 days running."))
    }

    @Test
    fun `lines are stable for a seed and use the numbers`() {
        assertEquals(Drill.lockdown("Webnovel", 34, 30, 7), Drill.lockdown("Webnovel", 34, 30, 7))
        assertTrue("1 minute " in Drill.finalWarning("Webnovel", 1, 0))
        assertTrue("3 times" in Drill.order(dodges = 3, seed = 0))
        assertTrue("2 done" in Drill.reportDone(done = 2, open = 1, seed = 0))
    }
}
