package app.selfup.core

/**
 * Quests the System checks for itself, from data it already has. These can't be ticked by
 * hand: they complete when the data shows they were done, with XP for how well.
 */
public enum class AutoCheck(public val source: String) {
    /** "Phone off by 23:00", "In bed by 00:00": the phone's usage log, settled the next morning. */
    PHONE_OFF("Usage log"),

    /** "Finish a gym session": the Gym app's workout, scored as it syncs. */
    GYM("Gym app"),

    /** "Log every meal today": the meals on the Log tab, by count and quality, settled the next morning. */
    MEALS("Meal log"),

    /** "Finish last meal 2+ hours before bed": last meal against when the phone went down. */
    LAST_MEAL("Meal log"),
}

public object QuestChecks {
    private val gym = Regex("""(?i)\b(gym|workout|lifting|weights session|training session)\b""")
    private val meals = Regex("""(?i)\b(log|logged|track)\b.*\bmeals?\b""")
    private val lastMeal = Regex("""(?i)\blast meal\b""")

    /**
     * How [quest] is checked, or null for one only you can vouch for. Make-up quests stay
     * manual: they're due the same day they're made, before any data could settle them.
     * [gymConnected] is whether the Gym app shares workouts; without it a gym quest is manual.
     */
    public fun of(quest: Quest, gymConnected: Boolean): AutoCheck? {
        if (quest.penaltyFor != null || quest.schedule !is Schedule.Recurring) return null
        if (quest.input is CompletionInput.Number) return null
        val title = quest.title
        return when {
            ScreenTime.cutoff(title) != null -> AutoCheck.PHONE_OFF
            lastMeal.containsMatchIn(title) -> AutoCheck.LAST_MEAL
            meals.containsMatchIn(title) -> AutoCheck.MEALS
            gym.containsMatchIn(title) -> if (gymConnected) AutoCheck.GYM else null
            else -> null
        }
    }
}
