package app.selfup.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuestChecksTest {
    private fun quest(title: String, input: CompletionInput = CompletionInput.Tap) =
        Quest(id = 1, title = title, difficulty = Difficulty.MEDIUM, stats = setOf(Stat.LIFE), schedule = Schedule.Recurring(), input = input)

    @Test
    fun `the default quests the System can see are checked`() {
        val checks = DefaultQuests.all.associate { it.title to QuestChecks.of(it, gymConnected = true) }
        assertEquals(AutoCheck.GYM, checks["Finish a gym session"])
        assertEquals(AutoCheck.MEALS, checks["Log every meal today"])
        assertEquals(AutoCheck.LAST_MEAL, checks["Finish last meal 2+ hours before bed"])
        assertEquals(AutoCheck.PHONE_OFF, checks["Phone off by 23:00"])
        assertEquals(AutoCheck.PHONE_OFF, checks["In bed by 00:00"])
        assertNull(checks["Spend 30 minutes building or learning"])
        assertNull(checks["Log today's spending"])
    }

    @Test
    fun `gym is a tap until Gym is linked, and make-up quests stay manual`() {
        assertNull(QuestChecks.of(quest("Finish a gym session"), gymConnected = false))
        val makeUp = DailyReset.penaltyQuest(quest("Phone off by 23:00"), LocalDate.of(2026, 10, 20))
        assertNull(QuestChecks.of(makeUp, gymConnected = true))
        val oneTime = quest("Phone off by 22:00").copy(schedule = Schedule.OneTime())
        assertNull(QuestChecks.of(oneTime, gymConnected = true))
    }
}
