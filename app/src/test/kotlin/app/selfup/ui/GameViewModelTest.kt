package app.selfup.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.Difficulty
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.data.LifeRepository
import app.selfup.data.SelfUpDatabase
import app.selfup.ui.components.levelUpMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class GameViewModelTest {
    private lateinit var db: SelfUpDatabase
    private lateinit var repo: LifeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SelfUpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LifeRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private val hardGym = Quest(
        title = "Gym",
        difficulty = Difficulty.HARD,
        stats = setOf(Stat.STRENGTH),
        schedule = Schedule.Recurring(),
    )

    @Test
    fun completingEmitsCompletedWithTheXp() = runTest {
        val id = repo.saveQuest(hardGym)
        val game = GameViewModel(repo)
        game.complete(hardGym.copy(id = id))

        val event = game.events.first() as GameEvent.Completed
        assertEquals(30, event.changes.single().delta)
    }

    @Test
    fun crossingALevelEmitsLevelUpBeforeCompleted() = runTest {
        // 90 XP on Strength already; one more hard quest crosses 100.
        repeat(3) {
            val id = repo.saveQuest(hardGym.copy(schedule = Schedule.OneTime()))
            repo.complete(id)
        }
        val id = repo.saveQuest(hardGym)
        val game = GameViewModel(repo)
        game.complete(hardGym.copy(id = id))

        val events = game.events.take(2).toList()
        val levelUp = events[0] as GameEvent.LeveledUp
        assertEquals(2, levelUp.changes.single().levelAfter)
        assertTrue(events[1] is GameEvent.Completed)
    }

    @Test
    fun levelUpMessageNamesTheStatAndRankChanges() {
        assertEquals(
            "Strength reached Lv 3",
            levelUpMessage(listOf(StatChange(Stat.STRENGTH, 30, levelBefore = 2, levelAfter = 3))),
        )
        assertEquals(
            "Money reached Lv 10 · now Apprentice",
            levelUpMessage(listOf(StatChange(Stat.MONEY, 10, levelBefore = 9, levelAfter = 10))),
        )
    }
}
