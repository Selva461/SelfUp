package app.selfup.core

/** What a shop reward does when bought. */
public enum class RewardKind(public val key: String) {
    /** A real-life treat you grant yourself; the System only records it. */
    CUSTOM("custom"),

    /** Extra entertainment minutes today, on top of the app-lock budget. */
    SCREEN_MINUTES("screen"),
    ;

    public companion object {
        public fun fromKey(key: String): RewardKind = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

public data class Reward(
    val id: Long = 0,
    val title: String,
    val price: Int,
    val kind: RewardKind = RewardKind.CUSTOM,
    /** Minutes added for a [RewardKind.SCREEN_MINUTES] reward. */
    val minutes: Int = 0,
) {
    init {
        require(title.isNotBlank()) { "A reward needs a name" }
        require(price in Gold.MIN_PRICE..Gold.MAX_PRICE) { "Price must be ${Gold.MIN_PRICE}..${Gold.MAX_PRICE}, was $price" }
    }
}

/**
 * Gold, the System's currency, and the shop it buys from. Gold is earned only by playing
 * well: each closed day pays its net XP (penalties included) plus a bonus for a clear day,
 * a settled dungeon pays by its rank, and an achievement pays by its tier. It is spent on
 * rewards you price yourself, so a takeaway or an evening of series is something earned
 * with effort rather than a default.
 */
public object Gold {
    public const val CLEAR_DAY_BONUS: Int = 10
    public const val MIN_PRICE: Int = 10
    public const val MAX_PRICE: Int = 100_000

    /** Screen-minute rewards bought per day at most, so the lock still means something. */
    public const val SCREEN_BUYS_PER_DAY: Int = 2

    /** Gold for one closed day: its net XP when positive, plus [CLEAR_DAY_BONUS] when every daily was done. */
    public fun forDay(netXp: Int, outcome: DayOutcome): Int =
        netXp.coerceAtLeast(0) + if (outcome == DayOutcome.CLEAR) CLEAR_DAY_BONUS else 0

    /** Offered on first launch of the shop; all editable. */
    public val defaultRewards: List<Reward> = listOf(
        Reward(title = "15 more minutes of entertainment", price = 200, kind = RewardKind.SCREEN_MINUTES, minutes = 15),
        Reward(title = "Order in or eat out", price = 300),
        Reward(title = "Movie or series night", price = 250),
        Reward(title = "Sleep in, no alarm", price = 200),
        Reward(title = "Buy something you've wanted", price = 1500),
    )
}
