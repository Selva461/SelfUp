package app.selfup.core

/**
 * A life area that levels up independently.
 *
 * [key] is what gets persisted, so renaming an enum constant never corrupts saved data.
 */
public enum class Stat(public val key: String, public val displayName: String) {
    STRENGTH("strength", "Strength"),
    NUTRITION("nutrition", "Nutrition"),
    TECHNICAL("technical", "Technical"),
    SPEAKING("speaking", "Speaking"),
    SOCIAL("social", "Social"),
    LIFE("life", "Life"),
    MONEY("money", "Money"),
    ;

    public companion object {
        /**
         * Stats shown at launch. Life is included alongside the spec's four because the
         * seeded sleep and phone quests feed it, and sleep matters for the food log.
         */
        public val launchSet: List<Stat> = listOf(STRENGTH, NUTRITION, TECHNICAL, LIFE, MONEY)

        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String): Stat =
            byKey[key] ?: throw IllegalArgumentException("Unknown stat key: $key")
    }
}
