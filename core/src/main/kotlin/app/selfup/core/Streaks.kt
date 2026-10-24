package app.selfup.core

/**
 * The daily streak and its shields. A clear day (every due daily done) adds one; a day with
 * misses either burns a shield, which also cancels that day's penalties, or resets the
 * streak. Shields are earned, never bought: one per [SHIELD_EVERY] clear days, at most
 * [MAX_SHIELDS] held, so one bad day doesn't wipe out weeks of work.
 */
public data class StreakState(val current: Int = 0, val best: Int = 0, val shields: Int = 0) {
    init {
        require(current >= 0 && best >= current) { "Streak $current must be between 0 and best $best" }
        require(shields in 0..Streaks.MAX_SHIELDS) { "Shields must be 0..${Streaks.MAX_SHIELDS}, was $shields" }
    }

    /** `current:best:shields`, for the key-value store. */
    public fun encode(): String = "$current:$best:$shields"

    public companion object {
        public fun decode(value: String?): StreakState {
            val parts = value?.split(':')?.mapNotNull { it.toIntOrNull() }
            if (parts == null || parts.size != 3) return StreakState()
            return runCatching { StreakState(parts[0], parts[1], parts[2]) }.getOrDefault(StreakState())
        }
    }
}

public enum class DayOutcome {
    /** Nothing was due. The streak neither grows nor breaks. */
    EMPTY,
    CLEAR,

    /** Misses, but a shield absorbed them: no penalty and the streak holds. */
    SHIELDED,

    /** A rest day declared in advance (see [RestDays]): misses cost nothing and the streak holds without growing. */
    RESTED,
    BROKEN,
}

public object Streaks {
    public const val SHIELD_EVERY: Int = 7
    public const val MAX_SHIELDS: Int = 2

    /**
     * Settles one day. A [rested] day that was cleared anyway still counts as clear; one with
     * misses is excused without spending a shield.
     */
    public fun close(state: StreakState, due: Int, missed: Int, rested: Boolean = false): Pair<StreakState, DayOutcome> {
        require(missed in 0..due) { "Missed $missed of $due due quests" }
        return when {
            due == 0 -> state to DayOutcome.EMPTY
            missed == 0 -> {
                val current = state.current + 1
                val earned = if (current % SHIELD_EVERY == 0) 1 else 0
                StreakState(
                    current = current,
                    best = maxOf(state.best, current),
                    shields = minOf(MAX_SHIELDS, state.shields + earned),
                ) to DayOutcome.CLEAR
            }
            rested -> state to DayOutcome.RESTED
            state.shields > 0 -> state.copy(shields = state.shields - 1) to DayOutcome.SHIELDED
            else -> state.copy(current = 0) to DayOutcome.BROKEN
        }
    }
}
