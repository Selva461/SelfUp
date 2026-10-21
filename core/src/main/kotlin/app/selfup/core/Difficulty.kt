package app.selfup.core

public enum class Difficulty(public val value: Int) {
    /** Under 15 minutes. */
    EASY(1),

    /** 15 to 30 minutes. */
    MEDIUM(2),

    /** Over 30 minutes, or effortful. */
    HARD(3),
    ;

    public companion object {
        public fun fromValue(value: Int): Difficulty =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Difficulty must be 1..3, was $value")
    }
}
