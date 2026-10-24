package app.selfup.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsTest {
    @Test
    fun `ids and titles are unique`() {
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
        assertEquals(Achievements.all.size, Achievements.all.map { it.title }.toSet().size)
    }

    @Test
    fun `nothing is earned on a fresh start`() {
        assertTrue(Achievements.newlyEarned(HunterFacts(), emptySet()).isEmpty())
    }

    @Test
    fun `earned achievements are reported once`() {
        val facts = HunterFacts(bestStreak = 8, personalBests = 1)
        assertEquals(listOf("streak_3", "streak_7", "pr_1"), Achievements.newlyEarned(facts, emptySet()).map { it.id })
        assertEquals(listOf("pr_1"), Achievements.newlyEarned(facts, setOf("streak_3", "streak_7")).map { it.id })
    }

    @Test
    fun `all-rounder needs every launch stat, not the total`() {
        val lopsided = HunterFacts(levels = mapOf(Stat.STRENGTH to 30))
        val balanced = HunterFacts(levels = Stat.launchSet.associateWith { 5 })
        val allRounder = Achievements.byId("balance_5")!!
        assertEquals(false, allRounder.earned(lopsided))
        assertEquals(true, allRounder.earned(balanced))
    }

    @Test
    fun `progress is capped at the target`() {
        val streak = Achievements.byId("streak_3")!!
        assertEquals(3, streak.current(HunterFacts(bestStreak = 40)))
        assertEquals(1f, streak.fraction(HunterFacts(bestStreak = 40)))
    }
}

class GoldTest {
    @Test
    fun `a day pays its net xp plus a clear bonus`() {
        assertEquals(60, Gold.forDay(50, DayOutcome.CLEAR))
        assertEquals(50, Gold.forDay(50, DayOutcome.SHIELDED))
        assertEquals(0, Gold.forDay(-15, DayOutcome.BROKEN))
    }

    @Test
    fun `default rewards are valid and priced above a day's clear bonus`() {
        assertTrue(Gold.defaultRewards.all { it.price > Gold.CLEAR_DAY_BONUS })
        assertEquals(1, Gold.defaultRewards.count { it.kind == RewardKind.SCREEN_MINUTES })
    }
}
