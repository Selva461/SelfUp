package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StreaksTest {
    @Test
    fun `a clear day grows the streak and the best`() {
        val (next, outcome) = Streaks.close(StreakState(current = 3, best = 3), due = 4, missed = 0)
        assertEquals(DayOutcome.CLEAR, outcome)
        assertEquals(StreakState(current = 4, best = 4), next)
    }

    @Test
    fun `every seventh clear day earns a shield up to the cap`() {
        var state = StreakState()
        repeat(21) { state = Streaks.close(state, due = 1, missed = 0).first }
        assertEquals(StreakState(current = 21, best = 21, shields = Streaks.MAX_SHIELDS), state)
    }

    @Test
    fun `a shield absorbs a miss and keeps the streak`() {
        val (next, outcome) = Streaks.close(StreakState(current = 9, best = 12, shields = 1), due = 3, missed = 2)
        assertEquals(DayOutcome.SHIELDED, outcome)
        assertEquals(StreakState(current = 9, best = 12, shields = 0), next)
    }

    @Test
    fun `a miss without a shield resets the streak but not the best`() {
        val (next, outcome) = Streaks.close(StreakState(current = 9, best = 12), due = 3, missed = 1)
        assertEquals(DayOutcome.BROKEN, outcome)
        assertEquals(StreakState(current = 0, best = 12), next)
    }

    @Test
    fun `a day with nothing due changes nothing`() {
        val state = StreakState(current = 2, best = 5, shields = 1)
        assertEquals(state to DayOutcome.EMPTY, Streaks.close(state, due = 0, missed = 0))
    }

    @Test
    fun `state survives encode and decode and bad input falls back to zero`() {
        val state = StreakState(current = 4, best = 10, shields = 2)
        assertEquals(state, StreakState.decode(state.encode()))
        assertEquals(StreakState(), StreakState.decode("junk"))
        assertEquals(StreakState(), StreakState.decode("5:2:0"))
        assertEquals(StreakState(), StreakState.decode(null))
    }
}
