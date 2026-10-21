package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordsTest {
    private val t0 = Instant.parse("2026-10-21T15:00:00Z")

    private fun bench(kg: Double, reps: Int, day: Long = 0) =
        RecordEntry(RecordKind.BENCH, t0.plusSeconds(day * 86_400), weightKg = kg, reps = reps)

    private fun run(km: Double, seconds: Int, day: Long = 0) =
        RecordEntry(RecordKind.RUN, t0.plusSeconds(day * 86_400), distanceKm = km, seconds = seconds)

    @Test
    fun `epley estimate, with a single counting as itself`() {
        assertEquals(100.0, RecordMath.estimatedOneRepMax(100.0, 1))
        assertEquals(80.0 * (1 + 5 / 30.0), RecordMath.estimatedOneRepMax(80.0, 5), 1e-9)
        assertEquals(120.0, RecordMath.estimatedOneRepMax(60.0, 30), 1e-9)
        assertFailsWith<IllegalArgumentException> { RecordMath.estimatedOneRepMax(60.0, 31) }
    }

    @Test
    fun `entries must carry the fields their kind needs`() {
        assertFailsWith<IllegalArgumentException> { RecordEntry(RecordKind.BENCH, t0, weightKg = 60.0) }
        assertFailsWith<IllegalArgumentException> { RecordEntry(RecordKind.RUN, t0, distanceKm = 5.0) }
        assertFailsWith<IllegalArgumentException> { RecordEntry(RecordKind.PLANK, t0, seconds = 0) }
        RecordEntry(RecordKind.PLANK, t0, seconds = 90)
    }

    @Test
    fun `first entry is a baseline, not a record`() {
        assertEquals(emptyList(), RecordMath.personalBests(emptyList(), bench(60.0, 5)))
    }

    @Test
    fun `a heavier estimate beats the best, an equal one does not`() {
        val history = listOf(bench(60.0, 5), bench(62.5, 3, day = 3))
        val best = RecordMath.bests(history).getValue(Metric.EST_1RM)
        assertEquals(70.0, best, 1e-9) // 60 × (1 + 5/30) = 70 beats 62.5 × 1.1 = 68.75

        val pr = RecordMath.personalBests(history, bench(65.0, 3, day = 7)).single()
        assertEquals(Metric.EST_1RM, pr.metric)
        assertEquals(70.0, pr.previous, 1e-9)
        assertEquals(71.5, pr.value, 1e-9)
        assertEquals(RecordMath.PR_XP, RecordMath.xpFor(listOf(pr)))

        assertEquals(emptyList(), RecordMath.personalBests(history, bench(70.0, 1, day = 7)))
        assertEquals(0, RecordMath.xpFor(emptyList()))
    }

    @Test
    fun `runs set distance and pace records separately, and short runs set no pace`() {
        val history = listOf(run(5.0, 30 * 60)) // 6:00 per km
        val faster = RecordMath.personalBests(history, run(3.0, 15 * 60, day = 2)) // 5:00, shorter
        assertEquals(listOf(Metric.PACE), faster.map { it.metric })
        assertEquals(5.0, faster.single().value, 1e-9)

        val longer = RecordMath.personalBests(history, run(7.0, 49 * 60, day = 4)) // 7:00, longer
        assertEquals(listOf(Metric.DISTANCE), longer.map { it.metric })

        assertFalse(Metric.PACE in RecordMath.metrics(run(0.5, 90)))
    }

    @Test
    fun `body weight is a trend, never a record`() {
        val history = listOf(RecordEntry(RecordKind.BODY_WEIGHT, t0, weightKg = 78.0))
        val next = RecordEntry(RecordKind.BODY_WEIGHT, t0.plusSeconds(604_800), weightKg = 76.5)
        assertEquals(emptyList(), RecordMath.personalBests(history, next))
        assertEquals(emptyMap(), RecordMath.bests(history))
    }

    @Test
    fun `history of another kind is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RecordMath.personalBests(listOf(run(5.0, 1800)), bench(60.0, 5))
        }
    }

    @Test
    fun `record keys round-trip`() {
        RecordKind.entries.forEach { assertEquals(it, RecordKind.fromKey(it.key)) }
    }

    @Test
    fun `test day is the first saturday of the month`() {
        assertTrue(TestDay.isTestDay(LocalDate.of(2026, 11, 7)))
        assertFalse(TestDay.isTestDay(LocalDate.of(2026, 11, 14)))
        assertTrue(TestDay.isTestDay(LocalDate.of(2026, 8, 1))) // the 1st itself is a Saturday
        assertEquals(LocalDate.of(2026, 11, 7), TestDay.next(LocalDate.of(2026, 10, 21)))
        assertEquals(LocalDate.of(2026, 11, 7), TestDay.next(LocalDate.of(2026, 11, 7)))
        assertEquals(LocalDate.of(2026, 12, 5), TestDay.next(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `test day quest shows on its day only`() {
        val day = LocalDate.of(2026, 11, 7)
        val quest = TestDay.quest(day)
        assertTrue(quest.schedule.isDueOn(day))
        assertFalse(quest.schedule.isDueOn(day.plusDays(1)))
        assertFalse(quest.isPenalizable)
        assertFailsWith<IllegalArgumentException> { TestDay.quest(day.plusDays(1)) }
    }
}
