package app.selfup.focus

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.core.UsageEvent
import app.selfup.core.UsageKind
import app.selfup.data.LifeRepository
import app.selfup.data.SelfUpDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class FocusRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 10, 19)
    private val clock = SettableClock(at(monday, "20:00"), zone)
    private val source = FakeUsage()
    private val insta = "com.instagram.android"

    private lateinit var db: SelfUpDatabase
    private lateinit var life: LifeRepository
    private lateinit var focus: FocusRepository

    private fun at(day: LocalDate, time: String): Instant = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        life = LifeRepository(db, clock, DayBoundary(LocalTime.of(4, 0), zone))
        focus = FocusRepository(source, life, db.metaDao(), db.trackingDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun phoneOffQuest(): Long {
        val id = life.saveQuest(Quest(title = "Phone off by 23:00", difficulty = Difficulty.MEDIUM, stats = setOf(Stat.LIFE), schedule = Schedule.Recurring()))
        life.closeFinishedDays()
        return id
    }

    @Test
    fun aQuietNightIsVerifiedAndALightDayEarnsTheBonus() = runTest {
        val id = phoneOffQuest()
        source.events += listOf(
            UsageEvent(insta, at(monday, "12:00"), UsageKind.RESUMED),
            UsageEvent(insta, at(monday, "12:12"), UsageKind.PAUSED),
            UsageEvent("launcher", at(monday, "22:40"), UsageKind.RESUMED),
        )
        clock.now = at(monday.plusDays(1), "09:00")

        val report = focus.settle()
        assertEquals(listOf(id), report.verified.map { it.id })
        assertEquals(10, report.bonusXp)
        assertTrue(life.closeFinishedDays().missed.isEmpty())
        assertEquals(30, life.observeStats().first().single { it.stat == Stat.LIFE }.totalXp.toInt())
        assertTrue(focus.settle().isEmpty)

        val day = monday.toEpochDay()
        val tracking = db.trackingDao()
        assertEquals(12 * 60_000L, tracking.phoneDay(day)?.entertainmentMillis)
        assertEquals(2, tracking.phoneDay(day)?.opens)
        assertEquals(setOf(insta, "launcher"), tracking.appUsageOn(day).map { it.pkg }.toSet())
        assertEquals(listOf("verified", "bonus"), tracking.focusEventsOn(day).map { it.kind })
        assertEquals("CLEAR", tracking.recentDays(1).single().outcome)
    }

    @Test
    fun lateUseFailsTheCheckAndHeavyUseEarnsNothing() = runTest {
        phoneOffQuest()
        source.events += listOf(
            UsageEvent(insta, at(monday, "21:00"), UsageKind.RESUMED),
            UsageEvent(insta, at(monday, "21:45"), UsageKind.PAUSED),
            UsageEvent(insta, at(monday.plusDays(1), "00:30"), UsageKind.RESUMED),
        )
        clock.now = at(monday.plusDays(1), "09:00")
        assertTrue(focus.settle().isEmpty)
        assertEquals(1, life.closeFinishedDays().missed.size)
    }

    @Test
    fun withoutAccessNothingIsReadOrBlocked() = runTest {
        source.access = false
        phoneOffQuest()
        clock.now = at(monday.plusDays(1), "09:00")
        assertTrue(focus.settle().isEmpty)
        assertFalse(focus.shouldBlock(insta))
    }

    @Test
    fun appsLockPastTheBudgetWithOneGraceUnlockADay() = runTest {
        source.events += listOf(
            UsageEvent(insta, at(monday, "18:00"), UsageKind.RESUMED),
            UsageEvent(insta, at(monday, "18:35"), UsageKind.PAUSED),
        )
        assertTrue(focus.shouldBlock(insta))
        assertFalse(focus.shouldBlock("com.whatsapp"))

        assertTrue(focus.useGrace())
        assertFalse(focus.shouldBlock(insta))
        assertFalse(focus.useGrace())

        clock.now = at(monday, "20:06")
        assertTrue(focus.shouldBlock(insta))
        assertEquals(listOf("locked", "grace", "locked"), db.trackingDao().focusEventsOn(monday.toEpochDay()).map { it.kind })
    }
}

private class FakeUsage : UsageSource {
    var access = true
    val events = mutableListOf<UsageEvent>()
    override fun hasAccess() = access
    override fun events(start: Instant, end: Instant) = events.filter { !it.at.isBefore(start) && it.at.isBefore(end) }
}

private class SettableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = SettableClock(now, zone)
}
