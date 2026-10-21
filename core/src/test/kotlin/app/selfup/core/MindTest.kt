package app.selfup.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MindTest {
    @Test
    fun `digit span grows on success and ends after two misses in a row`() {
        val run = DigitSpan(Random(1))
        val first = run.next()
        assertEquals(4, first.size)
        assertTrue(first.zipWithNext().none { (a, b) -> a == b })
        assertTrue(run.answer(first, first))
        assertEquals(5, run.length)

        val second = run.next()
        assertFalse(run.answer(second, second.reversed() + 0))
        assertFalse(run.isOver)
        assertTrue(run.answer(second, second)) // a success clears the miss
        val third = run.next()
        run.answer(third, emptyList())
        run.answer(third, emptyList())
        assertTrue(run.isOver)
        assertEquals(5, run.best)
        assertFailsWith<IllegalStateException> { run.answer(third, third) }
    }

    @Test
    fun `n-back blocks have honest targets and accuracy counts both kinds of right answer`() {
        val n = 2
        val seq = NBack.block(n, Random(7))
        assertEquals(NBack.TRIALS + n, seq.size)
        val targets = (seq.indices).count { NBack.isTarget(seq, n, it) }
        assertTrue(targets in 2..12, "targets=$targets")

        val perfect = seq.indices.map { NBack.isTarget(seq, n, it) }
        assertEquals(1.0, NBack.accuracy(seq, n, perfect))
        val never = seq.indices.map { false }
        assertEquals(1.0 - targets.toDouble() / NBack.TRIALS, NBack.accuracy(seq, n, never), 1e-9)
    }

    @Test
    fun `n-back adapts up, holds, and drops but never below one`() {
        assertEquals(3, NBack.nextN(2, 0.85))
        assertEquals(2, NBack.nextN(2, 0.6))
        assertEquals(1, NBack.nextN(2, 0.4))
        assertEquals(1, NBack.nextN(1, 0.1))
    }

    @Test
    fun `speed math answers are right at every level`() {
        val random = Random(3)
        repeat(200) {
            val level = it % 4
            val p = SpeedMath.problem(level, random)
            val parts = p.text.split(' ')
            val a = parts[0].toInt()
            val b = parts[2].toInt()
            val expected = when (parts[1]) {
                "+" -> a + b
                "−" -> a - b
                "×" -> a * b
                "÷" -> a / b
                else -> error(p.text)
            }
            assertEquals(expected, p.answer, p.text)
            assertTrue(p.answer >= 0, p.text)
        }
        assertEquals(0, SpeedMath.levelFor(4))
        assertEquals(1, SpeedMath.levelFor(5))
        assertEquals(3, SpeedMath.levelFor(40))
    }

    @Test
    fun `grid grows so at most half of it lights up`() {
        assertEquals(3, GridRecall.sizeFor(3))
        assertEquals(3, GridRecall.sizeFor(4))
        assertEquals(4, GridRecall.sizeFor(5))
        assertEquals(4, GridRecall.sizeFor(8))
        assertEquals(5, GridRecall.sizeFor(9))
        val pattern = GridRecall.pattern(6, Random(2))
        assertEquals(6, pattern.size)
        assertTrue(pattern.all { it in 0 until 16 })
    }

    @Test
    fun `reaction uses the median and flags anticipation`() {
        assertEquals(260L, Reaction.median(listOf(300L, 250L, 900L, 240L, 260L)))
        assertEquals(255L, Reaction.median(listOf(250L, 260L)))
        assertTrue(Reaction.isFalseStart(80))
        assertFalse(Reaction.isFalseStart(180))
    }

    @Test
    fun `xp once a day per game plus a bonus for a new best`() {
        assertEquals(MindRules.SESSION_XP, MindRules.xpFor(MindGame.DIGIT_SPAN, 6.0, emptyList(), playedToday = false))
        assertEquals(MindRules.SESSION_XP + MindRules.BEST_XP, MindRules.xpFor(MindGame.DIGIT_SPAN, 7.0, listOf(5.0, 6.0), false))
        assertEquals(0, MindRules.xpFor(MindGame.DIGIT_SPAN, 6.0, listOf(6.0), playedToday = true))
        // Lower is better for reaction.
        assertEquals(MindRules.BEST_XP, MindRules.xpFor(MindGame.REACTION, 230.0, listOf(260.0), playedToday = true))
    }

    @Test
    fun `sessions rotate so every game is played within two days`() {
        val day1 = MindRules.sessionGames(100)
        val day2 = MindRules.sessionGames(101)
        assertEquals(3, day1.toSet().size)
        assertEquals(MindGame.entries.toSet(), (day1 + day2).toSet())
        MindGame.entries.forEach { assertEquals(it, MindGame.fromKey(it.key)) }
    }
}
