package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.CompletionInput
import app.selfup.core.DayBoundary
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class HealthRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 19).atTime(21, 0).atZone(zone).toInstant(), zone)
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    private lateinit var db: SelfUpDatabase
    private lateinit var health: HealthRepository
    private lateinit var life: LifeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        health = HealthRepository(db, clock, boundary)
        life = LifeRepository(db, clock, boundary)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun mealsRoundTripAndUndo() = runTest {
        val meal = health.logMeal("  Grill ")
        assertEquals(listOf("Grill"), health.observeMeals(7).first().map { it.food })

        health.deleteMeal(meal)
        assertTrue(health.observeMeals(7).first().isEmpty())
    }

    @Test
    fun dailySpendComesFromMoneyNumberQuestsAndIsZeroFilled() = runTest {
        val spending = life.saveQuest(
            Quest(
                title = "Log spending",
                difficulty = Difficulty.EASY,
                stats = setOf(Stat.MONEY),
                schedule = Schedule.Recurring(),
                input = CompletionInput.Number("₹"),
            ),
        )
        life.complete(spending, value = 640.0)

        val series = health.observeDailySpend(14).first()
        assertEquals(14, series.size)
        assertEquals(LocalDate.of(2026, 10, 19) to 640.0, series.last())
        assertEquals(0.0, series.first().second, 0.0)
    }

    @Test
    fun csvExportIncludesEveryEntry() = runTest {
        health.logMeal("Dosa")
        health.logMeal("Grill")
        val lines = health.exportCsv().trim().lines()
        assertEquals("date,time,type,detail", lines.first())
        assertEquals(3, lines.size)
    }
}
