package app.selfup.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selfup.core.CompletionInput
import app.selfup.core.DayBoundary
import app.selfup.core.Difficulty
import app.selfup.core.HealthDay
import app.selfup.core.MealEntry
import app.selfup.core.Quest
import app.selfup.core.Schedule
import app.selfup.core.Stat
import app.selfup.core.StreakState
import app.selfup.focus.ScreenToday
import app.selfup.ui.home.screenTimeSection
import app.selfup.core.StatChange
import app.selfup.core.StatProgress
import app.selfup.data.CompletionEntity
import app.selfup.data.DayQuest
import app.selfup.data.GoalsOverview
import app.selfup.data.MindSummary
import app.selfup.data.XpEventEntity
import app.selfup.data.XpReason
import app.selfup.core.MindGame
import app.selfup.gym.GymStatus
import app.selfup.ui.components.LevelUpOverlay
import app.selfup.ui.system.SystemBackground
import app.selfup.ui.system.BlurLayers
import app.selfup.ui.system.SystemTab
import app.selfup.ui.system.SystemTabBar
import app.selfup.ui.system.SystemTabBarHeight
import app.selfup.ui.system.LocalBlurLayers
import app.selfup.ui.goals.GoalsScreen
import app.selfup.ui.goals.planSection
import app.selfup.ui.home.HomeScreen
import app.selfup.core.DungeonDay
import app.selfup.core.Dungeons
import app.selfup.core.Gold
import app.selfup.core.HunterFacts
import app.selfup.data.HunterOverview
import app.selfup.data.PurchaseEntity
import app.selfup.ui.hunter.HunterScreen
import app.selfup.core.DsaTopic
import app.selfup.data.DsaAttemptEntity
import app.selfup.data.DsaOverview
import app.selfup.data.DsaProblemEntity
import app.selfup.data.DsaProblemView
import app.selfup.core.Dsa
import app.selfup.core.DsaAttempt
import app.selfup.core.DsaOutcome
import app.selfup.ui.dsa.DsaScreen
import app.selfup.ui.dsa.dsaSection
import app.selfup.ui.hunter.HunterTab
import app.selfup.ui.hunter.dungeonSection
import app.selfup.ui.log.LogScreen
import app.selfup.ui.log.LogState
import app.selfup.ui.mind.MindState
import app.selfup.ui.mind.mindSection
import app.selfup.ui.quests.QuestsScreen
import app.selfup.ui.records.RecordsScreen
import app.selfup.ui.records.RecordsState
import app.selfup.ui.skills.SkillsScreen
import app.selfup.ui.skills.SkillsState
import app.selfup.ui.stat.StatDetailScreen
import app.selfup.ui.stat.StatDetailState
import app.selfup.ui.theme.SelfUpTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Not an assertion test: renders every main screen with realistic data to
 * app/build/screens/<name>.png, so the UI can be reviewed without a device. CI publishes them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val zone = ZoneId.of("Asia/Kolkata")

    private val stats = listOf(
        StatProgress(Stat.STRENGTH, 1_340),
        StatProgress(Stat.NUTRITION, 620),
        StatProgress(Stat.TECHNICAL, 2_210),
        StatProgress(Stat.LIFE, 410),
        StatProgress(Stat.MONEY, 180),
    )

    private val quests = listOf(
        Quest(1, "Finish a gym session", Difficulty.MEDIUM, setOf(Stat.STRENGTH), Schedule.Recurring()),
        Quest(2, "Log every meal today", Difficulty.EASY, setOf(Stat.NUTRITION), Schedule.Recurring()),
        Quest(3, "Spend 30 minutes building or learning", Difficulty.MEDIUM, setOf(Stat.TECHNICAL), Schedule.Recurring()),
        Quest(4, "Log today's spending", Difficulty.EASY, setOf(Stat.MONEY), Schedule.Recurring(), input = CompletionInput.Number("₹")),
        Quest(5, "Phone off by 23:00", Difficulty.MEDIUM, setOf(Stat.LIFE), Schedule.Recurring(), penaltyFor = 9),
    )

    private val todayState = TodayState(
        day = today,
        stats = stats,
        quests = quests.mapIndexed { i, q ->
            DayQuest(q, if (i < 2) CompletionEntity(i.toLong(), q.id, today.toEpochDay(), 0, null) else null)
        },
        streak = StreakState(current = 9, best = 14, shields = 1),
    )

    private val tabs = listOf(
        SystemTab("home", "Today", Icons.Filled.Home),
        SystemTab("quests", "Quests", Icons.AutoMirrored.Filled.List),
        SystemTab("records", "Records", Icons.Filled.Star),
        SystemTab("skills", "Skills", Icons.Filled.Build),
        SystemTab("log", "Log", Icons.Filled.Edit),
    )

    private val hunter = run {
        val monday = Dungeons.weekStart(today)
        val days = (0L..2L).map { DungeonDay(monday.plusDays(it), clear = it != 1L, trained = true, studied = it == 0L, scrollMinutes = if (it == 2L) 25 else 6) }
        HunterOverview(
            today = today,
            gold = 1_240,
            dungeon = Dungeons.progress(monday, days),
            facts = HunterFacts(bestStreak = 14, clearDays = 22, personalBests = 4, quizzes = 31, mindGames = 12, lowScrollDays = 5),
            unlocked = mapOf("streak_3" to 0L, "streak_7" to 0L, "pr_1" to 0L, "quiz_25" to 0L),
            rewards = Gold.defaultRewards.mapIndexed { i, r -> r.copy(id = i + 1L) },
            purchases = listOf(PurchaseEntity(1, 2, "Order in or eat out", 300, "custom", 0, today.toEpochDay() - 2, 0)),
            restDays = setOf(today.plusDays(3)),
        )
    }

    private val dsa = run {
        fun problem(id: Long, title: String, topic: DsaTopic, difficulty: String, vararg daysAgo: Long): DsaProblemView {
            val attempts = daysAgo.map { DsaAttemptEntity(0, id, "alone", 20, 1.0, 10, 1, today.toEpochDay() - it, 0) }
            return DsaProblemView(
                DsaProblemEntity(id, title, topic.key, difficulty, null, 0),
                Dsa.replay(attempts.map { DsaAttempt(java.time.LocalDate.ofEpochDay(it.epochDay), DsaOutcome.ALONE, it.minutes) }),
                attempts,
            )
        }
        DsaOverview(
            today,
            listOf(
                problem(1, "Two Sum", DsaTopic.ARRAYS, "easy", 20, 17, 10),
                problem(2, "Group Anagrams", DsaTopic.ARRAYS, "medium", 4),
                problem(3, "Valid Palindrome", DsaTopic.TWO_POINTERS, "easy", 3),
                problem(4, "Longest Substring Without Repeating", DsaTopic.SLIDING_WINDOW, "medium", 1),
            ),
        )
    }

    private val padding = PaddingValues(top = 32.dp, bottom = SystemTabBarHeight + 34.dp)

    @Test
    fun home() = shoot("01-home", tab = "home") {
        HomeScreen(todayState, padding, onComplete = {}, onEdit = {}, onStatClick = {}, title = "Persistent", extra = {
            dungeonSection(hunter, onOpen = {})
            screenTimeSection(
                ScreenToday(hasAccess = true, blockerOn = true, minutes = 22, apps = listOf("Instagram" to 14, "YouTube" to 8)),
                onAllowUsage = {},
                onEnableBlocker = {},
            )
            planSection(GoalsOverview(emptyList(), today), onTask = {}, onGoals = {})
        })
    }

    @Test
    fun quests() = shoot("02-quests", tab = "quests") {
        QuestsScreen(todayState, quests, padding, onComplete = {}, onEdit = {})
    }

    @Test
    fun records() = shoot("03-records", tab = "records") {
        RecordsScreen(RecordsState(emptyList(), LocalDate.of(2026, 11, 7)), padding, GymStatus.NeedsUpdate, onConnectGym = {}, onOpen = {})
    }

    @Test
    fun skills() = shoot("04-skills", tab = "skills") {
        val mind = MindState(
            summaries = MindGame.entries.map { MindSummary(it, listOf(5.0, 6.0, 7.0), playedToday = it == MindGame.DIGIT_SPAN) },
            sessionGames = MindGame.entries.take(3),
        )
        SkillsScreen(SkillsState(today = today), padding, onStart = {}, onOpenPack = {}, extra = {
            dsaSection(dsa, onLog = {}, onProblem = {}, onOpen = {})
            mindSection(mind, onSession = {}, onGame = {})
        })
    }

    @Test
    fun log() = shoot("05-log", tab = "log") {
        val at = today.atTime(13, 10).atZone(zone).toInstant()
        val days = (0L until 7L).map { d ->
            HealthDay(
                today.minusDays(d),
                if (d % 3 == 2L) emptyList() else listOf(MealEntry(1, at.minusSeconds(d * 86_400), "Rice and dal"), MealEntry(2, at.minusSeconds(d * 86_400 - 25_000), "Eggs")),
            )
        }
        val spend = (13 downTo 0).map { today.minusDays(it.toLong()) to listOf(0.0, 240.0, 120.0, 560.0, 90.0)[it % 5] }
        LogScreen(LogState(days = days, spend = spend), DayBoundary(LocalTime.of(4, 0), zone), padding, onMeal = { _, _ -> }, onShare = {})
    }

    @Test
    fun goals() = shoot("06-goals") {
        GoalsScreen(GoalsOverview(emptyList(), today), {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }

    @Test
    fun statDetail() = shoot("07-stat") {
        val events = listOf(
            XpEventEntity(1, Stat.STRENGTH.key, 20, XpReason.COMPLETION, 1, 1, today.toEpochDay(), 0),
            XpEventEntity(2, Stat.STRENGTH.key, 15, XpReason.RECORD, null, null, today.toEpochDay() - 1, 0),
            XpEventEntity(3, Stat.STRENGTH.key, -5, XpReason.PENALTY, 1, null, today.toEpochDay() - 2, 0),
        )
        StatDetailScreen(StatDetailState(stats.first(), events, quests.take(1)), onBack = {})
    }

    @Test
    fun levelUp() = shoot("08-level-up", tab = "home") {
        HomeScreen(todayState, padding, onComplete = {}, onEdit = {}, onStatClick = {})
        LevelUpOverlay(listOf(StatChange(Stat.STRENGTH, 20, 4, 5)), onDismiss = {})
    }

    @Test
    fun hunterDungeon() = shoot("09-hunter-dungeon") { hunterScreen(HunterTab.DUNGEON) }

    @Test
    fun hunterShop() = shoot("10-hunter-shop") { hunterScreen(HunterTab.SHOP) }

    @Test
    fun hunterTitles() = shoot("11-hunter-titles") { hunterScreen(HunterTab.TITLES) }

    @Test
    fun hunterRest() = shoot("12-hunter-rest") { hunterScreen(HunterTab.REST) }

    @Test
    fun dsaTracker() = shoot("13-dsa") { DsaScreen(dsa, onLog = {}, onProblem = {}, onBack = {}) }

    @Composable
    private fun hunterScreen(tab: HunterTab) =
        HunterScreen(hunter, tab, onTab = {}, onBuy = {}, onEditReward = {}, onEquip = {}, onDeclareRest = {}, onCancelRest = {}, onBack = {})

    private fun shoot(name: String, tab: String? = null, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SelfUpTheme {
                val layers = remember { BlurLayers(backdrop = HazeState(), chrome = HazeState()) }
                CompositionLocalProvider(LocalBlurLayers provides layers) {
                    Box(Modifier.fillMaxSize()) {
                        SystemBackground(
                            Modifier
                                .fillMaxSize()
                                .hazeSource(layers.backdrop)
                                .hazeSource(layers.chrome, zIndex = 0f),
                        )
                        Box(Modifier.fillMaxSize().hazeSource(layers.chrome, zIndex = 1f)) { content() }
                        if (tab != null) {
                            SystemTabBar(
                                tabs,
                                selected = tab,
                                onSelect = {},
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
        // Let entry animations and springs settle before capturing.
        compose.mainClock.advanceTimeBy(1_500)
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }
}
