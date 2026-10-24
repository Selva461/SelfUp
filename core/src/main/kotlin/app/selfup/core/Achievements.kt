package app.selfup.core

/** Everything the achievements look at, counted from the database. */
public data class HunterFacts(
    val bestStreak: Int = 0,
    val clearDays: Int = 0,
    /** Level of each launch stat. */
    val levels: Map<Stat, Int> = emptyMap(),
    val personalBests: Int = 0,
    val trainingDays: Int = 0,
    val quizzes: Int = 0,
    val conceptsStudied: Int = 0,
    val mindGames: Int = 0,
    val workLogs: Int = 0,
    val lowScrollDays: Int = 0,
    val phoneOffVerified: Int = 0,
    val goalsReached: Int = 0,
    val dungeonsCleared: Int = 0,
    val sRankDungeons: Int = 0,
    val purchases: Int = 0,
    /** DSA problems solved alone at least once, and ones mastered on the re-solve ladder. */
    val dsaSolved: Int = 0,
    val dsaMastered: Int = 0,
) {
    val totalLevel: Int get() = Stat.launchSet.sumOf { levels[it] ?: 1 }
    val lowestLevel: Int get() = Stat.launchSet.minOf { levels[it] ?: 1 }
}

public enum class Tier(public val displayName: String, public val gold: Int) {
    BRONZE("Bronze", 20),
    SILVER("Silver", 50),
    GOLD("Gold", 100),
    LEGEND("Legend", 300),
}

public enum class AchievementGroup(public val displayName: String) {
    STREAK("Streak"),
    LEVEL("Level"),
    DUNGEON("Dungeon"),
    BODY("Body"),
    MIND("Mind"),
    FOCUS("Focus"),
    GOALS("Goals"),
}

/**
 * An achievement, which also unlocks [title] to wear on the status window. [measure] reads
 * how far along it is; it is earned at [target]. Some stay hidden until earned.
 */
public data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val tier: Tier,
    val group: AchievementGroup,
    val target: Int,
    val hidden: Boolean = false,
    val measure: (HunterFacts) -> Int,
) {
    public fun current(facts: HunterFacts): Int = measure(facts).coerceAtMost(target)

    public fun earned(facts: HunterFacts): Boolean = measure(facts) >= target

    public fun fraction(facts: HunterFacts): Float = current(facts).toFloat() / target
}

public object Achievements {
    public val all: List<Achievement> = listOf(
        Achievement("streak_3", "The Awakened", "Clear 3 days in a row", Tier.BRONZE, AchievementGroup.STREAK, 3) { it.bestStreak },
        Achievement("streak_7", "Persistent", "Clear 7 days in a row", Tier.SILVER, AchievementGroup.STREAK, 7) { it.bestStreak },
        Achievement("streak_30", "Iron Will", "Clear 30 days in a row", Tier.GOLD, AchievementGroup.STREAK, 30) { it.bestStreak },
        Achievement("streak_100", "Unbreakable", "Clear 100 days in a row", Tier.LEGEND, AchievementGroup.STREAK, 100) { it.bestStreak },
        Achievement("clear_50", "Daily Grinder", "Clear 50 days in total", Tier.SILVER, AchievementGroup.STREAK, 50) { it.clearDays },

        Achievement("level_15", "Rising Hunter", "Reach total level 15", Tier.BRONZE, AchievementGroup.LEVEL, 15) { it.totalLevel },
        Achievement("level_40", "Elite Hunter", "Reach total level 40", Tier.SILVER, AchievementGroup.LEVEL, 40) { it.totalLevel },
        Achievement("level_100", "National Level", "Reach total level 100", Tier.GOLD, AchievementGroup.LEVEL, 100) { it.totalLevel },
        Achievement("balance_5", "All-Rounder", "Get every stat to level 5", Tier.SILVER, AchievementGroup.LEVEL, 5) { it.lowestLevel },
        Achievement("balance_20", "Shadow Monarch", "Get every stat to level 20", Tier.LEGEND, AchievementGroup.LEVEL, 20, hidden = true) { it.lowestLevel },

        Achievement("dungeon_1", "Dungeon Breaker", "Clear a weekly dungeon (rank C or better)", Tier.BRONZE, AchievementGroup.DUNGEON, 1) { it.dungeonsCleared },
        Achievement("dungeon_8", "Raid Leader", "Clear 8 weekly dungeons", Tier.GOLD, AchievementGroup.DUNGEON, 8) { it.dungeonsCleared },
        Achievement("dungeon_s", "S-Rank", "Finish a dungeon with every objective met", Tier.GOLD, AchievementGroup.DUNGEON, 1) { it.sRankDungeons },

        Achievement("pr_1", "First Blood", "Set a personal best", Tier.BRONZE, AchievementGroup.BODY, 1) { it.personalBests },
        Achievement("pr_10", "Record Breaker", "Set 10 personal bests", Tier.SILVER, AchievementGroup.BODY, 10) { it.personalBests },
        Achievement("train_50", "Iron Body", "Train on 50 days", Tier.GOLD, AchievementGroup.BODY, 50) { it.trainingDays },

        Achievement("quiz_25", "Student", "Take 25 skill quizzes", Tier.BRONZE, AchievementGroup.MIND, 25) { it.quizzes },
        Achievement("concepts_30", "Scholar", "Study 30 different concepts", Tier.SILVER, AchievementGroup.MIND, 30) { it.conceptsStudied },
        Achievement("mind_30", "Sharp Mind", "Play 30 mind games", Tier.BRONZE, AchievementGroup.MIND, 30) { it.mindGames },
        Achievement("dsa_25", "Problem Solver", "Solve 25 DSA problems on your own", Tier.SILVER, AchievementGroup.MIND, 25) { it.dsaSolved },
        Achievement("dsa_master_10", "Pattern Master", "Master 10 DSA problems (pass the 14-day re-solve)", Tier.GOLD, AchievementGroup.MIND, 10) { it.dsaMastered },
        Achievement("work_5", "Builder", "Log 5 pieces of work you built", Tier.SILVER, AchievementGroup.MIND, 5) { it.workLogs },

        Achievement("scroll_7", "Monk Mode", "Keep entertainment to ${Dungeons.LOW_SCROLL_MINUTES} min on 7 days", Tier.BRONZE, AchievementGroup.FOCUS, 7) { it.lowScrollDays },
        Achievement("scroll_30", "Unshaken", "Keep entertainment to ${Dungeons.LOW_SCROLL_MINUTES} min on 30 days", Tier.GOLD, AchievementGroup.FOCUS, 30) { it.lowScrollDays },
        Achievement("phone_off_14", "Night Guard", "Pass \"Phone off\" on 14 nights", Tier.SILVER, AchievementGroup.FOCUS, 14) { it.phoneOffVerified },

        Achievement("goal_1", "Goal Getter", "Reach a goal", Tier.SILVER, AchievementGroup.GOALS, 1) { it.goalsReached },
        Achievement("goal_5", "Conqueror", "Reach 5 goals", Tier.GOLD, AchievementGroup.GOALS, 5) { it.goalsReached },
        Achievement("shop_1", "Earned It", "Buy a reward with gold", Tier.BRONZE, AchievementGroup.GOALS, 1) { it.purchases },
    )

    private val byId = all.associateBy { it.id }

    public fun byId(id: String): Achievement? = byId[id]

    /** Achievements [facts] has earned that aren't in [unlocked] yet. */
    public fun newlyEarned(facts: HunterFacts, unlocked: Set<String>): List<Achievement> =
        all.filter { it.id !in unlocked && it.earned(facts) }
}
