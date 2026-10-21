package app.selfup.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanCodeTest {
    private val sample = """
        ```
        SELFUP PLAN 1
        # Bench block written for 20:50 gym sessions
        GOAL record:bench:est_1rm 80 12w
        MON Bench 5×5 at 60 kg | Log the top set
        THU Bench 3×3 at 67.5 kg
        GOAL mind:n_back 3 8w
        DAILY Mind session | N-back counts most
        GOAL skill:java_core 12 10w
        WEEKDAYS Java: one concept on the bus
        ```
    """.trimIndent()

    @Test
    fun `reads goals with their weekday tasks`() {
        val goals = assertIs<PlanCodeResult.Ok>(PlanCode.parse(sample)).goals
        assertEquals(3, goals.size)
        val bench = goals[0]
        assertEquals(GoalMeasure.Record(RecordKind.BENCH, Metric.EST_1RM), bench.measure)
        assertEquals(80.0, bench.target)
        assertEquals(12, bench.weeks)
        assertEquals(CustomTask("Bench 5×5 at 60 kg", "Log the top set"), bench.plan.on(DayOfWeek.MONDAY).single())
        assertTrue(bench.plan.on(DayOfWeek.TUESDAY).isEmpty())
        assertEquals(7, goals[1].plan.days.size)
        assertEquals(5, goals[2].plan.days.size)
        assertTrue(goals[2].plan.on(DayOfWeek.SATURDAY).isEmpty())
    }

    @Test
    fun `errors name the line`() {
        val noHeader = assertIs<PlanCodeResult.Error>(PlanCode.parse("GOAL mind:n_back 3 8w"))
        assertEquals(1, noHeader.line)
        val badGoal = assertIs<PlanCodeResult.Error>(PlanCode.parse("SELFUP PLAN 1\nGOAL record:bench:nope 80 12w"))
        assertEquals(2, badGoal.line)
        val orphan = assertIs<PlanCodeResult.Error>(PlanCode.parse("SELFUP PLAN 1\nMON Run 3 km"))
        assertEquals(2, orphan.line)
        val badDay = assertIs<PlanCodeResult.Error>(PlanCode.parse("SELFUP PLAN 1\nGOAL mind:n_back 3 8w\nMONDAY Run"))
        assertEquals(3, badDay.line)
        assertIs<PlanCodeResult.Error>(PlanCode.parse("SELFUP PLAN 1\nGOAL skill:unknown 3 8w"))
        assertIs<PlanCodeResult.Error>(PlanCode.parse("SELFUP PLAN 1\nGOAL mind:n_back 3 99w"))
        assertIs<PlanCodeResult.Error>(PlanCode.parse("SELFUP PLAN 1\nGOAL mind:n_back 3 8w\nMON a\nMON b\nMON c"))
    }

    @Test
    fun `a stored week round-trips and replaces the built-in tasks`() {
        val goals = assertIs<PlanCodeResult.Ok>(PlanCode.parse(sample)).goals
        val week = goals[0].plan
        assertEquals(week, PlanCode.parseWeek(PlanCode.encodeWeek(week)))

        val monday = LocalDate.of(2026, 10, 19)
        val goal = Goal(1, goals[0].measure, 80.0, monday, monday.plusWeeks(12), baseline = 70.0, plan = week)
        val task = GoalPlanner.tasks(goal, 70.0, monday).single()
        assertEquals("Bench 5×5 at 60 kg", task.title)
        assertEquals(PlanAction.LogRecord(RecordKind.BENCH), task.action)
        assertTrue(GoalPlanner.tasks(goal, 70.0, monday.plusDays(1)).isEmpty())
        // Checkpoint retests still come first on test Saturdays.
        assertTrue(GoalPlanner.tasks(goal, 72.0, monday.plusWeeks(3).with(DayOfWeek.SATURDAY)).single().isTest)
    }
}
