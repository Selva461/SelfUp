package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.MindGame
import app.selfup.core.MindRun
import app.selfup.core.MindRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class MindRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)
    private var now: Instant = LocalDate.of(2026, 10, 21).atTime(8, 0).atZone(zone).toInstant()
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private lateinit var db: SelfUpDatabase
    private lateinit var mind: MindRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mind = MindRepository(db, clock, boundary)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun technicalXp(): Long =
        db.xpDao().totals().firstOrNull { it.stat == MindRepository.STAT.key }?.total ?: 0

    @Test
    fun theFirstGameOfTheDayIsScoredAndOnlyBeatingYourBestEarnsMore() = runTest {
        // Six digits against the baseline of seven: 0.86, which pays 6 of 10.
        val first = mind.record(MindGame.DIGIT_SPAN, MindRun(6.0, 3, 5))
        assertEquals(6, first.xp)
        assertEquals(6.0 / 7, first.performance, 1e-9)
        assertNull(first.previousBest)
        assertFalse(first.isNewBest)

        val same = mind.record(MindGame.DIGIT_SPAN, MindRun(6.0))
        assertEquals(0, same.xp)

        val better = mind.record(MindGame.DIGIT_SPAN, MindRun(7.0))
        assertTrue(better.isNewBest)
        assertEquals(MindRules.BEST_XP, better.xp)
        assertEquals((6 + MindRules.BEST_XP).toLong(), technicalXp())

        val stored = db.mindDao().forGame(MindGame.DIGIT_SPAN.key).first()
        assertEquals(3, stored.correct)
        assertEquals(5, stored.attempts)

        val summary = mind.observeSummaries().first().single { it.game == MindGame.DIGIT_SPAN }
        assertEquals(7.0, summary.best!!, 0.0)
        assertTrue(summary.playedToday)

        now += Duration.ofDays(1)
        assertFalse(mind.observeSummaries().first().single { it.game == MindGame.DIGIT_SPAN }.playedToday)
    }

    @Test
    fun aBadFirstGameCostsXpButNeverALevel() = runTest {
        val bad = mind.record(MindGame.SPEED_MATH, MindRun(5.0, 5, 15))
        // Nothing to lose yet: the loss is clamped to zero.
        assertEquals(0, bad.xp)
        assertEquals(0L, technicalXp())
    }

    @Test
    fun reactionBestIsTheLowestTime() = runTest {
        mind.record(MindGame.REACTION, MindRun(260.0, 5, 5))
        val slower = mind.record(MindGame.REACTION, MindRun(300.0, 5, 5))
        assertFalse(slower.isNewBest)
        val faster = mind.record(MindGame.REACTION, MindRun(230.0, 5, 5))
        assertTrue(faster.isNewBest)
        assertEquals(230.0, mind.observeSummaries().first().single { it.game == MindGame.REACTION }.best!!, 0.0)
    }
}
