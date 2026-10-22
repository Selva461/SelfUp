package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpendingTest {
    private val week = listOf(200.0, 300.0, 250.0, 400.0, 100.0, 300.0, 250.0)

    @Test
    fun `typical day is the median of recent logged days`() {
        assertNull(Spending.typical(week.take(6)))
        assertEquals(250.0, Spending.typical(week))
        assertEquals(275.0, Spending.typical(week + 300.0))
    }

    @Test
    fun `spending at or under typical is full, more is scaled down`() {
        assertEquals(1.0, Spending.performance(500.0, week.take(3)))
        assertEquals(1.0, Spending.performance(250.0, week))
        assertEquals(1.0, Spending.performance(0.0, week))
        assertEquals(0.5, Spending.performance(500.0, week))
        assertEquals(-1, Performance.xp(10, Spending.performance(1000.0, week)))
    }
}
