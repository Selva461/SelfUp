package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.DsaDifficulty
import app.selfup.core.DsaOutcome
import app.selfup.core.DsaTopic
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class DsaRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day0 = LocalDate.of(2026, 10, 19)
    private val clock = StepClock(day0.atTime(21, 0).atZone(zone).toInstant(), zone)

    private lateinit var db: SelfUpDatabase
    private lateinit var life: LifeRepository
    private lateinit var dsa: DsaRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        life = LifeRepository(db, clock, DayBoundary(LocalTime.of(4, 0), zone))
        dsa = DsaRepository(db, life)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun technical(): Long = life.observeStats(Stat.entries).first().single { it.stat == Stat.TECHNICAL }.totalXp

    @Test
    fun aFirstSolvePaysFullAndSchedulesTheReSolve() = runTest {
        val result = dsa.add("Two Sum", DsaTopic.ARRAYS, DsaDifficulty.EASY, null, DsaOutcome.ALONE, 10)
        assertEquals(10, result.xp)
        assertEquals(10, technical())
        assertEquals(day0.plusDays(3), result.state.due)

        val overview = dsa.observe().first()
        assertEquals(1, overview.solvedAlone)
        assertTrue(overview.due.isEmpty())
        clock.advance(Duration.ofDays(3))
        assertEquals(listOf("Two Sum"), dsa.observe().first().due.map { it.problem.title })
    }

    @Test
    fun reSolvesPayHalfAndClimbToMastery() = runTest {
        val id = dsa.add("Valid Anagram", DsaTopic.ARRAYS, DsaDifficulty.MEDIUM, null, DsaOutcome.ALONE, null).let { db.dsaDao().attempt(it.attemptId)!!.problemId }
        clock.advance(Duration.ofDays(3))
        assertEquals(10, dsa.attempt(id, DsaOutcome.ALONE, null).xp)
        clock.advance(Duration.ofDays(7))
        val last = dsa.attempt(id, DsaOutcome.ALONE, null)
        assertTrue(last.state.mastered)
        assertEquals(40, technical())
        assertEquals(1, dsa.observe().first().mastered)
    }

    @Test
    fun undoTakesBackExactlyWhatWasPaidAndDropsAnEmptyProblem() = runTest {
        val result = dsa.add("Two Sum", DsaTopic.ARRAYS, DsaDifficulty.HARD, null, DsaOutcome.ALONE, null)
        assertEquals(30, technical())
        dsa.undo(result.attemptId)
        assertEquals(0, technical())
        assertTrue(dsa.observe().first().problems.isEmpty())
    }

    @Test
    fun readingTheSolutionCostsALittleButNeverALevel() = runTest {
        val result = dsa.add("Median of Two Arrays", DsaTopic.BINARY_SEARCH, DsaDifficulty.HARD, null, DsaOutcome.SOLUTION, null)
        // No Technical XP yet, so the loss is clamped to nothing.
        assertEquals(0, result.xp)
        assertEquals(0, result.state.stage)
        assertEquals(day0.plusDays(3), result.state.due)
    }
}

private class StepClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    fun advance(duration: Duration) {
        now += duration
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = StepClock(now, zone)
}
