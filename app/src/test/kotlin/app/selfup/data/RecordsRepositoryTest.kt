package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.ImportedRecord
import app.selfup.core.Metric
import app.selfup.core.RecordEntry
import app.selfup.core.RecordKind
import app.selfup.core.RecordMath
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RecordsRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 21).atTime(21, 30).atZone(zone).toInstant(), zone)
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    private lateinit var db: SelfUpDatabase
    private lateinit var records: RecordsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        records = RecordsRepository(db, clock, boundary)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun strengthXp(): Long =
        db.xpDao().totals().firstOrNull { it.stat == Stat.STRENGTH.key }?.total ?: 0

    @Test
    fun firstEntryIsBaselineThenPersonalBestEarnsBonus() = runTest {
        val baseline = records.log(RecordKind.BENCH, weightKg = 60.0, reps = 5)
        assertTrue(baseline.personalBests.isEmpty())
        assertEquals(0, strengthXp())

        val pr = records.log(RecordKind.BENCH, weightKg = 65.0, reps = 5)
        assertEquals(listOf(Metric.EST_1RM), pr.personalBests.map { it.metric })
        assertEquals(RecordMath.PR_XP.toLong(), strengthXp())
        assertEquals(RecordMath.PR_XP, pr.changes.single().delta)

        val summary = records.observeSummaries().first().first { it.kind == RecordKind.BENCH }
        assertEquals(2, summary.entries.size)
        assertEquals(65.0 * (1 + 5 / 30.0), summary.bests.getValue(Metric.EST_1RM), 1e-9)
        assertEquals(65.0, records.latest(RecordKind.BENCH)?.weightKg)
    }

    @Test
    fun deletingAnEntryTakesBackItsBonus() = runTest {
        records.log(RecordKind.PLANK, seconds = 60)
        val pr = records.log(RecordKind.PLANK, seconds = 75)
        assertEquals(RecordMath.PR_XP.toLong(), strengthXp())

        records.delete(pr.entryId)
        assertEquals(0, strengthXp())
        assertEquals(1, records.observeSummaries().first().first { it.kind == RecordKind.PLANK }.entries.size)
    }

    private fun gymBench(session: Long, kg: Double, daysAgo: Long) = ImportedRecord(
        "gym:session:$session:bench",
        RecordEntry(RecordKind.BENCH, clock.instant().minus(Duration.ofDays(daysAgo)), weightKg = kg, reps = 5),
    )

    @Test
    fun gymSyncIsIdempotentAndHistoryEarnsNoBonus() = runTest {
        val connected = clock.instant().minus(Duration.ofDays(2))
        val history = listOf(gymBench(1, 60.0, daysAgo = 10), gymBench(2, 65.0, daysAgo = 5))

        val first = records.syncImported("gym:", history, earnsFrom = connected)
        assertEquals(2, first.added)
        assertTrue(first.personalBests.isEmpty())
        assertEquals(0, strengthXp())

        val again = records.syncImported("gym:", history, earnsFrom = connected)
        assertEquals(0, again.added + again.updated + again.removed)
        val entries = records.observeSummaries().first().first { it.kind == RecordKind.BENCH }.entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.imported })
    }

    @Test
    fun gymWorkoutAfterConnectingEarnsBonusAndDeletingItInGymTakesItBack() = runTest {
        val connected = clock.instant().minus(Duration.ofDays(2))
        records.log(RecordKind.BENCH, weightKg = 60.0, reps = 5)
        val withPr = listOf(gymBench(1, 60.0, daysAgo = 10), gymBench(3, 70.0, daysAgo = 0))
        val result = records.syncImported("gym:", withPr, earnsFrom = connected)
        assertEquals(listOf(RecordKind.BENCH), result.personalBests.map { it.first })
        assertEquals(RecordMath.PR_XP.toLong(), strengthXp())

        val edited = records.syncImported("gym:", listOf(withPr[0], gymBench(3, 72.5, daysAgo = 0)), earnsFrom = connected)
        assertEquals(1, edited.updated)
        assertEquals(RecordMath.PR_XP.toLong(), strengthXp())

        val deleted = records.syncImported("gym:", listOf(withPr[0]), earnsFrom = connected)
        assertEquals(1, deleted.removed)
        assertEquals(0, strengthXp())
        // The entry logged in SelfUp is never touched by a sync.
        assertEquals(2, records.observeSummaries().first().first { it.kind == RecordKind.BENCH }.entries.size)
    }

    @Test
    fun bodyWeightGoesToNutritionAndNeverEarnsBonus() = runTest {
        records.log(RecordKind.BODY_WEIGHT, weightKg = 78.0)
        val next = records.log(RecordKind.BODY_WEIGHT, weightKg = 77.0)
        assertTrue(next.personalBests.isEmpty())
        assertTrue(db.xpDao().totals().isEmpty())
    }
}

/** Upgrading a v1 install must keep its data and pass Room's schema check. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {
    private val name = "migration-test.db"
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun upgradingFromV1AddsNewTablesAndKeepsData() = runTest {
        // Build the current schema, then turn it back into a v1 file: drop what v2 added.
        Room.databaseBuilder(context, SelfUpDatabase::class.java, name).allowMainThreadQueries().build().apply {
            openHelper.writableDatabase.run {
                execSQL("INSERT INTO app_meta (`key`, value) VALUES ('seeded_default_quests', '1')")
                downgradeToV1()
            }
            close()
        }

        val migrated = Room.databaseBuilder(context, SelfUpDatabase::class.java, name)
            .addMigrations(*SelfUpDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("1", migrated.metaDao().get("seeded_default_quests"))
            migrated.recordDao().insert(
                RecordEntryEntity(kind = "run", atMillis = 0, weightKg = null, reps = null, seconds = 1800, distanceKm = 5.0, xpAwarded = 0),
            )
            assertEquals(1, migrated.recordDao().history("run").size)
            migrated.skillDao().insert(
                SkillEventEntity(conceptId = "java.strings", kind = SkillEventKind.READ, epochDay = 0, atMillis = 0, correct = null, total = null),
            )
            assertEquals(1, migrated.skillDao().forConcept("java.strings").size)
            migrated.mindDao().insert(MindScoreEntity(game = "reaction", score = 240.0, epochDay = 0, atMillis = 0, xpAwarded = 0))
            assertEquals(1, migrated.mindDao().forGame("reaction").size)
            migrated.goalDao().insert(
                GoalEntity(measure = "mind:reaction", target = 220.0, baseline = null, startEpochDay = 0, deadlineEpochDay = 60, state = GoalState.ACTIVE, createdAtMillis = 0),
            )
            assertEquals(1, migrated.goalDao().withState(GoalState.ACTIVE).size)
        } finally {
            migrated.close()
        }
    }

    private fun SupportSQLiteDatabase.downgradeToV1() {
        execSQL("DROP TABLE record_entry")
        execSQL("DROP TABLE skill_event")
        execSQL("DROP TABLE mind_score")
        execSQL("DROP TABLE goal")
        version = 1
    }
}
