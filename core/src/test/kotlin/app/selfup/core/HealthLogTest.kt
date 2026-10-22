package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthLogTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    @Test
    fun `quick foods put the most used first and keep defaults after`() {
        val history = listOf("Grill", "Biryani", "Grill", "Dosa").map { MealEntry(at = Instant.EPOCH, food = it) }
        val quick = HealthLog.quickFoods(history)
        assertEquals(listOf("Grill", "Biryani", "Dosa", "Chapati + omelette"), quick.take(4))
        assertEquals(quick.distinct(), quick)
    }

    @Test
    fun `entries group by quest day so a late dinner stays on its day`() {
        val meals = listOf(
            MealEntry(at = at("2026-10-19", "09:10"), food = "Dosa"),
            MealEntry(at = at("2026-10-20", "00:20"), food = "Grill"),
        )
        val days = HealthLog.byDay(meals, boundary, listOf(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")))

        val monday = days.single { it.day == LocalDate.parse("2026-10-19") }
        assertEquals(listOf("Dosa", "Grill"), monday.meals.map { it.food })
        assertEquals(at("2026-10-20", "00:20"), monday.lastMealAt)
        assertTrue(days.single { it.day == LocalDate.parse("2026-10-20") }.meals.isEmpty())
        assertEquals(LocalDate.parse("2026-10-20"), days.first().day)
    }

    @Test
    fun `csv lists entries oldest first and escapes commas`() {
        val csv = HealthLog.toCsv(
            meals = listOf(
                MealEntry(at = at("2026-10-19", "13:30"), food = "Chapati, omelette"),
                MealEntry(at = at("2026-10-19", "09:00"), food = "Dosa"),
            ),
            boundary = boundary,
        )
        assertEquals(
            "date,time,type,detail,quality\n2026-10-19,09:00,meal,Dosa,ok\n2026-10-19,13:30,meal,\"Chapati, omelette\",clean\n",
            csv.replace("\r\n", "\n"),
        )
    }

    @Test
    fun `a meal within two hours of bed is late`() {
        val bed = LocalTime.MIDNIGHT
        assertFalse(HealthLog.isLateMeal(at("2026-10-19", "21:59"), bed, boundary))
        assertTrue(HealthLog.isLateMeal(at("2026-10-19", "23:00"), bed, boundary))
        assertTrue(HealthLog.isLateMeal(at("2026-10-20", "00:30"), bed, boundary))
    }
}
