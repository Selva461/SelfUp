package app.selfup.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionTest {
    private fun at(time: String): Instant = LocalDateTime.parse("2026-10-19T$time").toInstant(ZoneOffset.UTC)
    private fun meal(food: String, time: String = "12:00", quality: FoodQuality? = null) =
        if (quality == null) MealEntry(at = at(time), food = food) else MealEntry(at = at(time), food = food, quality = quality)

    @Test
    fun `foods are classified by name, junk winning over clean`() {
        assertEquals(FoodQuality.CLEAN, Nutrition.classify("Chapati + omelette"))
        assertEquals(FoodQuality.CLEAN, Nutrition.classify("Whey shake"))
        assertEquals(FoodQuality.JUNK, Nutrition.classify("Fried chicken"))
        assertEquals(FoodQuality.JUNK, Nutrition.classify("Chicken burger"))
        assertEquals(FoodQuality.OK, Nutrition.classify("Dosa"))
        assertEquals(FoodQuality.OK, Nutrition.classify("Restaurant meal"))
    }

    @Test
    fun `a correction sticks to the food`() {
        val history = listOf(meal("Dosa", "09:00", FoodQuality.CLEAN), meal("Dosa", "08:00", FoodQuality.JUNK))
        assertEquals(FoodQuality.CLEAN, Nutrition.qualityFor("dosa ", history))
        assertEquals(FoodQuality.OK, Nutrition.qualityFor("Biryani", history))
    }

    @Test
    fun `full marks need three clean meals`() {
        assertEquals(1.0, Nutrition.mealsPerformance(listOf(meal("Oats"), meal("Grill"), meal("Whey shake"))))
        assertEquals(0.0, Nutrition.mealsPerformance(emptyList()))
        // Two clean meals: too few.
        assertEquals(2.0 / 3, Nutrition.mealsPerformance(listOf(meal("Oats"), meal("Grill"))), 1e-9)
        // Three meals with a pizza.
        assertEquals((1.0 + 1.0 + 0.2) / 3, Nutrition.mealsPerformance(listOf(meal("Oats"), meal("Grill"), meal("Pizza"))), 1e-9)
    }

    @Test
    fun `last meal is measured against the earlier of planned bed and the phone going down`() {
        val meals = listOf(meal("Grill", "21:00"), meal("Oats", "08:00"))
        assertEquals(1.0, Nutrition.lastMealPerformance(meals, at("23:00")))
        assertEquals(0.5, Nutrition.lastMealPerformance(meals, at("22:00")))
        assertNull(Nutrition.lastMealPerformance(emptyList(), at("22:00")))
        assertEquals(at("22:30"), Nutrition.bedtime(planned = at("23:59"), lastPhoneUse = at("22:30")))
        assertEquals(at("23:59"), Nutrition.bedtime(planned = at("23:59"), lastPhoneUse = null))
        // Staying up later than planned doesn't buy a bigger gap.
        assertEquals(at("23:59"), Nutrition.bedtime(planned = at("23:59"), lastPhoneUse = at("23:59").plusSeconds(7200)))
    }
}
