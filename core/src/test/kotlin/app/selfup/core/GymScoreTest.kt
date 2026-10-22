package app.selfup.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GymScoreTest {
    private var nextId = 0L

    private fun set(session: Long, exercise: String, kg: Double, reps: Int, minute: Long) = GymSet(
        setId = nextId++,
        sessionId = session,
        exercise = exercise,
        weightKg = kg,
        reps = reps,
        variation = "",
        assisted = false,
        loggedAt = Instant.ofEpochSecond(session * 86_400 + minute * 60),
    )

    /** A push day: [sets] sets of each lift. */
    private fun push(session: Long, bench: Double, ohp: Double, sets: Int = 4) =
        (0 until sets).flatMap { i ->
            listOf(set(session, "Barbell Bench Press", bench, 5, i * 3L), set(session, "Overhead Press", ohp, 5, i * 3L + 1), set(session, "Dips", 0.0, 10, i * 3L + 2))
        }

    private fun legs(session: Long) = (0 until 4).flatMap { i -> listOf(set(session, "Back Squat", 100.0, 5, i * 2L), set(session, "Deadlift", 120.0, 5, i * 2L + 1)) }

    @Test
    fun `a first session is judged on its sets alone`() {
        val sessions = GymScore.sessions(push(1, 60.0, 40.0))
        val verdict = GymScore.verdict(sessions.single(), sessions)
        assertNull(verdict.comparedTo)
        assertEquals(1.0, verdict.performance)
        val short = GymScore.sessions(push(2, 60.0, 40.0, sets = 2)).single()
        assertEquals(0.5, GymScore.verdict(short, emptyList()).performance)
    }

    @Test
    fun `matching last push day in sets, volume and every top set is full marks`() {
        val sessions = GymScore.sessions(push(1, 60.0, 40.0) + legs(2) + push(3, 62.5, 40.0))
        val verdict = GymScore.verdict(sessions.last(), sessions)
        assertEquals(1L, verdict.comparedTo)
        assertEquals(1.0, verdict.performance)
    }

    @Test
    fun `a weaker session against the same kind is scaled down`() {
        val sessions = GymScore.sessions(push(1, 60.0, 40.0) + push(2, 50.0, 40.0))
        val verdict = GymScore.verdict(sessions.last(), sessions)
        assertEquals(1L, verdict.comparedTo)
        // Bench dropped; overhead press and dips held.
        assertEquals(2.0 / 3, verdict.strengthPart, 1e-9)
        val volume = sessions.last().volumeKg / sessions.first().volumeKg
        assertEquals(volume, verdict.volumePart, 1e-9)
        assertEquals(volume * (0.5 + 0.5 * 2.0 / 3), verdict.performance, 1e-9)
    }

    @Test
    fun `unweighted sets carry body weight and a leg day is never held against a push day`() {
        val sessions = GymScore.sessions(push(1, 60.0, 40.0) + legs(2), bodyWeightKg = 80.0)
        assertEquals(4 * (60.0 * 5 + 40.0 * 5 + 80.0 * 10), sessions.first().volumeKg)
        assertNull(GymScore.comparable(sessions.last(), sessions))
    }
}
