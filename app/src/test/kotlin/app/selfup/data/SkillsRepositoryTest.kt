package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.Mastery
import app.selfup.core.SkillRules
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
class SkillsRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)
    private var now: Instant = LocalDate.of(2026, 10, 21).atTime(8, 0).atZone(zone).toInstant()
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private lateinit var db: SelfUpDatabase
    private lateinit var skills: SkillsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        skills = SkillsRepository(db, clock, boundary)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun technicalXp(): Long =
        db.xpDao().totals().firstOrNull { it.stat == Stat.TECHNICAL.key }?.total ?: 0

    @Test
    fun theFirstQuizOfTheDayIsScoredAndSpacedStrongScoresMakeSolid() = runTest {
        val read = skills.read("java.hashmap")
        assertEquals(Mastery.MET, read.after)
        assertEquals(0, read.xp)

        // 4/5 isn't perfect: 5 XP, not the full 10.
        val first = skills.quiz("java.hashmap", 4, 5)
        assertEquals(Mastery.RECALLED, first.after)
        assertEquals(5, first.xp)

        // A perfect retake the same day earns nothing: the first try counts.
        val retake = skills.quiz("java.hashmap", 5, 5)
        assertEquals(0, retake.xp)
        assertEquals(5L, technicalXp())

        now += Duration.ofDays(3)
        val spaced = skills.quiz("java.hashmap", 5, 5)
        assertEquals(Mastery.SOLID, spaced.after)
        assertTrue(spaced.masteryUp)
        assertEquals(SkillRules.QUIZ_XP + SkillRules.MASTERY_BONUS_XP, spaced.xp)

        val states = skills.observeStates().first()
        assertEquals(Mastery.SOLID, states.getValue("java.hashmap").mastery)
        assertEquals(setOf("java.hashmap"), states.keys)
    }

    @Test
    fun appliedNeedsSolidFirst() = runTest {
        assertEquals(Mastery.UNSEEN, skills.applied("java.streams").after)
        assertEquals(0L, technicalXp())
    }

}
