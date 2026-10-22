package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkCodeTest {
    private val code = """
        ```
        SELFUP WORK 1
        TITLE Performance-based XP for System
        STAT technical
        DIFFICULTY 4
        QUALITY 4
        COMPLETE 5
        OWNERSHIP 1
        HOURS 2.5
        NOTE Shipped; Claude wrote the code.
        ```
    """.trimIndent()

    @Test
    fun `parses a code and scores it on the rubric`() {
        val report = assertIs<WorkCodeResult.Ok>(WorkCode.parse(code)).report
        assertEquals(Stat.TECHNICAL, report.stat)
        assertEquals(WorkJudge.CLAUDE, report.judge)
        assertEquals(2.5, report.hours)
        assertEquals(40, report.fullXp)
        // (0.3×4 + 0.3×5 + 0.4×1) / 5 = 0.62
        assertEquals(0.62, report.performance, 1e-9)
        assertEquals(9, report.xp)
    }

    @Test
    fun `only a perfect rubric from Claude earns the full reward`() {
        val perfect = WorkReport("X", Stat.TECHNICAL, 3, 5, 5, 5, null, WorkJudge.CLAUDE, "")
        assertEquals(30, perfect.xp)
        assertTrue(perfect.copy(judge = WorkJudge.GROQ).xp < 30)
        assertEquals(-3, perfect.copy(quality = 1, completeness = 1, ownership = 0).xp)
    }

    @Test
    fun `round trips and fingerprints ignore spacing and case`() {
        val report = assertIs<WorkCodeResult.Ok>(WorkCode.parse(code)).report
        assertEquals(report, assertIs<WorkCodeResult.Ok>(WorkCode.parse(WorkCode.write(report))).report)
        assertEquals(WorkCode.fingerprint(report), WorkCode.fingerprint(report.copy(title = "performance-based  XP for system")))
    }

    @Test
    fun `reports what is wrong and where`() {
        assertEquals(1, assertIs<WorkCodeResult.Error>(WorkCode.parse("SELFUP PLAN 1")).line)
        val missing = assertIs<WorkCodeResult.Error>(WorkCode.parse("SELFUP WORK 1\nTITLE X\nDIFFICULTY 2\nQUALITY 3\nCOMPLETE 3"))
        assertEquals("Missing OWNERSHIP", missing.message)
        val range = assertIs<WorkCodeResult.Error>(WorkCode.parse("SELFUP WORK 1\nTITLE X\nDIFFICULTY 9\nQUALITY 3\nCOMPLETE 3\nOWNERSHIP 2"))
        assertEquals(3, range.line)
        assertIs<WorkCodeResult.Error>(WorkCode.parse("SELFUP WORK 1\nTITLE X\nDIFFICULTY 2\nQUALITY 3\nCOMPLETE 3\nOWNERSHIP 2\nSTAT wizardry"))
        // "4/5" is read as 4.
        assertIs<WorkCodeResult.Ok>(WorkCode.parse("SELFUP WORK 1\nTITLE X\nDIFFICULTY 2\nQUALITY 4/5\nCOMPLETE 3\nOWNERSHIP 2"))
    }
}
