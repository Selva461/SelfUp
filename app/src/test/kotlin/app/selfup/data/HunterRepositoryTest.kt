package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.Difficulty
import app.selfup.core.DungeonRank
import app.selfup.core.Dungeons
import app.selfup.core.Performance
import app.selfup.core.Quest
import app.selfup.core.RestRefusal
import app.selfup.core.RewardKind
import app.selfup.core.Schedule
import app.selfup.core.Stat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class HunterRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 10, 19)
    private val clock = SettableClock(monday.atTime(20, 0).atZone(zone).toInstant(), zone)

    private lateinit var db: SelfUpDatabase
    private lateinit var life: LifeRepository
    private lateinit var hunter: HunterRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        life = LifeRepository(db, clock, DayBoundary(LocalTime.of(4, 0), zone))
        hunter = HunterRepository(db, life)
    }

    @After
    fun tearDown() = db.close()

    private fun daily(title: String = "Train", stats: Set<Stat> = setOf(Stat.STRENGTH)) =
        Quest(title = title, difficulty = Difficulty.MEDIUM, stats = stats, schedule = Schedule.Recurring())

    private suspend fun earn(stat: Stat, xp: Int) {
        repeat(xp / 30) {
            life.complete(life.saveQuest(Quest(title = "Big", difficulty = Difficulty.HARD, stats = setOf(stat), schedule = Schedule.OneTime())))
        }
    }

    private suspend fun totalXp(stat: Stat): Long = life.observeStats(Stat.entries).first().single { it.stat == stat }.totalXp

    private suspend fun nextDay() {
        clock.advance(Duration.ofDays(1))
        life.closeFinishedDays()
    }

    @Test
    fun restDayExcusesMissesWithoutAPenaltyOrMakeUpQuest() = runTest {
        clock.set(monday.atTime(10, 0).atZone(zone).toInstant())
        life.closeFinishedDays()
        earn(Stat.STRENGTH, 30)
        life.saveQuest(daily())
        assertNull(hunter.declareRest(monday))

        clock.advance(Duration.ofDays(1))
        val report = life.closeFinishedDays()

        assertEquals(1, report.restedDays)
        assertEquals(0, report.xpLost)
        assertTrue(report.missed.isEmpty())
        assertEquals(30, totalXp(Stat.STRENGTH))
        assertTrue(life.observeAllQuests().first().none { it.penaltyFor != null })
    }

    @Test
    fun restCanNotBeDeclaredForTodayAfterNoon() = runTest {
        assertEquals(RestRefusal.TOO_LATE, hunter.declareRest(monday))
        assertNull(hunter.declareRest(monday.plusDays(1)))
        assertEquals(RestRefusal.ALREADY, hunter.declareRest(monday.plusDays(1)))
        assertTrue(hunter.cancelRest(monday.plusDays(1)))
        assertNull(hunter.declareRest(monday.plusDays(1)))
    }

    @Test
    fun xpBeforeGoldConvertsOnce() = runTest {
        earn(Stat.STRENGTH, 30)
        clock.advance(Duration.ofDays(1))
        life.closeFinishedDays()
        hunter.settle()
        assertEquals(30, db.hunterDao().gold())
        hunter.settle()
        assertEquals(30, db.hunterDao().gold())
    }

    @Test
    fun eachClosedDayPaysItsNetXpPlusAClearBonus() = runTest {
        life.closeFinishedDays()
        hunter.settle()
        earn(Stat.STRENGTH, 30)
        life.complete(life.saveQuest(daily()))

        nextDay()
        val report = hunter.settle()

        // 30 + 20 XP on Monday, every daily done.
        assertEquals(60, report.dayGold)
        assertEquals(60, db.hunterDao().gold())
        assertEquals(0, hunter.settle().dayGold)
    }

    @Test
    fun buyingSpendsGoldAndScreenMinutesAreCappedPerDay() = runTest {
        hunter.settle()
        val screen = hunter.observe().first().rewards.single { it.kind == RewardKind.SCREEN_MINUTES }
        db.hunterDao().insertGold(GoldEventEntity(delta = 100, reason = GoldReason.START, ref = "test:1", epochDay = monday.toEpochDay(), atMillis = 0))

        assertEquals(BuyResult.TooPoor(screen.price - 100), hunter.buy(screen.id))

        db.hunterDao().insertGold(GoldEventEntity(delta = 500, reason = GoldReason.START, ref = "test:2", epochDay = monday.toEpochDay(), atMillis = 0))
        val first = hunter.buy(screen.id) as BuyResult.Bought
        assertEquals(600 - screen.price, first.balance)
        assertEquals(listOf("shop_1"), first.unlocked.map { it.id })
        assertEquals(screen.minutes, hunter.extraMinutesToday())

        hunter.buy(screen.id)
        assertEquals(screen.minutes * 2, hunter.extraMinutesToday())
        assertEquals(BuyResult.DailyLimit, hunter.buy(screen.id))

        // Bought minutes last until the reset.
        clock.advance(Duration.ofDays(1))
        assertEquals(0, hunter.extraMinutesToday())
    }

    @Test
    fun deletedRewardsLeaveTheShopButNotThePurchaseHistory() = runTest {
        hunter.settle()
        val reward = hunter.observe().first().rewards.first { it.kind == RewardKind.CUSTOM }
        db.hunterDao().insertGold(GoldEventEntity(delta = 5_000, reason = GoldReason.START, ref = "test", epochDay = monday.toEpochDay(), atMillis = 0))
        hunter.buy(reward.id)
        hunter.deleteReward(reward.id)

        val overview = hunter.observe().first()
        assertTrue(overview.rewards.none { it.id == reward.id })
        assertEquals(reward.title, overview.purchases.single().title)
        assertEquals(BuyResult.NotFound, hunter.buy(reward.id))
    }

    @Test
    fun aFinishedWeekSettlesTheDungeonOnce() = runTest {
        life.closeFinishedDays()
        hunter.settle()
        val train = life.saveQuest(daily())
        repeat(7) {
            life.complete(train)
            nextDay()
        }
        // Monday again: last week clears with 7/5 clear days, 7/4 training days and no study.
        val report = hunter.settle()
        val run = requireNotNull(report.dungeon)
        val performance = 2.0 / 3
        assertEquals(DungeonRank.C.name, run.rank)
        assertEquals(performance, run.performance, 1e-9)
        assertEquals(Performance.xp(Dungeons.FULL_XP, performance), run.xp)
        assertEquals(Performance.xp(Dungeons.FULL_GOLD, performance), run.gold)
        assertEquals(run.xp, report.dungeonChanges.sumOf { it.delta })
        assertTrue(report.unlocked.any { it.id == "dungeon_1" })
        assertNull(hunter.settle().dungeon)
    }

    @Test
    fun aWeekJoinedPartWayIsAWarmUp() = runTest {
        clock.set(monday.plusDays(3).atTime(20, 0).atZone(zone).toInstant())
        hunter.settle()
        assertTrue(!hunter.observe().first().dungeonScored)
        repeat(4) { nextDay() }
        // Monday the 26th: the warm-up week isn't scored.
        assertNull(hunter.settle().dungeon)
        assertTrue(hunter.observe().first().dungeonScored)
    }

    @Test
    fun achievementsUnlockOncePayGoldAndCanBeWorn() = runTest {
        db.recordDao().insert(RecordEntryEntity(kind = "bench", atMillis = clock.millis(), weightKg = 60.0, reps = 5, seconds = null, distanceKm = null, xpAwarded = 15))
        assertEquals(listOf("pr_1"), hunter.checkAchievements().map { it.id })
        assertTrue(hunter.checkAchievements().isEmpty())
        assertEquals(20, db.hunterDao().gold())

        hunter.equipTitle("streak_100")
        assertNull(hunter.observe().first().title)
        hunter.equipTitle("pr_1")
        assertEquals("First Blood", hunter.observe().first().title?.title)
    }
}

private class SettableClock(private var now: Instant, private val zone: ZoneId) : Clock() {
    fun advance(duration: Duration) {
        now += duration
    }

    fun set(instant: Instant) {
        now = instant
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = SettableClock(now, zone)
}
