package app.selfup.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalsTest {
    // A Monday, so week boundaries line up with weekdays.
    private val start = LocalDate.of(2026, 10, 19)
    private val bench = Goal(
        id = 1,
        measure = GoalMeasure.Record(RecordKind.BENCH, Metric.EST_1RM),
        target = 80.0,
        start = start,
        deadline = start.plusWeeks(12),
        baseline = 68.0,
    )

    @Test
    fun `measures round-trip through their keys`() {
        listOf(
            GoalMeasure.Record(RecordKind.RUN, Metric.PACE),
            GoalMeasure.Skill("java_core"),
            GoalMeasure.Mind(MindGame.N_BACK),
        ).forEach { assertEquals(it, GoalMeasure.fromKey(it.key)) }
    }

    @Test
    fun `expected line runs from baseline to target`() {
        assertEquals(68.0, GoalRules.expected(bench, start)!!, 1e-9)
        assertEquals(74.0, GoalRules.expected(bench, start.plusWeeks(6))!!, 1e-9)
        assertEquals(80.0, GoalRules.expected(bench, start.plusWeeks(20))!!, 1e-9)
        assertEquals(0.5, GoalRules.progress(bench, 74.0), 1e-9)
        assertEquals(12, GoalRules.totalWeeks(bench))
    }

    @Test
    fun `status reads ahead, on track, behind, reached and missed`() {
        val mid = start.plusWeeks(6) // expected 74, tolerance 1.2
        assertEquals(GoalStatus.AHEAD, GoalRules.status(bench, 76.0, mid))
        assertEquals(GoalStatus.ON_TRACK, GoalRules.status(bench, 73.5, mid))
        assertEquals(GoalStatus.BEHIND, GoalRules.status(bench, 71.0, mid))
        assertEquals(GoalStatus.REACHED, GoalRules.status(bench, 80.0, mid))
        assertEquals(GoalStatus.MISSED, GoalRules.status(bench, 75.0, bench.deadline.plusDays(1)))
        assertEquals(GoalStatus.NEEDS_BASELINE, GoalRules.status(bench.copy(baseline = null), null, mid))
    }

    @Test
    fun `lower-is-better goals flip the comparisons`() {
        val pace = Goal(2, GoalMeasure.Record(RecordKind.RUN, Metric.PACE), target = 5.5, start = start, deadline = start.plusWeeks(10), baseline = 6.5)
        assertFalse(pace.rising)
        val mid = start.plusWeeks(5) // expected 6.0
        assertEquals(GoalStatus.AHEAD, GoalRules.status(pace, 5.8, mid))
        assertEquals(GoalStatus.BEHIND, GoalRules.status(pace, 6.3, mid))
        assertTrue(pace.reached(5.45))
    }

    @Test
    fun `behind projects a later deadline and ahead raises the target`() {
        val mid = start.plusWeeks(6)
        val projected = assertNotNull(GoalRules.projectedDeadline(bench, 71.0, mid))
        assertTrue(projected.isAfter(bench.deadline))
        assertNull(GoalRules.projectedDeadline(bench, 68.0, mid))
        val raised = assertNotNull(GoalRules.raisedTarget(bench, 76.0, mid))
        assertEquals(85.0, raised, 1e-9) // +8 kg in 6 weeks, 6 more weeks to go
        assertNull(GoalRules.raisedTarget(bench, 72.0, mid))
    }

    @Test
    fun `checkpoints fall every 4th week and in the last week`() {
        assertFalse(GoalRules.isCheckpointWeek(bench, start))
        assertTrue(GoalRules.isCheckpointWeek(bench, start.plusWeeks(3)))
        assertTrue(GoalRules.isCheckpointWeek(bench, start.plusWeeks(7)))
        assertTrue(GoalRules.isCheckpointWeek(bench, start.plusWeeks(11)))
        assertFalse(GoalRules.isCheckpointWeek(bench, start.plusWeeks(9)))
    }

    @Test
    fun `a goal without a baseline asks for a test first`() {
        val tasks = GoalPlanner.tasks(bench.copy(baseline = null), null, start.plusDays(1))
        assertEquals(1, tasks.size)
        assertTrue(tasks.single().isTest)
        assertEquals(PlanAction.LogRecord(RecordKind.BENCH), tasks.single().action)
    }

    @Test
    fun `lift plan prescribes a rounded top set on lift days and deloads in test weeks`() {
        val monday = start.plusWeeks(1)
        val task = GoalPlanner.tasks(bench, 70.0, monday).single()
        // End of week 2 sits at 70 kg on the line; 70 / (1 + 5/30) = 60 kg.
        assertEquals("Bench press: top set 60 kg × 5", task.title)
        assertTrue(GoalPlanner.tasks(bench, 70.0, monday.plusDays(1)).isEmpty())

        val testWeek = start.plusWeeks(3)
        val light = GoalPlanner.tasks(bench, 72.0, testWeek).single()
        assertTrue(light.detail.startsWith("Lighter"))
        val saturday = testWeek.with(DayOfWeek.SATURDAY)
        assertTrue(GoalPlanner.tasks(bench, 72.0, saturday).single().isTest)
    }

    @Test
    fun `run volume grows at most ten percent a week`() {
        val run = Goal(3, GoalMeasure.Record(RecordKind.RUN, Metric.DISTANCE), target = 10.0, start = start, deadline = start.plusWeeks(12), baseline = 4.0)
        val w1 = GoalPlanner.runWeeklyKm(run, start)
        val w2 = GoalPlanner.runWeeklyKm(run, start.plusWeeks(1))
        assertEquals(8.0, w1, 1e-9)
        assertTrue(w2 <= w1 * 1.1 + 1e-9)
        assertTrue(GoalPlanner.runWeeklyKm(run, start.plusWeeks(3)) < GoalPlanner.runWeeklyKm(run, start.plusWeeks(2)))
        val saturday = GoalPlanner.tasks(run, 4.0, start.with(DayOfWeek.SATURDAY)).single()
        assertTrue(saturday.title.startsWith("Long run 3.2 km"), saturday.title)
    }

    @Test
    fun `no more than two tasks a day and three active goals`() {
        val saturday = start.plusWeeks(3).with(DayOfWeek.SATURDAY)
        val skill = Goal(4, GoalMeasure.Skill("java_core"), target = 10.0, start = start, deadline = start.plusWeeks(8), baseline = 0.0)
        assertTrue(GoalPlanner.tasks(skill, 2.0, saturday).size <= GoalRules.TASKS_PER_DAY)
        assertEquals(2, GoalPlanner.tasks(skill, 2.0, saturday).size)
        assertTrue(GoalRules.canAdd(2))
        assertFalse(GoalRules.canAdd(3))
        assertEquals("Java Core: 10 concepts solid by 14 Dec", GoalPlanner.title(skill))
    }
}
