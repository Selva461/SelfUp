package app.selfup.core

public enum class Rank(public val displayName: String, public val minLevel: Int) {
    NOVICE("Novice", 1),
    APPRENTICE("Apprentice", 10),
    ADEPT("Adept", 20),
    EXPERT("Expert", 35),
    MASTER("Master", 50),
    ;

    public companion object {
        public fun forLevel(level: Int): Rank = entries.last { level >= it.minLevel }
    }
}
