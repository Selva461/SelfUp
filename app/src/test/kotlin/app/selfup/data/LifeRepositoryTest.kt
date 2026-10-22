package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.CompletionInput
import app.selfup.core.DayBoundary
import app.selfup.core.DefaultQuests
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.core.StreakState
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class LifeRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = MutableClock(
        LocalDate.of(2026, 10, 19).atTime(20, 0).atZone(zone).toInstant(),
        zone,
    )

    private lateinit var db: SelfUpDatabase
    private lateinit var repo: LifeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LifeRepository(db, clock, DayBoundary(LocalTime.of(4, 0), zone))
    }

    @After
    fun tearDown() = db.close()

    private fun quest(
        stats: Set<Stat> = setOf(Stat.STRENGTH),
        difficulty: Difficulty = Difficulty.MEDIUM,
        schedule: Schedule = Schedule.Recurring(),
        input: CompletionInput = CompletionInput.Tap,
    ) = Quest(title = "Gym", difficulty = difficulty, stats = stats, schedule = schedule, input = input)

    private suspend fun totalXp(stat: Stat): Long =
        repo.observeStats(Stat.entries).first().single { it.stat == stat }.totalXp

    @Test
    fun saveAndReadBackRoundTripsEveryField() = runTest {
        val original = Quest(
            title = "Log spending",
            difficulty = Difficulty.EASY,
            stats = setOf(Stat.MONEY, Stat.LIFE),
            schedule = Schedule.Recurring(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            input = CompletionInput.Number("₹"),
            reminder = LocalTime.of(22, 45),
        )
        val id = repo.saveQuest(original)
        assertEquals(original.copy(id = id), repo.getQuest(id))
    }

    @Test
    fun editingReplacesStats() = runTest {
        val id = repo.saveQuest(quest(stats = setOf(Stat.STRENGTH, Stat.LIFE)))
        repo.saveQuest(quest(stats = setOf(Stat.TECHNICAL)).copy(id = id, title = "Code"))
        val edited = repo.getQuest(id)!!
        assertEquals("Code", edited.title)
        assertEquals(setOf(Stat.TECHNICAL), edited.stats)
    }

    @Test
    fun completingGrantsSplitXpAndReportsLevelUp() = runTest {
        val id = repo.saveQuest(quest(stats = setOf(Stat.STRENGTH, Stat.LIFE, Stat.MONEY), difficulty = Difficulty.MEDIUM))
        val result = repo.complete(id) as CompletionResult.Completed

        assertEquals(7, totalXp(Stat.STRENGTH))
        assertEquals(7, totalXp(Stat.LIFE))
        assertEquals(6, totalXp(Stat.MONEY))
        assertTrue(result.changes.none { it.leveledUp })
    }

    @Test
    fun crossingAThresholdLevelsUp() = runTest {
        val id = repo.saveQuest(quest(difficulty = Difficulty.HARD))
        repeat(3) {
            repo.complete(id)
            clock.advance(Duration.ofDays(1))
        }
        // 90 XP so far; the fourth hard completion crosses 100.
        val result = repo.complete(id) as CompletionResult.Completed
        assertTrue(result.changes.single().leveledUp)
        assertEquals(2, repo.observeStats().first().single { it.stat == Stat.STRENGTH }.level)
    }

    @Test
    fun aQuestCompletesOncePerDay() = runTest {
        val id = repo.saveQuest(quest())
        repo.complete(id)
        assertEquals(CompletionResult.AlreadyDone, repo.complete(id))
        assertEquals(20, totalXp(Stat.STRENGTH))

        clock.advance(Duration.ofDays(1))
        assertTrue(repo.complete(id) is CompletionResult.Completed)
        assertEquals(40, totalXp(Stat.STRENGTH))
    }

    @Test
    fun afterMidnightCountsForThePreviousQuestDay() = runTest {
        val id = repo.saveQuest(quest())
        clock.set(LocalDate.of(2026, 10, 20).atTime(0, 30).atZone(zone).toInstant())
        repo.complete(id)
        val monday = LocalDate.of(2026, 10, 19)
        assertTrue(repo.observeDay(monday).first().single().isDone)
        assertFalse(repo.observeDay(monday.plusDays(1)).first().single().isDone)
    }

    @Test
    fun questsNotScheduledTodayAreHiddenAndCannotComplete() = runTest {
        // 2026-10-19 is a Monday.
        val id = repo.saveQuest(quest(schedule = Schedule.Recurring(setOf(DayOfWeek.SATURDAY))))
        assertTrue(repo.observeDay(repo.today()).first().isEmpty())
        assertEquals(CompletionResult.NotDue, repo.complete(id))
    }

    @Test
    fun oneTimeQuestDisappearsTheDayAfterItIsDone() = runTest {
        val id = repo.saveQuest(quest(schedule = Schedule.OneTime()))
        repo.complete(id)
        assertTrue(repo.observeDay(repo.today()).first().single().isDone)

        clock.advance(Duration.ofDays(1))
        assertTrue(repo.observeDay(repo.today()).first().isEmpty())
        assertEquals(CompletionResult.AlreadyDone, repo.complete(id))
    }

    @Test
    fun undoTakesBackExactlyWhatWasGranted() = runTest {
        val id = repo.saveQuest(quest(stats = setOf(Stat.STRENGTH, Stat.LIFE, Stat.MONEY)))
        repo.complete(id)
        val changes = repo.undoCompletion(id)!!

        assertEquals(setOf(-7, -6), changes.map { it.delta }.toSet())
        Stat.entries.forEach { assertEquals(0, totalXp(it)) }
        assertFalse(repo.observeDay(repo.today()).first().single().isDone)
        assertNull(repo.undoCompletion(id))
    }

    @Test
    fun numberQuestsStoreTheValueForPrefill() = runTest {
        val id = repo.saveQuest(quest(input = CompletionInput.Number("₹")))
        repo.complete(id, value = 450.0)
        assertEquals(450.0, repo.lastLoggedValue(id)!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun numberQuestsRequireAValue() = runTest {
        repo.complete(repo.saveQuest(quest(input = CompletionInput.Number("₹"))))
    }

    @Test
    fun deletingAQuestKeepsEarnedXp() = runTest {
        val id = repo.saveQuest(quest())
        repo.complete(id)
        repo.deleteQuest(id)
        assertNull(repo.getQuest(id))
        assertEquals(20, totalXp(Stat.STRENGTH))
    }

    @Test
    fun defaultsSeedOnlyOnce() = runTest {
        repo.seedDefaultsOnce()
        val seeded = repo.observeDay(repo.today()).first()
        assertEquals(DefaultQuests.all.count { it.schedule.isDueOn(repo.today()) }, seeded.size)

        seeded.forEach { repo.deleteQuest(it.quest.id) }
        repo.seedDefaultsOnce()
        assertTrue(repo.observeDay(repo.today()).first().isEmpty())
    }

    // Day close: clock starts Monday 20:00; the first close only sets the marker.

    private suspend fun earn(stat: Stat, xp: Int) {
        // One-time HARD quests are 30 XP each; this keeps setup independent of daily quests.
        repeat(xp / 30) {
            repo.complete(repo.saveQuest(quest(stats = setOf(stat), difficulty = Difficulty.HARD, schedule = Schedule.OneTime())))
        }
    }

    @Test
    fun firstCloseOnlySetsTheStartingPoint() = runTest {
        repo.saveQuest(quest())
        assertTrue(repo.closeFinishedDays().isEmpty)
        assertTrue(repo.closeFinishedDays().isEmpty)
    }

    @Test
    fun missingADailyCostsXpAndAddsAMakeUpQuest() = runTest {
        repo.closeFinishedDays()
        earn(Stat.STRENGTH, 30)
        val gym = repo.saveQuest(quest())

        clock.advance(Duration.ofDays(1))
        val report = repo.closeFinishedDays()

        assertEquals(listOf(gym), report.missed.map { it.id })
        assertEquals(5, report.xpLost)
        assertEquals(25, totalXp(Stat.STRENGTH))
        val makeUp = repo.observeDay(repo.today()).first().single { it.quest.penaltyFor == gym }.quest
        assertEquals(Difficulty.MEDIUM, makeUp.difficulty)
        assertEquals(setOf(Stat.STRENGTH), makeUp.stats)
    }

    @Test
    fun closingIsIdempotent() = runTest {
        repo.closeFinishedDays()
        earn(Stat.STRENGTH, 30)
        repo.saveQuest(quest())
        clock.advance(Duration.ofDays(1))
        repo.closeFinishedDays()
        assertTrue(repo.closeFinishedDays().isEmpty)
        assertEquals(25, totalXp(Stat.STRENGTH))
        assertEquals(1, repo.observeAllQuests().first().count { it.penaltyFor != null })
    }

    @Test
    fun completedAndNewQuestsAreNotPenalized() = runTest {
        repo.closeFinishedDays()
        earn(Stat.STRENGTH, 30)
        repo.complete(repo.saveQuest(quest()))
        clock.advance(Duration.ofDays(1))
        // Created on Tuesday, so it can't have been missed on Monday.
        repo.saveQuest(quest())

        assertTrue(repo.closeFinishedDays().isEmpty)
        assertEquals(50, totalXp(Stat.STRENGTH))
    }

    @Test
    fun penaltiesNeverDropALevel() = runTest {
        repo.closeFinishedDays()
        earn(Stat.STRENGTH, 120) // Level 2, 20 XP above its floor.
        repeat(6) { repo.saveQuest(quest()) }
        clock.advance(Duration.ofDays(1))

        assertEquals(20, repo.closeFinishedDays().xpLost)
        assertEquals(100, totalXp(Stat.STRENGTH))
        assertEquals(2, repo.observeStats().first().single { it.stat == Stat.STRENGTH }.level)
    }

    @Test
    fun catchUpPenalizesEachDayButOnlyOffersYesterdaysMakeUp() = runTest {
        repo.closeFinishedDays()
        earn(Stat.STRENGTH, 60)
        val gym = repo.saveQuest(quest())
        clock.advance(Duration.ofDays(3)) // Monday, Tuesday and Wednesday all missed.

        val report = repo.closeFinishedDays()
        assertEquals(15, report.xpLost)
        assertEquals(listOf(gym), report.missed.map { it.id })
        assertEquals(1, repo.observeAllQuests().first().count { it.penaltyFor == gym })
    }

    @Test
    fun aWeekOfClearDaysEarnsAShieldThatAbsorbsTheNextMiss() = runTest {
        repo.closeFinishedDays()
        earn(Stat.STRENGTH, 30)
        val gym = repo.saveQuest(quest())
        repeat(7) {
            repo.complete(gym)
            clock.advance(Duration.ofDays(1))
            repo.closeFinishedDays()
        }
        assertEquals(StreakState(current = 7, best = 7, shields = 1), repo.observeStreak().first())

        clock.advance(Duration.ofDays(1))
        val report = repo.closeFinishedDays()
        assertEquals(1, report.shieldsUsed)
        assertEquals(0, report.xpLost)
        assertTrue(report.missed.isEmpty())
        assertEquals(0, repo.observeAllQuests().first().count { it.penaltyFor == gym })
        assertEquals(StreakState(current = 7, best = 7, shields = 0), repo.observeStreak().first())
    }

    @Test
    fun aMissWithoutAShieldBreaksTheStreak() = runTest {
        repo.closeFinishedDays()
        val gym = repo.saveQuest(quest())
        repeat(3) {
            repo.complete(gym)
            clock.advance(Duration.ofDays(1))
            repo.closeFinishedDays()
        }
        clock.advance(Duration.ofDays(1))
        assertEquals(3, repo.closeFinishedDays().streakLost)
        assertEquals(StreakState(current = 0, best = 3), repo.observeStreak().first())
    }

    @Test
    fun unfinishedMakeUpQuestsExpire() = runTest {
        repo.closeFinishedDays()
        val gym = repo.saveQuest(quest(schedule = Schedule.Recurring(setOf(DayOfWeek.MONDAY))))
        clock.advance(Duration.ofDays(1))
        repo.closeFinishedDays()
        assertEquals(1, repo.observeAllQuests().first().count { it.penaltyFor == gym })

        clock.advance(Duration.ofDays(1))
        repo.closeFinishedDays()
        assertEquals(0, repo.observeAllQuests().first().count { it.penaltyFor == gym })
    }

    @Test
    fun testDayQuestAppearsOnFirstSaturdayAndIsRemovedAfter() = runTest {
        val testDay = LocalDate.of(2026, 11, 7)
        clock.set(testDay.atTime(10, 0).atZone(zone).toInstant())
        repo.closeFinishedDays()
        repo.closeFinishedDays() // idempotent: still one quest
        val onTheDay = repo.observeAllQuests().first().filter { it.title.startsWith("Test day") }
        assertEquals(1, onTheDay.size)
        assertTrue(onTheDay.single().schedule.isDueOn(testDay))

        clock.set(testDay.plusDays(1).atTime(10, 0).atZone(zone).toInstant())
        repo.closeFinishedDays()
        assertTrue(repo.observeAllQuests().first().none { it.title.startsWith("Test day") })
    }
}

private class MutableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    fun advance(duration: Duration) {
        now += duration
    }

    fun set(instant: Instant) {
        now = instant
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
}
