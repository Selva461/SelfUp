package app.selfup.core

import kotlin.random.Random

/** A short game with a score you can push. [key] is persisted. */
public enum class MindGame(
    public val key: String,
    public val displayName: String,
    public val trains: String,
    public val unit: String,
    public val better: Metric.Better,
) {
    DIGIT_SPAN("digit_span", "Digit span", "Short-term memory", "digits", Metric.Better.HIGHER),
    N_BACK("n_back", "N-back", "Working memory", "N", Metric.Better.HIGHER),
    SPEED_MATH("speed_math", "Speed math", "Mental arithmetic", "per min", Metric.Better.HIGHER),
    GRID_RECALL("grid_recall", "Grid recall", "Visual memory", "cells", Metric.Better.HIGHER),
    REACTION("reaction", "Reaction", "Processing speed", "ms", Metric.Better.LOWER),
    ;

    public fun isBetter(candidate: Double, best: Double): Boolean = when (better) {
        Metric.Better.HIGHER -> candidate > best
        Metric.Better.LOWER -> candidate < best
    }

    public companion object {
        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String): MindGame =
            byKey[key] ?: throw IllegalArgumentException("Unknown game key: $key")
    }
}

public object MindRules {
    /** XP for the first finished game of each kind per day. */
    public const val SESSION_XP: Int = 10

    /** Bonus for beating a game's personal best. */
    public const val BEST_XP: Int = 15

    /** Games in a 10-minute session, rotating by day so all five get played across the week. */
    public const val SESSION_GAMES: Int = 3

    public fun xpFor(game: MindGame, score: Double, history: List<Double>, playedToday: Boolean): Int {
        val first = if (playedToday) 0 else SESSION_XP
        val best = history.reduceOrNull { a, b -> if (game.isBetter(b, a)) b else a }
        val bonus = if (best != null && game.isBetter(score, best)) BEST_XP else 0
        return first + bonus
    }

    /** Which games today's session plays: a rotating window over all five. */
    public fun sessionGames(epochDay: Long): List<MindGame> {
        val all = MindGame.entries
        val start = Math.floorMod(epochDay * SESSION_GAMES, all.size.toLong()).toInt()
        return List(SESSION_GAMES) { all[(start + it) % all.size] }
    }
}

/**
 * Digit span: repeat a string of digits after it disappears. Starts at [START], grows by
 * one after each success, and ends after two misses in a row at the same length. The score
 * is the longest string repeated correctly.
 */
public class DigitSpan(private val random: Random = Random.Default) {
    public var length: Int = START
        private set
    public var best: Int = 0
        private set
    private var misses = 0
    public val isOver: Boolean get() = misses >= MAX_MISSES

    /** A new string of [length] digits with no digit repeated back to back. */
    public fun next(): List<Int> {
        val digits = mutableListOf<Int>()
        while (digits.size < length) {
            val d = random.nextInt(10)
            if (digits.lastOrNull() != d) digits += d
        }
        return digits
    }

    /** Records the attempt for [shown]; returns whether [typed] was right. */
    public fun answer(shown: List<Int>, typed: List<Int>): Boolean {
        check(!isOver) { "The run is over" }
        val right = shown == typed
        if (right) {
            best = maxOf(best, shown.size)
            length = shown.size + 1
            misses = 0
        } else {
            misses++
        }
        return right
    }

    public companion object {
        public const val START: Int = 4
        public const val MAX_MISSES: Int = 2
    }
}

/**
 * N-back: letters appear one at a time; tap Match when the current letter equals the one
 * N steps back. A block passes at [PASS] accuracy (hits and correct rejections over all
 * scorable trials).
 */
public object NBack {
    public const val PASS: Double = 0.8
    public const val DROP: Double = 0.5
    public const val TARGET_RATE: Double = 0.3
    public const val TRIALS: Int = 20
    private val letters = "BCDFGHJKLMNPQRSTVWXZ".toList()

    /** A block of [TRIALS] + [n] letters where about [TARGET_RATE] of scorable trials are matches. */
    public fun block(n: Int, random: Random = Random.Default, trials: Int = TRIALS): List<Char> {
        require(n >= 1) { "N must be at least 1" }
        val seq = mutableListOf<Char>()
        repeat(trials + n) { i ->
            val target = i >= n && random.nextDouble() < TARGET_RATE
            seq += if (target) {
                seq[i - n]
            } else {
                // Avoid accidental matches so the target rate stays honest.
                letters.filter { i < n || it != seq[i - n] }.random(random)
            }
        }
        return seq
    }

    public fun isTarget(seq: List<Char>, n: Int, i: Int): Boolean = i >= n && seq[i] == seq[i - n]

    /** Accuracy over the scorable trials (index n onwards). [pressed] has one entry per letter. */
    public fun accuracy(seq: List<Char>, n: Int, pressed: List<Boolean>): Double {
        require(pressed.size == seq.size) { "One response per letter" }
        val scorable = (n until seq.size)
        val right = scorable.count { pressed[it] == isTarget(seq, n, it) }
        return right.toDouble() / scorable.count()
    }

    /** The N for the next block: up after a pass, down after a poor block, never below 1. */
    public fun nextN(n: Int, accuracy: Double): Int = when {
        accuracy >= PASS -> n + 1
        accuracy < DROP -> maxOf(1, n - 1)
        else -> n
    }
}

/** Speed math: as many correct answers as possible in 60 seconds; problems scale with your streak. */
public object SpeedMath {
    public const val SECONDS: Int = 60

    public data class Problem(val text: String, val answer: Int)

    /** [level] 0 is single-digit addition; higher levels add subtraction, bigger numbers and multiplication. */
    public fun problem(level: Int, random: Random = Random.Default): Problem = when (level.coerceIn(0, 3)) {
        0 -> {
            val a = random.nextInt(2, 10)
            val b = random.nextInt(2, 10)
            Problem("$a + $b", a + b)
        }
        1 -> {
            val a = random.nextInt(11, 60)
            val b = random.nextInt(2, a)
            if (random.nextBoolean()) Problem("$a + $b", a + b) else Problem("$a − $b", a - b)
        }
        2 -> {
            val a = random.nextInt(3, 13)
            val b = random.nextInt(3, 13)
            Problem("$a × $b", a * b)
        }
        else -> {
            val a = random.nextInt(12, 40)
            val b = random.nextInt(3, 10)
            if (random.nextBoolean()) Problem("$a × $b", a * b) else Problem("${a * b} ÷ $b", a)
        }
    }

    /** Every 5 correct in a row raises the level. */
    public fun levelFor(streak: Int): Int = (streak / 5).coerceAtMost(3)
}

/** Grid recall: some cells flash; tap them back. Each success adds a cell; the grid grows as it fills. */
public object GridRecall {
    public const val START_SIZE: Int = 3
    public const val START_CELLS: Int = 3
    public const val MAX_MISSES: Int = 2

    /** Grid side for a pattern of [cells]: big enough that at most half the grid lights up. */
    public fun sizeFor(cells: Int): Int {
        var size = START_SIZE
        while (cells > size * size / 2) size++
        return size
    }

    public fun pattern(cells: Int, random: Random = Random.Default): Set<Int> {
        val size = sizeFor(cells)
        return (0 until size * size).shuffled(random).take(cells).toSet()
    }
}

/** Reaction: tap when the screen turns; the score is the median of [TAPS] taps, ignoring false starts. */
public object Reaction {
    public const val TAPS: Int = 5
    public const val MIN_DELAY_MS: Long = 1200
    public const val MAX_DELAY_MS: Long = 3500

    /** Anything faster than this is anticipation, not reaction. */
    public const val FALSE_START_MS: Long = 100

    public fun median(times: List<Long>): Long {
        require(times.isNotEmpty()) { "No reaction times" }
        val sorted = times.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    public fun isFalseStart(ms: Long): Boolean = ms < FALSE_START_MS

    public fun delay(random: Random = Random.Default): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS)
}
