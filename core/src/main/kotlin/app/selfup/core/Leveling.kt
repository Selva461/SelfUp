package app.selfup.core

import kotlin.math.sqrt

/**
 * Level curve: going from level L to L + 1 costs L × 100 XP.
 *
 * Stats store cumulative XP only; level, rank and bar progress are always derived from it,
 * so they can never drift out of sync with the XP history.
 */
public object Leveling {
    public const val XP_STEP: Long = 100

    /** XP needed to go from [level] to the next one. */
    public fun xpToAdvance(level: Int): Long {
        require(level >= 1) { "Level starts at 1, was $level" }
        return level * XP_STEP
    }

    /** Cumulative XP at which [level] is reached: 100 × (L − 1) × L / 2. */
    public fun floorXp(level: Int): Long {
        require(level >= 1) { "Level starts at 1, was $level" }
        return XP_STEP * (level - 1) * level / 2
    }

    public fun levelFor(totalXp: Long): Int {
        if (totalXp <= 0) return 1
        // Closed-form estimate, then correct for floating point error at the boundaries.
        var level = ((1 + sqrt(1 + 8.0 * totalXp / XP_STEP)) / 2).toInt().coerceAtLeast(1)
        while (floorXp(level) > totalXp) level--
        while (floorXp(level + 1) <= totalXp) level++
        return level
    }

    public fun progress(totalXp: Long): LevelProgress {
        val xp = totalXp.coerceAtLeast(0)
        val level = levelFor(xp)
        return LevelProgress(
            level = level,
            xpIntoLevel = xp - floorXp(level),
            xpToAdvance = xpToAdvance(level),
        )
    }
}

public data class LevelProgress(
    val level: Int,
    val xpIntoLevel: Long,
    val xpToAdvance: Long,
) {
    /** Bar fill, from 0 up to but never reaching 1. */
    val fraction: Float get() = xpIntoLevel.toFloat() / xpToAdvance
}
