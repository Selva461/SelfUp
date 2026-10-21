package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanPromptTest {
    @Test
    fun `every measure the prompt offers parses back`() {
        val measures = PlanPrompt.measures()
        assertTrue(measures.size > 10)
        measures.forEach { (key, _) -> assertEquals(key, GoalMeasure.fromKey(key).key) }
        assertTrue("record:bench:EST_1RM" in PlanPrompt.system())
        assertTrue("supplements" in PlanPrompt.system())
    }

    @Test
    fun `extract drops chatter and fences around the code`() {
        val reply = """
            Sure! Here's your plan:
            ```
            SELFUP PLAN 1
            GOAL mind:n_back 3 8w
            DAILY Mind session | Focus on n-back
            ```
            Good luck, you've got this.
        """.trimIndent()
        val code = PlanPrompt.extract(reply)
        assertEquals("SELFUP PLAN 1\nGOAL mind:n_back 3 8w\nDAILY Mind session | Focus on n-back", code)
        assertIs<PlanCodeResult.Ok>(PlanCode.parse(code))

        val unfenced = "SELFUP PLAN 1\nGOAL mind:n_back 3 8w\nDAILY Mind session\nLet me know if you want changes."
        assertIs<PlanCodeResult.Ok>(PlanCode.parse(PlanPrompt.extract(unfenced)))
    }

    @Test
    fun `repair quotes the line`() {
        assertTrue("Line 3" in PlanPrompt.repair(PlanCodeResult.Error(3, "bad")))
    }
}
