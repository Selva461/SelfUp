package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthLogTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    @Test
    fun `gas level must be on the 1 to 5 scale`() {
        assertFailsWith<IllegalArgumentException> { GasEntry(at = Instant.EPOCH, level = 0) }
        assertFailsWith<IllegalArgumentException> { GasEntry(at = Instant.EPOCH, level = 6) }
    }

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
        val gas = listOf(GasEntry(at = at("2026-10-19", "21:00"), level = 4), GasEntry(at = at("2026-10-20", "01:00"), level = 2))
        val days = HealthLog.byDay(meals, gas, boundary, listOf(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")))

        val monday = days.single { it.day == LocalDate.parse("2026-10-19") }
        assertEquals(listOf("Dosa", "Grill"), monday.meals.map { it.food })
        assertEquals(3.0, monday.averageGas)
        assertEquals(4, monday.maxGas)
        assertEquals(at("2026-10-20", "00:20"), monday.lastMealAt)
        assertNull(days.single { it.day == LocalDate.parse("2026-10-20") }.averageGas)
        assertEquals(LocalDate.parse("2026-10-20"), days.first().day)
    }

    @Test
    fun `csv lists entries oldest first and escapes commas`() {
        val csv = HealthLog.toCsv(
            meals = listOf(MealEntry(at = at("2026-10-19", "13:30"), food = "Chapati, omelette")),
            gas = listOf(GasEntry(at = at("2026-10-19", "09:00"), level = 3)),
            boundary = boundary,
        )
        assertEquals(
            "date,time,type,detail\n2026-10-19,09:00,gas,3\n2026-10-19,13:30,meal,\"Chapati, omelette\"\n",
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
