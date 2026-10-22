package app.selfup.data

import app.selfup.core.AutoCheck
import app.selfup.core.DayBoundary
import app.selfup.core.FoodQuality
import app.selfup.core.HealthLog
import app.selfup.core.MealEntry
import app.selfup.core.Nutrition
import app.selfup.core.Quest
import app.selfup.core.ReminderPlanner
import app.selfup.core.ScreenTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** What settling finished days' meals did, for telling the user. */
data class NutritionReport(
    /** Meal quests the log completed, with how well each went. */
    val verified: List<Pair<Quest, Double>> = emptyList(),
    /** Ones the log showed weren't met (too few or poor meals, or eating too close to bed). */
    val failed: List<Quest> = emptyList(),
) {
    val isEmpty: Boolean get() = verified.isEmpty() && failed.isEmpty()
}

/** Meal and spending logs. Every write is one insert, so logging stays instant. */
class HealthRepository(
    db: SelfUpDatabase,
    private val clock: Clock,
    val dayBoundary: DayBoundary,
) {
    private val health = db.healthDao()
    private val meta = db.metaDao()
    private val tracking = db.trackingDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    /** Logs [food] now, with the quality it had last time (or a guess from its name). */
    suspend fun logMeal(food: String, quality: FoodQuality? = null): Long {
        val name = food.trim()
        val history = health.allMeals().map { it.toDomain() }
        val meal = MealEntry(at = clock.instant(), food = name, quality = quality ?: Nutrition.qualityFor(name, history))
        return health.insertMeal(MealLogEntity(atMillis = meal.at.toEpochMilli(), food = meal.food, quality = meal.quality.key))
    }

    /** Corrects a meal's quality; the next log of the same food remembers it. */
    suspend fun setQuality(id: Long, quality: FoodQuality) = health.setQuality(id, quality.key)

    suspend fun deleteMeal(id: Long) = health.deleteMeal(id)

    /** Entries from the start of the quest day [days] − 1 days ago up to now. */
    fun observeMeals(days: Long): Flow<List<MealEntry>> =
        health.observeMeals(windowStart(days)).map { rows -> rows.map { it.toDomain() } }

    /** Spending per quest day for the last [days] days, zero-filled, oldest first. */
    fun observeDailySpend(days: Long): Flow<List<Pair<LocalDate, Double>>> {
        val today = dayBoundary.questDay(clock.instant())
        val start = today.minusDays(days - 1)
        return health.observeDailySpend(start.toEpochDay()).map { rows ->
            val byDay = rows.associate { it.epochDay to it.total }
            (0 until days).map { offset ->
                val day = start.plusDays(offset)
                day to (byDay[day.toEpochDay()] ?: 0.0)
            }
        }
    }

    suspend fun exportCsv(): String = HealthLog.toCsv(
        meals = health.allMeals().map { it.toDomain() },
        boundary = dayBoundary,
    )

    /**
     * Scores the meal quests of every finished day not settled yet (up to a week back) from
     * what was logged: "Log every meal" by count and quality, "last meal before bed" by the
     * gap to when the phone went down. Must run after the usage log is settled (it reads that
     * day's last phone use) and before [LifeRepository.closeFinishedDays].
     */
    suspend fun settle(life: LifeRepository): NutritionReport {
        val today = life.today()
        val yesterday = today.minusDays(1)
        val last = meta.get(SETTLED_KEY)?.toLongOrNull()?.let(LocalDate::ofEpochDay)
        if (last != null && !last.isBefore(yesterday)) return NutritionReport()
        meta.put(MetaEntity(SETTLED_KEY, yesterday.toEpochDay().toString()))
        val first = maxOf(last?.plusDays(1) ?: yesterday, yesterday.minusDays(MAX_CATCH_UP - 1))

        val verified = mutableListOf<Pair<Quest, Double>>()
        val failed = mutableListOf<Quest>()
        var day = first
        while (!day.isAfter(yesterday)) {
            val due = life.dailiesDueOn(day).associateWith { life.checkOf(it) }
            val mealQuests = due.filterValues { it == AutoCheck.MEALS || it == AutoCheck.LAST_MEAL }
            if (mealQuests.isNotEmpty()) {
                val meals = health.mealsBetween(dayBoundary.startOf(day).toEpochMilli(), dayBoundary.startOf(day.plusDays(1)).toEpochMilli())
                    .map { it.toDomain() }
                val bed = bedtime(day, due.filterValues { it == AutoCheck.PHONE_OFF }.keys)
                mealQuests.forEach { (quest, check) ->
                    val (performance, detail) = when (check) {
                        AutoCheck.MEALS -> Nutrition.mealsPerformance(meals) to
                            "meals=${meals.size} quality=${meals.joinToString("/") { it.quality.key }}"
                        else -> (Nutrition.lastMealPerformance(meals, bed) ?: 0.0) to
                            "last=${meals.maxOfOrNull { it.at }?.toEpochMilli()} bed=${bed.toEpochMilli()}"
                    }
                    when (life.verify(quest.id, day, check!!, performance, detail)) {
                        is CompletionResult.Completed -> verified += quest to performance
                        is CompletionResult.Failed -> failed += quest
                        else -> Unit
                    }
                }
            }
            day = day.plusDays(1)
        }
        return NutritionReport(verified, failed)
    }

    /**
     * When [day]'s bed was: the latest "Phone off"/"In bed" cutoff due that day (midnight
     * without one), or the phone's last use if that came first.
     */
    private suspend fun bedtime(day: LocalDate, phoneQuests: Set<Quest>): Instant {
        val planned = phoneQuests.mapNotNull { ScreenTime.cutoff(it.title) }
            .map { ReminderPlanner.instantOn(day, it, dayBoundary) }
            .maxOrNull()
            ?: ReminderPlanner.instantOn(day, LocalTime.MIDNIGHT, dayBoundary)
        val lastUse = tracking.phoneDay(day.toEpochDay())?.lastUseMillis?.let(Instant::ofEpochMilli)
        return Nutrition.bedtime(planned, lastUse)
    }

    private fun windowStart(days: Long): Long {
        val firstDay = dayBoundary.questDay(clock.instant()).minusDays(days - 1)
        return firstDay.atTime(dayBoundary.resetTime).atZone(dayBoundary.zone).toInstant().toEpochMilli()
    }

    private fun MealLogEntity.toDomain() =
        MealEntry(id, Instant.ofEpochMilli(atMillis), food, FoodQuality.fromKey(quality) ?: Nutrition.classify(food))

    private companion object {
        const val SETTLED_KEY = "nutrition_settled_day"
        const val MAX_CATCH_UP = 7L
    }
}
