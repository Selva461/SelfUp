package app.selfup.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.selfup.core.CompletionInput
import app.selfup.core.GoalMeasure
import app.selfup.core.GoalRules
import app.selfup.core.Mastery
import app.selfup.core.MindGame
import app.selfup.core.PersonalBest
import app.selfup.core.PlanAction
import app.selfup.core.Quest
import app.selfup.core.RecordKind
import app.selfup.core.RecordMath
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.core.packs.Packs
import app.selfup.ai.AiSettings
import app.selfup.ai.GroqPlanner
import app.selfup.data.GoalsRepository
import app.selfup.data.HealthRepository
import app.selfup.data.LifeRepository
import app.selfup.data.MindRepository
import app.selfup.data.RecordsRepository
import app.selfup.data.SkillResult
import app.selfup.data.SkillsRepository
import app.selfup.gym.GymBridge
import app.selfup.gym.GymStatus
import app.selfup.gym.GymSync
import app.selfup.ui.components.CelebrationOverlay
import app.selfup.ui.components.LevelUpOverlay
import app.selfup.ui.components.NumberEntryDialog
import app.selfup.ui.system.SystemBackground
import app.selfup.ui.system.SystemIconButton
import app.selfup.ui.system.BlurLayers
import app.selfup.ui.system.SystemSnackbar
import app.selfup.ui.system.SystemTab
import app.selfup.ui.system.SystemTabBar
import app.selfup.ui.system.SystemTabBarHeight
import app.selfup.ui.system.LocalBlurLayers
import app.selfup.ui.goals.AddGoalSheet
import app.selfup.ui.goals.AiPlanSheet
import app.selfup.ui.goals.GoalsEvent
import app.selfup.ui.goals.GoalsScreen
import app.selfup.ui.goals.GoalsViewModel
import app.selfup.ui.goals.ImportPlanSheet
import app.selfup.ui.goals.planSection
import app.selfup.ui.home.HomeScreen
import app.selfup.ui.log.LogEvent
import app.selfup.ui.log.LogScreen
import app.selfup.ui.log.LogViewModel
import app.selfup.ui.mind.MindEvent
import app.selfup.ui.mind.MindSessionScreen
import app.selfup.ui.mind.MindViewModel
import app.selfup.ui.mind.mindSection
import app.selfup.ui.quests.QuestEditorSheet
import app.selfup.ui.quests.QuestsScreen
import app.selfup.ui.records.RecordFormat
import app.selfup.ui.records.RecordLogSheet
import app.selfup.ui.records.RecordsEvent
import app.selfup.ui.records.RecordsScreen
import app.selfup.ui.records.RecordsViewModel
import app.selfup.ui.skills.ConceptScreen
import app.selfup.ui.skills.PackScreen
import app.selfup.ui.skills.QuizScreen
import app.selfup.ui.skills.SkillsEvent
import app.selfup.ui.skills.SkillsScreen
import app.selfup.ui.skills.SkillsViewModel
import app.selfup.ui.stat.StatDetailScreen
import app.selfup.ui.stat.StatDetailViewModel
import app.selfup.ui.theme.SystemColors
import app.selfup.ui.theme.color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Tabs = listOf(
    SystemTab(Routes.HOME, "Today", Icons.Filled.Home),
    SystemTab(Routes.QUESTS, "Quests", Icons.AutoMirrored.Filled.List),
    SystemTab(Routes.RECORDS, "Records", Icons.Filled.Star),
    SystemTab(Routes.SKILLS, "Skills", Icons.Filled.Build),
    SystemTab(Routes.LOG, "Log", Icons.Filled.Edit),
)

private object Routes {
    const val HOME = "home"
    const val QUESTS = "quests"
    const val LOG = "log"
    const val RECORDS = "records"
    const val SKILLS = "skills"
    const val PACK = "pack/{id}"
    const val CONCEPT = "concept/{id}"
    const val QUIZ = "quiz/{ids}/{learn}"
    fun pack(id: String) = "pack/$id"
    fun concept(id: String) = "concept/$id"
    fun quiz(ids: List<String>, learn: List<String>) = "quiz/${ids.joinToString(",")}/${learn.joinToString(",").ifEmpty { "-" }}"
    const val GOALS = "goals"
    const val MIND = "mind/{games}"
    fun mind(games: List<MindGame>) = "mind/${games.joinToString(",") { it.key }}"
    const val STAT = "stat/{key}"
    fun stat(stat: Stat) = "stat/${stat.key}"
}

/** Which quest the editor is open for: none, a new one, or an existing one. */
private sealed interface Editing {
    data object New : Editing
    data class Existing(val quest: Quest) : Editing
}

@Composable
fun SelfUpApp(repository: LifeRepository, health: HealthRepository, records: RecordsRepository, skills: SkillsRepository, mind: MindRepository, goals: GoalsRepository, ai: AiSettings, planner: GroqPlanner, gym: GymSync) {
    val game: GameViewModel = viewModel(factory = viewModelFactory { initializer { GameViewModel(repository) } })
    val state by game.state.collectAsStateWithLifecycle()
    val allQuests by game.allQuests.collectAsStateWithLifecycle()

    // Ask for notification permission once a quest actually has a reminder (Android 13+).
    val hasReminders = allQuests.any { it.reminder != null }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(hasReminders) {
        if (hasReminders &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var editing by remember { mutableStateOf<Editing?>(null) }
    var logging by remember { mutableStateOf<Pair<Quest, Double?>?>(null) }
    var levelUp by remember { mutableStateOf<List<StatChange>?>(null) }
    var recordSheet by remember { mutableStateOf<RecordKind?>(null) }
    var newBest by remember { mutableStateOf<Pair<RecordKind, PersonalBest>?>(null) }
    val recordsVm: RecordsViewModel = viewModel(
        factory = viewModelFactory { initializer { RecordsViewModel(records, repository::today) } },
    )
    val recordsState by recordsVm.state.collectAsStateWithLifecycle()
    val skillsVm: SkillsViewModel = viewModel(factory = viewModelFactory { initializer { SkillsViewModel(skills) } })
    val skillsState by skillsVm.state.collectAsStateWithLifecycle()
    var masteryUp by remember { mutableStateOf<SkillResult?>(null) }
    val mindVm: MindViewModel = viewModel(factory = viewModelFactory { initializer { MindViewModel(mind) } })
    val mindState by mindVm.state.collectAsStateWithLifecycle()

    val goalsVm: GoalsViewModel = viewModel(factory = viewModelFactory { initializer { GoalsViewModel(goals, planner, ai) } })
    val goalsState by goalsVm.state.collectAsStateWithLifecycle()
    var addingGoal by remember { mutableStateOf(false) }
    var importingPlan by remember { mutableStateOf(false) }
    var aiPlanning by remember { mutableStateOf(false) }
    val hasAiKey by goalsVm.hasKey.collectAsStateWithLifecycle()

    LaunchedEffect(goalsVm) {
        goalsVm.events.collect { event ->
            when (event) {
                is GoalsEvent.Added -> launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val text = if (event.queued) "${event.title} is queued. It starts when a goal ends." else "${event.title} goal started"
                    snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
                }
                is GoalsEvent.Imported -> launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val started = if (event.active == 1) "1 goal started" else "${event.active} goals started"
                    val text = if (event.queued > 0) "$started, ${event.queued} queued" else started
                    snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
                }
            }
        }
    }

    LaunchedEffect(mindVm) {
        mindVm.events.collect { event ->
            when (event) {
                is MindEvent.LeveledUp -> levelUp = event.changes
                is MindEvent.Result -> if (event.result.xp > 0) {
                    launch { snackbar.showSnackbar("+${event.result.xp} XP · ${MindRepository.STAT.displayName}", duration = SnackbarDuration.Short) }
                }
            }
        }
    }

    LaunchedEffect(skillsVm) {
        skillsVm.events.collect { event ->
            when (event) {
                is SkillsEvent.LeveledUp -> levelUp = event.changes
                is SkillsEvent.Result -> {
                    val result = event.result
                    if (result.masteryUp && result.after.level >= Mastery.SOLID.level) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        masteryUp = result
                    }
                    if (result.xp > 0) {
                        val stat = Packs.packOf(result.conceptId)?.stat
                        launch { snackbar.showSnackbar("+${result.xp} XP" + (stat?.let { " · ${it.displayName}" } ?: ""), duration = SnackbarDuration.Short) }
                    }
                }
            }
        }
    }

    val gymStatus by gym.status.collectAsStateWithLifecycle()
    val askGym = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = (gym.status.value as? GymStatus.NeedsPermission)?.permission
        when {
            granted -> scope.launch(Dispatchers.IO) { gym.sync() }
            // Denied with "don't ask again": Android won't show the prompt, so only Settings can grant it.
            permission != null && (context as? Activity)?.shouldShowRequestPermissionRationale(permission) == false -> scope.launch {
                val result = snackbar.showSnackbar("Allow Gym access in Settings to connect", actionLabel = "Settings", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            }
        }
    }
    val onConnectGym: () -> Unit = {
        when (val status = gymStatus) {
            is GymStatus.NeedsPermission -> askGym.launch(status.permission)
            GymStatus.NeedsUpdate -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GymBridge.DOWNLOAD_URL)))
            else -> Unit
        }
    }

    LaunchedEffect(gym) {
        gym.results.collect { result ->
            val leveled = result.changes.filter { it.leveledUp }
            if (leveled.isNotEmpty()) levelUp = leveled
            val best = result.personalBests.lastOrNull()
            if (best != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                newBest = best
            }
            launch {
                val count = result.added + result.updated
                val text = when {
                    best != null -> "New ${best.first.displayName} best from Gym · +${RecordMath.PR_XP} XP ${best.first.stat.displayName}"
                    count == 1 -> "1 entry in from Gym"
                    count > 1 -> "$count entries in from Gym"
                    else -> null
                }
                if (text != null) snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }
    }

    LaunchedEffect(recordsVm) {
        recordsVm.events.collect { event ->
            when (event) {
                is RecordsEvent.LeveledUp -> levelUp = event.changes
                is RecordsEvent.Logged -> {
                    val best = event.personalBests.firstOrNull()
                    if (best != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        newBest = event.kind to best
                    }
                    launch {
                        val text = if (best != null) {
                            "New ${event.kind.displayName} best · +${RecordMath.PR_XP} XP ${event.kind.stat.displayName}"
                        } else {
                            "Logged ${event.kind.displayName}"
                        }
                        val result = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) recordsVm.delete(event.entryId)
                    }
                }
            }
        }
    }

    LaunchedEffect(game) {
        game.events.collect { event ->
            when (event) {
                is GameEvent.LeveledUp -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    levelUp = event.changes
                }
                is GameEvent.DayClosed -> launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    val count = event.report.missed.size
                    val quests = if (count == 1) "1 daily quest" else "$count daily quests"
                    snackbar.showSnackbar(
                        "Penalty: missed $quests yesterday, −${event.report.xpLost} XP. Penalty quests added.",
                        duration = SnackbarDuration.Long,
                    )
                }
                is GameEvent.Completed -> launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val xp = event.changes.joinToString(" · ") { "+${it.delta} XP ${it.stat.displayName}" }
                    val result = snackbar.showSnackbar(xp, actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) game.undo(event.quest.id)
                }
            }
        }
    }

    val complete: (Quest) -> Unit = { quest ->
        if (quest.input is CompletionInput.Number) {
            scope.launch { logging = quest to game.lastLoggedValue(quest.id) }
        } else {
            game.complete(quest)
        }
    }
    val edit: (Quest) -> Unit = { editing = Editing.Existing(it) }

    val layers = remember { BlurLayers(backdrop = HazeState(), chrome = HazeState()) }
    val onTab = route == Routes.HOME || route == Routes.QUESTS || route == Routes.RECORDS || route == Routes.SKILLS || route == Routes.LOG

    CompositionLocalProvider(LocalBlurLayers provides layers) {
        Box(Modifier.fillMaxSize()) {
            // The ambient light feeds both layers; the content feeds only the chrome layer, above it.
            SystemBackground(
                Modifier
                    .fillMaxSize()
                    .hazeSource(layers.backdrop)
                    .hazeSource(layers.chrome, zIndex = 0f),
            )
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(layers.chrome, zIndex = 1f),
                containerColor = Color.Transparent,
            ) { scaffoldPadding ->
                // Screens scroll under the floating bar, so they pad their last row clear of it.
                val padding = PaddingValues(
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding() + SystemTabBarHeight + 24.dp,
                )
                NavHost(
                    nav,
                    startDestination = Routes.HOME,
                    enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.985f) },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            state = state,
                            contentPadding = padding,
                            onComplete = complete,
                            onEdit = edit,
                            onStatClick = { nav.navigate(Routes.stat(it)) },
                            extra = {
                                planSection(
                                    goalsState,
                                    onTask = { item ->
                                        when (val action = item.task.action) {
                                            is PlanAction.LogRecord -> recordSheet = action.kind
                                            is PlanAction.Study -> Packs.pack(action.packId)?.let { pack ->
                                                val session = skillsState.session(pack)
                                                if (session.isEmpty) {
                                                    nav.navigate(Routes.pack(pack.id))
                                                } else {
                                                    val learn = session.fresh.filter { skillsState.state(it).mastery == Mastery.UNSEEN }
                                                    nav.navigate(Routes.quiz(session.all.map { it.id }, learn.map { it.id }))
                                                }
                                            }
                                            PlanAction.MindSession -> {
                                                val game = (goalsState.active.firstOrNull { it.goal.id == item.task.goalId }?.goal?.measure as? GoalMeasure.Mind)?.game
                                                val games = when {
                                                    game == null -> mindState.sessionGames
                                                    item.task.isTest -> listOf(game)
                                                    game in mindState.sessionGames -> mindState.sessionGames
                                                    else -> mindState.sessionGames.dropLast(1) + game
                                                }
                                                nav.navigate(Routes.mind(games))
                                            }
                                        }
                                    },
                                    onGoals = { nav.navigate(Routes.GOALS) },
                                )
                            },
                        )
                    }
                    composable(Routes.QUESTS) {
                        QuestsScreen(state, allQuests, padding, onComplete = complete, onEdit = edit)
                    }
                    composable(Routes.RECORDS) {
                        RecordsScreen(recordsState, padding, gymStatus, onConnectGym = onConnectGym, onOpen = { recordSheet = it })
                    }
                    composable(Routes.SKILLS) {
                        SkillsScreen(
                            skillsState,
                            padding,
                            onStart = { pack ->
                                val session = skillsState.session(pack)
                                val learn = session.fresh.filter { skillsState.state(it).mastery == Mastery.UNSEEN }
                                nav.navigate(Routes.quiz(session.all.map { it.id }, learn.map { it.id }))
                            },
                            onOpenPack = { nav.navigate(Routes.pack(it.id)) },
                            extra = {
                                mindSection(
                                    mindState,
                                    onSession = { nav.navigate(Routes.mind(mindState.sessionGames)) },
                                    onGame = { nav.navigate(Routes.mind(listOf(it))) },
                                )
                            },
                        )
                    }
                    composable(Routes.GOALS) {
                        GoalsScreen(
                            overview = goalsState,
                            onAdd = { addingGoal = true },
                            onImport = { importingPlan = true },
                            onAiPlan = { aiPlanning = true },
                            onMoveDeadline = { view, date -> goalsVm.moveDeadline(view.goal.id, date) },
                            onChangeTarget = { view, target -> goalsVm.changeTarget(view.goal.id, target) },
                            onReached = { goalsVm.markReached(it.goal.id) },
                            onDrop = { goalsVm.drop(it.goal.id) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.MIND, arguments = listOf(navArgument("games") { type = NavType.StringType })) { entry ->
                        val games = remember(entry) {
                            requireNotNull(entry.arguments?.getString("games")).split(',').map(MindGame::fromKey)
                        }
                        MindSessionScreen(
                            games = games,
                            state = mindState,
                            onScored = mindVm::record,
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.PACK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        val pack = Packs.pack(requireNotNull(entry.arguments?.getString("id")))
                        if (pack != null) {
                            PackScreen(pack, skillsState, onConcept = { nav.navigate(Routes.concept(it.id)) }, onBack = { nav.popBackStack() })
                        }
                    }
                    composable(Routes.CONCEPT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        val id = requireNotNull(entry.arguments?.getString("id"))
                        val concept = Packs.concept(id)
                        val pack = Packs.packOf(id)
                        if (concept != null && pack != null) {
                            ConceptScreen(
                                concept = concept,
                                pack = pack,
                                state = skillsState,
                                onQuiz = {
                                    val learn = if (skillsState.state(concept).mastery == Mastery.UNSEEN) listOf(id) else emptyList()
                                    nav.navigate(Routes.quiz(listOf(id), learn))
                                },
                                onRead = { skillsVm.read(id) },
                                onApplied = { skillsVm.applied(id) },
                                onTaught = { skillsVm.taught(id) },
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                    composable(
                        Routes.QUIZ,
                        arguments = listOf(
                            navArgument("ids") { type = NavType.StringType },
                            navArgument("learn") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val ids = requireNotNull(entry.arguments?.getString("ids")).split(',')
                        val learn = requireNotNull(entry.arguments?.getString("learn")).split(',').filter { it != "-" }.toSet()
                        val concepts = remember(ids) { ids.mapNotNull { Packs.concept(it) } }
                        val accent = Packs.packOf(ids.first())?.stat?.color ?: Color.White
                        QuizScreen(
                            concepts = concepts,
                            learnFirst = learn,
                            accent = accent,
                            onRead = { skillsVm.read(it.id) },
                            onScored = { concept, correct, total -> skillsVm.quiz(concept.id, correct, total) },
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.LOG) {
                        val log: LogViewModel = viewModel(factory = viewModelFactory { initializer { LogViewModel(health) } })
                        val logState by log.state.collectAsStateWithLifecycle()
                        LaunchedEffect(log) {
                            log.events.collect { event ->
                                launch {
                                    val text = when (event) {
                                        is LogEvent.MealLogged -> "Logged ${event.food}"
                                        is LogEvent.GasLogged -> "Logged gas ${event.level}"
                                    }
                                    val result = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short)
                                    if (result == SnackbarResult.ActionPerformed) log.undo(event)
                                }
                            }
                        }
                        LogScreen(
                            state = logState,
                            boundary = health.dayBoundary,
                            contentPadding = padding,
                            onMeal = log::logMeal,
                            onGas = log::logGas,
                            onShare = {
                                scope.launch {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Food and gas log")
                                        putExtra(Intent.EXTRA_TEXT, log.exportCsv())
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share log"))
                                }
                            },
                        )
                    }
                    composable(Routes.STAT, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                        val stat = Stat.fromKey(requireNotNull(entry.arguments?.getString("key")))
                        val vm: StatDetailViewModel = viewModel(
                            key = stat.key,
                            factory = viewModelFactory { initializer { StatDetailViewModel(repository, stat) } },
                        )
                        StatDetailScreen(vm, onBack = { nav.popBackStack() })
                    }
                }
            }

            // Floating chrome: snackbar, tab bar and the screen's add button, over the content layer.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SnackbarHost(snackbar) { data -> SystemSnackbar(data) }
                AnimatedVisibility(
                    visible = onTab,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SystemTabBar(
                            tabs = Tabs,
                            selected = route,
                            onSelect = { nav.switchTab(it) },
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedVisibility(
                            visible = route == Routes.QUESTS,
                            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f)),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SystemIconButton(Icons.Filled.Add, contentDescription = "Add quest", onClick = { editing = Editing.New })
                        }
                    }
                }
            }

            newBest?.let { (kind, best) ->
                val headline = RecordFormat.metric(best.metric, best.value)
                CelebrationOverlay(
                    label = "New record",
                    headline = headline.substringBefore(' '),
                    message = "${kind.displayName} · ${best.metric.displayName.lowercase()} ${headline.substringAfter(' ', "")}\n" +
                        "was ${RecordFormat.metric(best.metric, best.previous)}",
                    accent = SystemColors.Gold,
                    key = best,
                    onDismiss = { newBest = null },
                )
            }
            masteryUp?.let { result ->
                val concept = Packs.concept(result.conceptId)
                CelebrationOverlay(
                    label = "Mastery up",
                    headline = result.after.displayName,
                    message = "${concept?.title.orEmpty()}\n${result.after.howToEarn}: done",
                    accent = Packs.packOf(result.conceptId)?.stat?.color ?: Color.White,
                    key = result,
                    onDismiss = { masteryUp = null },
                )
            }
            levelUp?.let { changes -> LevelUpOverlay(changes, onDismiss = { levelUp = null }) }
        }
    }

    editing?.let { current ->
        QuestEditorSheet(
            initial = (current as? Editing.Existing)?.quest,
            onSave = {
                game.save(it)
                editing = null
            },
            onDelete = {
                game.delete(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (addingGoal) {
        AddGoalSheet(
            best = { goalsVm.best(it.measure) },
            queued = !GoalRules.canAdd(goalsState.active.size),
            today = goalsVm.today(),
            onAdd = { template, target, deadline ->
                goalsVm.add(template, target, deadline)
                addingGoal = false
            },
            onDismiss = { addingGoal = false },
        )
    }

    if (aiPlanning) {
        AiPlanSheet(
            hasKey = hasAiKey,
            activeCount = goalsState.active.size,
            today = goalsVm.today(),
            onSaveKey = goalsVm::saveKey,
            onForgetKey = goalsVm::forgetKey,
            plan = goalsVm::aiPlan,
            onImport = {
                goalsVm.import(it)
                aiPlanning = false
            },
            onDismiss = { aiPlanning = false },
        )
    }

    if (importingPlan) {
        ImportPlanSheet(
            activeCount = goalsState.active.size,
            today = goalsVm.today(),
            onImport = {
                goalsVm.import(it)
                importingPlan = false
            },
            onDismiss = { importingPlan = false },
        )
    }

    recordSheet?.let { kind ->
        RecordLogSheet(
            kind = kind,
            summary = recordsState.summaries.firstOrNull { it.kind == kind },
            onSave = { weight, reps, seconds, distance ->
                recordsVm.log(kind, weight, reps, seconds, distance)
                recordSheet = null
            },
            onDelete = recordsVm::delete,
            onDismiss = { recordSheet = null },
        )
    }

    logging?.let { (quest, last) ->
        NumberEntryDialog(
            title = quest.title,
            unit = (quest.input as CompletionInput.Number).unit,
            initial = last,
            onConfirm = { value ->
                game.complete(quest, value)
                logging = null
            },
            onDismiss = { logging = null },
        )
    }
}

private fun androidx.navigation.NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
