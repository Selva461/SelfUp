package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PerformanceTest {
    @Test
    fun `full reward only at perfect, falling steeply below`() {
        assertEquals(10, Performance.xp(10, 1.0))
        assertEquals(7, Performance.xp(10, 0.9))
        assertEquals(5, Performance.xp(10, 0.8))
        assertEquals(3, Performance.xp(10, 0.7))
        assertEquals(2, Performance.xp(10, 0.6))
        assertEquals(1, Performance.xp(10, 0.5))
        assertEquals(0, Performance.xp(10, 0.4))
    }

    @Test
    fun `a very poor attempt costs a tenth of the reward, at least 1`() {
        assertEquals(-1, Performance.xp(10, 0.2))
        assertEquals(-1, Performance.xp(10, 0.0))
        assertEquals(-2, Performance.xp(20, 0.1))
        assertEquals(-5, Performance.xp(50, 0.0))
        assertEquals(-1, Performance.xp(5, 0.0))
    }

    @Test
    fun `out of range performance is clamped and rounding slack counts as perfect`() {
        assertEquals(10, Performance.xp(10, 1.4))
        assertEquals(10, Performance.xp(10, 0.6 / 0.6))
        assertEquals(-1, Performance.xp(10, -3.0))
        assertTrue(Performance.isPerfect(1 - 1e-12))
        assertFailsWith<IllegalArgumentException> { Performance.xp(-1, 1.0) }
    }

    @Test
    fun `targets in both directions`() {
        assertEquals(0.5, Performance.atLeast(5.0, 10.0))
        assertEquals(1.0, Performance.atLeast(12.0, 10.0))
        assertEquals(1.0, Performance.atMost(200.0, 250.0))
        assertEquals(0.5, Performance.atMost(500.0, 250.0))
    }
}
