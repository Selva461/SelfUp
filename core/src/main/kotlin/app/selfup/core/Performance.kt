package app.selfup.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * XP for how well something went, not for having done it.
 *
 * Every scored activity reduces to a performance from 0 to 1, where 1 means the target was
 * fully met: every answer right, the best matched, the session up to the mark. Only that
 * earns the full reward. Below it the reward falls off steeply (the cube of the
 * performance, rounded down), and a very poor attempt costs XP instead.
 *
 * With a full reward of 10:
 *
 * | performance | XP |
 * |-------------|----|
 * | 1.0         | 10 |
 * | 0.9         | 7  |
 * | 0.8         | 5  |
 * | 0.7         | 3  |
 * | 0.6         | 2  |
 * | 0.5         | 1  |
 * | 0.4         | 0  |
 * | under 0.35  | −1 |
 */
public object Performance {
    /** Below this an attempt counts as lost badly and costs XP. */
    public const val FAIL_BELOW: Double = 0.35

    /** How sharply the reward falls away from perfect. */
    private const val CURVE: Double = 3.0

    /** Rounding slack, so 4.9999 out of 5 still counts as perfect. */
    private const val EPSILON: Double = 1e-9

    public fun isPerfect(performance: Double): Boolean = performance >= 1 - EPSILON

    public fun isFail(performance: Double): Boolean = performance < FAIL_BELOW

    /** XP for [performance] (clamped to 0..1) against a [full] reward. */
    public fun xp(full: Int, performance: Double): Int {
        require(full >= 0) { "A full reward can't be negative, was $full" }
        val p = performance.coerceIn(0.0, 1.0)
        return when {
            full == 0 -> 0
            isPerfect(p) -> full
            isFail(p) -> -loss(full)
            else -> floor(full * p.pow(CURVE) + EPSILON).toInt()
        }
    }

    /** What a badly lost attempt costs: a tenth of the full reward, at least 1. */
    public fun loss(full: Int): Int = maxOf(1, ceil(full / 10.0).toInt())

    /** A short verdict for the result screens. */
    public fun label(performance: Double): String = when {
        isPerfect(performance) -> "Perfect"
        performance >= 0.8 -> "Strong"
        performance >= 0.6 -> "Partial"
        !isFail(performance) -> "Weak"
        else -> "Failed"
    }

    /** [value] against [target] as 0..1, for "more is better" numbers. A zero target is met by anything. */
    public fun atLeast(value: Double, target: Double): Double =
        if (target <= 0) 1.0 else (value / target).coerceIn(0.0, 1.0)

    /** [value] against [target] as 0..1, for "less is better" numbers such as reaction time. */
    public fun atMost(value: Double, target: Double): Double =
        if (value <= target) 1.0 else (target / value).coerceIn(0.0, 1.0)
}
