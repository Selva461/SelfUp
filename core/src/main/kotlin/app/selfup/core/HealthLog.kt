package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A meal, logged with one tap at the time it was eaten. [quality] is guessed from the name unless set. */
public data class MealEntry(
    val id: Long = 0,
    val at: Instant,
    val food: String,
    val quality: FoodQuality = Nutrition.classify(food),
) {
    init {
        require(food.isNotBlank()) { "A meal needs a name" }
    }
}

public data class HealthDay(
    val day: LocalDate,
    val meals: List<MealEntry>,
) {
    val lastMealAt: Instant? get() = meals.maxOfOrNull { it.at }
}

/** Meal tracking: what was eaten and when, grouped by quest day. */
public object HealthLog {
    /** Starting presets from the usual diet; the most-used foods float to the front. */
    public val defaultFoods: List<String> = listOf(
        "Dosa",
        "Chapati + omelette",
        "Grill",
        "Restaurant meal",
        "Whey shake",
        "Oats",
    )

    /** Presets ordered by how often each was logged, then the defaults, without duplicates. */
    public fun quickFoods(history: List<MealEntry>, limit: Int = 8): List<String> {
        val byUse = history.groupingBy { it.food }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        return (byUse + defaultFoods).distinct().take(limit)
    }

    /** Groups entries by quest day, one [HealthDay] per day in [days] (newest first). */
    public fun byDay(
        meals: List<MealEntry>,
        boundary: DayBoundary,
        days: List<LocalDate>,
    ): List<HealthDay> {
        val mealsByDay = meals.groupBy { boundary.questDay(it.at) }
        return days.sortedDescending().map { day ->
            HealthDay(
                day = day,
                meals = mealsByDay[day].orEmpty().sortedBy { it.at },
            )
        }
    }

    /**
     * A plain CSV of every meal, oldest first, for sharing: `date,time,type,detail,quality`.
     */
    public fun toCsv(meals: List<MealEntry>, boundary: DayBoundary): String {
        val date = DateTimeFormatter.ISO_LOCAL_DATE
        val time = DateTimeFormatter.ofPattern("HH:mm")
        return buildString {
            appendLine("date,time,type,detail,quality")
            meals.sortedBy { it.at }.forEach { meal ->
                val local = meal.at.atZone(boundary.zone)
                appendLine("${local.format(date)},${local.toLocalTime().format(time)},meal,${csvField(meal.food)},${meal.quality.key}")
            }
        }
    }

    /** True when a meal at [at] ends within two hours of [bedtime], the spec's nutrition rule. */
    public fun isLateMeal(at: Instant, bedtime: LocalTime, boundary: DayBoundary): Boolean {
        val bed = ReminderPlanner.instantOn(boundary.questDay(at), bedtime, boundary)
        return !at.isBefore(bed.minusSeconds(2 * 60 * 60))
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
