package app.selfup.core

/**
 * Logging what you spent is a habit; spending less is the point. A day's spend is held
 * against your own typical day (the median of recent logged days): at or under it earns
 * full XP, double it earns half the performance, and so on.
 */
public object Spending {
    /** Logged days needed before there is a typical day to compare with. */
    public const val MIN_HISTORY: Int = 7

    /** How many recent logged days make up the typical day. */
    public const val WINDOW: Int = 30

    /** The median of the last [WINDOW] logged days in [history] (oldest first), or null with too little history. */
    public fun typical(history: List<Double>): Double? {
        val recent = history.takeLast(WINDOW)
        if (recent.size < MIN_HISTORY) return null
        val sorted = recent.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** [spend] against the typical day, 0 to 1. Full while there's too little history to judge. */
    public fun performance(spend: Double, history: List<Double>): Double {
        require(spend >= 0) { "Spending can't be negative, was $spend" }
        val typical = typical(history) ?: return 1.0
        if (typical <= 0) return if (spend <= 0) 1.0 else 0.0
        return Performance.atMost(spend, typical)
    }
}
