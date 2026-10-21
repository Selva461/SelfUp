package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.GoalMeasure
import app.selfup.core.GoalStatus
import app.selfup.core.Metric
import app.selfup.core.MindGame
import app.selfup.core.PlanAction
import app.selfup.core.PlanCode
import app.selfup.core.PlanCodeResult
import app.selfup.core.RecordKind
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
class GoalsRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    // A Monday, which is a lift day in the built-in plan.
    private var now: Instant = LocalDate.of(2026, 10, 19).atTime(8, 0).atZone(zone).toInstant()
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private lateinit var db: SelfUpDatabase
    private lateinit var goals: GoalsRepository
    private lateinit var records: RecordsRepository
    private lateinit var mind: MindRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        goals = GoalsRepository(db, clock, boundary)
        records = RecordsRepository(db, clock, boundary)
        mind = MindRepository(db, clock, boundary)
    }

    @After
    fun tearDown() = db.close()

    private val bench = GoalMeasure.Record(RecordKind.BENCH, Metric.EST_1RM)

    @Test
    fun aRecentRecordIsTheBaselineAndLoggingTodayTicksTheTask() = runTest {
        records.log(RecordKind.BENCH, weightKg = 60.0, reps = 5) // 70 kg estimated
        goals.add(bench, target = 80.0, deadline = goals.today().plusWeeks(10))

        var view = goals.observe().first().active.single()
        assertEquals(70.0, view.goal.baseline!!, 1e-9)
        assertEquals(GoalStatus.ON_TRACK, view.status)
        val task = view.tasks.single()
        assertEquals(PlanAction.LogRecord(RecordKind.BENCH), task.task.action)
        // The record logged earlier today already counts.
        assertTrue(task.done)

        now += Duration.ofDays(3) // Thursday, the next lift day
        view = goals.observe().first().active.single()
        assertFalse(view.tasks.single().done)
        records.log(RecordKind.BENCH, weightKg = 65.0, reps = 5)
        view = goals.observe().first().active.single()
        assertTrue(view.tasks.single().done)
        assertEquals(65.0 * (1 + 5 / 30.0), view.current!!, 1e-9)
    }

    @Test
    fun withoutARecentTestTheFirstTaskIsTheBaseline() = runTest {
        goals.add(GoalMeasure.Mind(MindGame.DIGIT_SPAN), target = 9.0, deadline = goals.today().plusWeeks(8))
        var view = goals.observe().first().active.single()
        assertEquals(GoalStatus.NEEDS_BASELINE, view.status)
        assertTrue(view.tasks.single().task.isTest)

        mind.record(MindGame.DIGIT_SPAN, 6.0)
        view = goals.observe().first().active.single()
        assertEquals(6.0, view.goal.baseline!!, 1e-9)
        assertTrue(view.tasks.single().done)
    }

    @Test
    fun aFourthGoalWaitsAndStartsWhenOneIsDropped() = runTest {
        val deadline = goals.today().plusWeeks(6)
        goals.add(bench, 80.0, deadline)
        goals.add(GoalMeasure.Skill("java_core"), 10.0, deadline)
        goals.add(GoalMeasure.Mind(MindGame.REACTION), 220.0, deadline)
        val fourth = goals.add(GoalMeasure.Record(RecordKind.RUN, Metric.DISTANCE), 10.0, deadline)
        assertTrue(fourth is AddGoalResult.Queued)

        var overview = goals.observe().first()
        assertEquals(3, overview.active.size)
        assertEquals(1, overview.queued.size)
        // A skill goal's baseline is today's count, known at once.
        assertEquals(0.0, overview.active.first { it.goal.measure is GoalMeasure.Skill }.goal.baseline!!, 0.0)

        now += Duration.ofDays(7)
        goals.drop(overview.active.first().goal.id)
        overview = goals.observe().first()
        assertEquals(3, overview.active.size)
        assertTrue(overview.queued.isEmpty())
        val promoted = overview.active.single { it.goal.id == (fourth as AddGoalResult.Queued).id }
        assertEquals(goals.today(), promoted.goal.start)
        assertEquals(goals.today().plusWeeks(6), promoted.goal.deadline)
        assertNull(promoted.goal.baseline)
    }

    @Test
    fun anImportedPlanReplacesTheBuiltInTasks() = runTest {
        val code = "SELFUP PLAN 1\nGOAL record:bench:EST_1RM 80 12w\nMON Bench 5x5 at 60 kg | Log the top set"
        val imported = (PlanCode.parse(code) as PlanCodeResult.Ok).goals
        val results = goals.import(imported)
        assertTrue(results.single() is AddGoalResult.Active)
        val view = goals.observe().first().active.single()
        // No recent bench record, so the baseline test comes first.
        assertTrue(view.tasks.single().task.isTest)
        records.log(RecordKind.BENCH, weightKg = 60.0, reps = 5)
        val after = goals.observe().first().active.single()
        assertEquals("Bench 5x5 at 60 kg", after.tasks.single().task.title)
        assertTrue(after.tasks.single().done)
        assertEquals(goals.today().plusWeeks(12), after.goal.deadline)
    }
}
