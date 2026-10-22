package app.selfup.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillsTest {
    private val d0 = LocalDate.of(2026, 10, 21)
    private fun day(n: Long) = d0.plusDays(n)
    private fun quiz(n: Long, correct: Int) = SkillEvent.Quiz(day(n), correct, 5)

    @Test
    fun `reading meets a concept and a pass recalls it`() {
        assertEquals(Mastery.MET, SkillRules.replay(listOf(SkillEvent.Read(d0))).mastery)
        assertEquals(Mastery.RECALLED, SkillRules.replay(listOf(quiz(0, 3))).mastery)
        assertEquals(Mastery.UNSEEN, SkillRules.replay(listOf(quiz(0, 2))).mastery)
    }

    @Test
    fun `solid needs two strong scores at least three days apart`() {
        assertEquals(Mastery.RECALLED, SkillRules.replay(listOf(quiz(0, 4), quiz(2, 5))).mastery)
        assertEquals(Mastery.SOLID, SkillRules.replay(listOf(quiz(0, 4), quiz(3, 4))).mastery)
        // A pass in between doesn't break the run; a fail does.
        assertEquals(Mastery.SOLID, SkillRules.replay(listOf(quiz(0, 4), quiz(1, 3), quiz(4, 5))).mastery)
        assertEquals(Mastery.RECALLED, SkillRules.replay(listOf(quiz(0, 4), quiz(1, 2), quiz(4, 5))).mastery)
    }

    @Test
    fun `applied and teachable must be earned in order`() {
        val solid = listOf(quiz(0, 5), quiz(3, 5))
        assertEquals(Mastery.RECALLED, SkillRules.replay(listOf(quiz(0, 5), SkillEvent.Applied(day(1)))).mastery)
        assertEquals(Mastery.APPLIED, SkillRules.replay(solid + SkillEvent.Applied(day(4))).mastery)
        assertEquals(Mastery.SOLID, SkillRules.replay(solid + SkillEvent.Taught(day(4))).mastery)
        assertEquals(
            Mastery.TEACHABLE,
            SkillRules.replay(solid + SkillEvent.Applied(day(4)) + SkillEvent.Taught(day(5))).mastery,
        )
    }

    @Test
    fun `a failed review drops a solid concept one level, never below solid minus one`() {
        val solid = listOf(quiz(0, 5), quiz(3, 5))
        assertEquals(Mastery.RECALLED, SkillRules.replay(solid + quiz(40, 1)).mastery)
        val applied = solid + SkillEvent.Applied(day(4))
        assertEquals(Mastery.SOLID, SkillRules.replay(applied + quiz(40, 1)).mastery)
        // Early levels don't drop on a fail.
        assertEquals(Mastery.RECALLED, SkillRules.replay(listOf(quiz(0, 3), quiz(1, 0))).mastery)
    }

    @Test
    fun `reviews space out with each pass and reset on a fail`() {
        val s1 = SkillRules.replay(listOf(quiz(0, 3)))
        assertEquals(day(3), s1.nextReview()) // one pass → 3 days
        val s2 = SkillRules.replay(listOf(quiz(0, 3), quiz(3, 4)))
        assertEquals(day(10), s2.nextReview()) // two passes → 7 days
        val failed = SkillRules.replay(listOf(quiz(0, 3), quiz(3, 1)))
        assertEquals(day(4), failed.nextReview()) // back to 1 day
        assertTrue(failed.isDue(day(4)))
        assertFalse(failed.isDue(day(3)))
    }

    @Test
    fun `solid concepts fade after thirty days untested`() {
        val solid = SkillRules.replay(listOf(quiz(0, 5), quiz(3, 5)))
        assertFalse(solid.isFading(day(32)))
        assertTrue(solid.isFading(day(33)))
        assertFalse(SkillRules.replay(listOf(quiz(0, 3))).isFading(day(90)))
    }

    @Test
    fun `quiz xp follows the score, the first try of the day counts, plus a bonus for reaching solid`() {
        val start = ConceptState()
        assertEquals(SkillRules.QUIZ_XP, SkillRules.xpFor(start, quiz(0, 5), emptyList()))
        assertEquals(5, SkillRules.xpFor(start, quiz(0, 4), emptyList()))
        assertEquals(2, SkillRules.xpFor(start, quiz(0, 3), emptyList()))
        assertEquals(0, SkillRules.xpFor(start, quiz(0, 2), emptyList()))
        assertEquals(-1, SkillRules.xpFor(start, quiz(0, 1), emptyList()))
        assertEquals(-1, SkillRules.xpFor(start, quiz(0, 0), emptyList()))
        // A retake the same day earns nothing, even when perfect.
        assertEquals(0, SkillRules.xpFor(SkillRules.replay(listOf(quiz(0, 2))), quiz(0, 5), listOf(quiz(0, 2))))

        val beforeSolid = SkillRules.replay(listOf(quiz(0, 4)))
        assertEquals(5 + SkillRules.MASTERY_BONUS_XP, SkillRules.xpFor(beforeSolid, quiz(3, 4), emptyList()))
    }

    private fun concept(id: String) = Concept(
        id = id,
        section = "Basics",
        title = id,
        card = listOf("A point"),
        questions = List(5) { Question("Q$it", listOf("a", "b"), 0, "because") },
        task = "Build it",
        keyPoints = listOf("one", "two"),
    )

    @Test
    fun `session puts due reviews first, then one new concept`() {
        val pack = SkillPack("p", "Pack", Stat.TECHNICAL, listOf(concept("a"), concept("b"), concept("c"), concept("d")))
        val states = mapOf(
            "a" to SkillRules.replay(listOf(quiz(0, 3))), // due day 3
            "b" to SkillRules.replay(listOf(quiz(0, 3), quiz(3, 4))), // due day 10
        )
        val onDay5 = StudyPlanner.session(pack, states, day(5))
        assertEquals(listOf("a"), onDay5.reviews.map { it.id })
        assertEquals(listOf("c"), onDay5.fresh.map { it.id })

        val onDay10 = StudyPlanner.session(pack, states, day(10))
        assertEquals(listOf("a", "b"), onDay10.reviews.map { it.id })
    }

    @Test
    fun `progress counts mastery out of five per concept`() {
        val pack = SkillPack("p", "Pack", Stat.TECHNICAL, listOf(concept("a"), concept("b")))
        val states = mapOf("a" to ConceptState(mastery = Mastery.TEACHABLE))
        assertEquals(0.5, StudyPlanner.progress(pack, states), 1e-9)
        assertEquals(1, StudyPlanner.breakdown(pack, states)[Mastery.UNSEEN])
    }

    @Test
    fun `malformed content is rejected`() {
        assertFailsWith<IllegalArgumentException> { Question("Q", listOf("a", "a"), 0, "x") }
        assertFailsWith<IllegalArgumentException> { Question("Q", listOf("a", "b"), 2, "x") }
        assertFailsWith<IllegalArgumentException> { concept("Bad Id") }
    }
}
