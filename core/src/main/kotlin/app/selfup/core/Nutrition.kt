package app.selfup.core

import java.time.Duration
import java.time.Instant

/** How good a food is for the goal (lean muscle, steady energy). [key] is persisted. */
public enum class FoodQuality(public val key: String, public val displayName: String, public val value: Double) {
    CLEAN("clean", "Clean", 1.0),
    OK("ok", "OK", 0.7),
    JUNK("junk", "Junk", 0.2),
    ;

    public companion object {
        private val byKey = entries.associateBy { it.key }

        public fun fromKey(key: String?): FoodQuality? = key?.let { byKey[it] }
    }
}

/**
 * Scores food by what was actually eaten. Logging three meals isn't the goal; eating three
 * good ones is. "Log every meal" earns full XP only for [MEALS_TARGET]+ meals that are all
 * clean, and "last meal 2+ hours before bed" is measured against when the phone actually
 * went down, not ticked by hand.
 */
public object Nutrition {
    public const val MEALS_TARGET: Int = 3

    /** Hours between the last meal and bed for full marks. */
    public const val BED_GAP_HOURS: Double = 2.0

    // Checked first, so "fried chicken" or "chicken burger" isn't called clean.
    private val junk = listOf(
        "pizza", "burger", "fries", "chips", "cake", "pastry", "pastries", "sweets", "chocolate", "ice cream",
        "soda", "coke", "pepsi", "cola", "soft drink", "energy drink", "biscuit", "cookie", "donut", "doughnut",
        "samosa", "pakora", "bajji", "bonda", "puff", "maggi", "noodles", "fried", "kfc", "mcdonald", "domino",
        "shawarma", "parotta", "porotta", "takeaway", "junk", "candy", "jalebi", "gulab", "halwa", "mixture",
        "namkeen", "fast food", "dessert", "milkshake", "frankie",
    )
    private val clean = listOf(
        "whey", "protein", "oats", "egg", "omelette", "chicken", "fish", "paneer", "tofu", "dal", "salad", "fruit",
        "banana", "apple", "orange", "papaya", "guava", "curd", "yogurt", "yoghurt", "sprouts", "grill", "chapati",
        "roti", "phulka", "vegetable", "veggies", "sabzi", "poriyal", "milk", "nuts", "peanut", "almond", "rajma",
        "chana", "millet", "ragi", "idli", "quinoa", "brown rice", "sambar", "soup", "rasam", "steamed", "boiled",
    )

    /** A best guess from the food's name; anything unrecognised is [FoodQuality.OK]. */
    public fun classify(food: String): FoodQuality {
        val text = food.lowercase()
        return when {
            junk.any { it in text } -> FoodQuality.JUNK
            clean.any { it in text } -> FoodQuality.CLEAN
            else -> FoodQuality.OK
        }
    }

    /**
     * The quality a new log of [food] gets: what it was last logged as (a correction sticks),
     * else [classify].
     */
    public fun qualityFor(food: String, history: List<MealEntry>): FoodQuality =
        history.filter { it.food.equals(food.trim(), ignoreCase = true) }.maxByOrNull { it.at }?.quality ?: classify(food)

    /** A day's eating, 0 to 1: enough meals, times their average quality. */
    public fun mealsPerformance(meals: List<MealEntry>): Double {
        if (meals.isEmpty()) return 0.0
        val enough = Performance.atLeast(meals.size.toDouble(), MEALS_TARGET.toDouble())
        return enough * meals.map { it.quality.value }.average()
    }

    /** How far before [bed] the last meal ended, 0 to 1 against [BED_GAP_HOURS]; null with no meals. */
    public fun lastMealPerformance(meals: List<MealEntry>, bed: Instant): Double? {
        val last = meals.maxOfOrNull { it.at } ?: return null
        val hours = Duration.between(last, bed).toMinutes() / 60.0
        return Performance.atLeast(hours, BED_GAP_HOURS)
    }

    /**
     * When the day's bed actually was: the earlier of the planned bedtime and the last time
     * the phone was used, so staying up late doesn't make the gap easier to hit.
     */
    public fun bedtime(planned: Instant, lastPhoneUse: Instant?): Instant =
        if (lastPhoneUse != null && lastPhoneUse.isBefore(planned)) lastPhoneUse else planned
}
