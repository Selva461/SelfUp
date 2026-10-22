package app.selfup.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.DayBoundary
import app.selfup.core.WorkJudge
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
class WorkRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 22).atTime(18, 0).atZone(zone).toInstant(), zone)
    private val boundary = DayBoundary(LocalTime.of(4, 0), zone)

    private lateinit var db: SelfUpDatabase
    private lateinit var work: WorkRepository
    private lateinit var life: LifeRepository

    private val code = """
        SELFUP WORK 1
        TITLE Performance-based XP
        DIFFICULTY 4
        QUALITY 4
        COMPLETE 5
        OWNERSHIP 1
    """.trimIndent()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        work = WorkRepository(db, clock, boundary)
        life = LifeRepository(db, clock, boundary)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aCodePaysOnceByItsRubric() = runTest {
        val added = work.import(code) as WorkImport.Added
        // (0.3×4 + 0.3×5 + 0.4×1) / 5 = 0.62 of 40.
        assertEquals(9, added.xp)
        assertEquals(9, life.observeStats().first().single { it.stat.key == "technical" }.totalXp.toInt())
        assertEquals(WorkImport.Duplicate, work.import(code.replace("Performance-based XP", "performance-based  xp")))
        val logged = work.observe().first().single()
        assertEquals("claude", logged.judge)
        assertEquals(0.62, logged.performance, 1e-9)
    }

    @Test
    fun groqVerdictsAreFiledAsGroqsAndBadCodesExplainThemselves() = runTest {
        val added = work.import(code.replace("TITLE Performance-based XP", "TITLE Other work"), judge = WorkJudge.GROQ) as WorkImport.Added
        assertEquals(WorkJudge.GROQ, added.report.judge)
        assertTrue(added.xp < 9)
        val invalid = work.import("SELFUP WORK 1\nTITLE X") as WorkImport.Invalid
        assertTrue(invalid.message.startsWith("Missing"))
    }
}
