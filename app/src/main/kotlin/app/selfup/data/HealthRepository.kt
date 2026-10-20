package app.selfup.data

import app.selfup.core.DayBoundary
import app.selfup.core.GasEntry
import app.selfup.core.HealthLog
import app.selfup.core.MealEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Meal, gas and spending logs. Every write is one insert, so logging stays instant. */
class HealthRepository(
    db: SelfUpDatabase,
    private val clock: Clock,
    val dayBoundary: DayBoundary,
) {
    private val health = db.healthDao()

    fun today(): LocalDate = dayBoundary.questDay(clock.instant())

    suspend fun logMeal(food: String): Long {
        val meal = MealEntry(at = clock.instant(), food = food.trim())
        return health.insertMeal(MealLogEntity(atMillis = meal.at.toEpochMilli(), food = meal.food))
    }

    suspend fun logGas(level: Int): Long {
        val gas = GasEntry(at = clock.instant(), level = level)
        return health.insertGas(GasLogEntity(atMillis = gas.at.toEpochMilli(), level = gas.level))
    }

    suspend fun deleteMeal(id: Long) = health.deleteMeal(id)

    suspend fun deleteGas(id: Long) = health.deleteGas(id)

    /** Entries from the start of the quest day [days] − 1 days ago up to now. */
    fun observeMeals(days: Long): Flow<List<MealEntry>> =
        health.observeMeals(windowStart(days)).map { rows -> rows.map { it.toDomain() } }

    fun observeGas(days: Long): Flow<List<GasEntry>> =
        health.observeGas(windowStart(days)).map { rows -> rows.map { it.toDomain() } }

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
        gas = health.allGas().map { it.toDomain() },
        boundary = dayBoundary,
    )

    private fun windowStart(days: Long): Long {
        val firstDay = dayBoundary.questDay(clock.instant()).minusDays(days - 1)
        return firstDay.atTime(dayBoundary.resetTime).atZone(dayBoundary.zone).toInstant().toEpochMilli()
    }

    private fun MealLogEntity.toDomain() = MealEntry(id, Instant.ofEpochMilli(atMillis), food)

    private fun GasLogEntity.toDomain() = GasEntry(id, Instant.ofEpochMilli(atMillis), level)
}
