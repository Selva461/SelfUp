package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.MindGame
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
    fun firstGameOfTheDayEarnsXpAndOnlyBeatingYourBestEarnsMore() = runTest {
        val first = mind.record(MindGame.DIGIT_SPAN, 6.0)
        assertEquals(MindRules.SESSION_XP, first.xp)
        assertNull(first.previousBest)
        assertFalse(first.isNewBest)

        val same = mind.record(MindGame.DIGIT_SPAN, 6.0)
        assertEquals(0, same.xp)

        val better = mind.record(MindGame.DIGIT_SPAN, 7.0)
        assertTrue(better.isNewBest)
        assertEquals(MindRules.BEST_XP, better.xp)
        assertEquals((MindRules.SESSION_XP + MindRules.BEST_XP).toLong(), technicalXp())

        val summary = mind.observeSummaries().first().single { it.game == MindGame.DIGIT_SPAN }
        assertEquals(7.0, summary.best!!, 0.0)
        assertTrue(summary.playedToday)

        now += Duration.ofDays(1)
        assertFalse(mind.observeSummaries().first().single { it.game == MindGame.DIGIT_SPAN }.playedToday)
    }

    @Test
    fun reactionBestIsTheLowestTime() = runTest {
        mind.record(MindGame.REACTION, 260.0)
        val slower = mind.record(MindGame.REACTION, 300.0)
        assertFalse(slower.isNewBest)
        val faster = mind.record(MindGame.REACTION, 230.0)
        assertTrue(faster.isNewBest)
        assertEquals(230.0, mind.observeSummaries().first().single { it.game == MindGame.REACTION }.best!!, 0.0)
    }
}
