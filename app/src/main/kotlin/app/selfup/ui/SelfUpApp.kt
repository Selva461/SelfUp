package app.selfup.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import app.selfup.core.PersonalBest
import app.selfup.core.Quest
import app.selfup.core.RecordKind
import app.selfup.core.RecordMath
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.data.HealthRepository
import app.selfup.data.LifeRepository
import app.selfup.data.RecordsRepository
import app.selfup.ui.components.CelebrationOverlay
import app.selfup.ui.components.LevelUpOverlay
import app.selfup.ui.components.NumberEntryDialog
import app.selfup.ui.glass.AmbientBackground
import app.selfup.ui.glass.GlassIconButton
import app.selfup.ui.glass.GlassLayers
import app.selfup.ui.glass.GlassSnackbar
import app.selfup.ui.glass.GlassTab
import app.selfup.ui.glass.GlassTabBar
import app.selfup.ui.glass.GlassTabBarHeight
import app.selfup.ui.glass.LocalGlassLayers
import app.selfup.ui.home.HomeScreen
import app.selfup.ui.log.LogEvent
import app.selfup.ui.log.LogScreen
import app.selfup.ui.log.LogViewModel
import app.selfup.ui.quests.QuestEditorSheet
import app.selfup.ui.quests.QuestsScreen
import app.selfup.ui.records.RecordFormat
import app.selfup.ui.records.RecordLogSheet
import app.selfup.ui.records.RecordsEvent
import app.selfup.ui.records.RecordsScreen
import app.selfup.ui.records.RecordsViewModel
import app.selfup.ui.theme.color
import app.selfup.ui.stat.StatDetailScreen
import app.selfup.ui.stat.StatDetailViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private val Tabs = listOf(
    GlassTab(Routes.HOME, "Today", Icons.Filled.Home),
    GlassTab(Routes.QUESTS, "Quests", Icons.AutoMirrored.Filled.List),
    GlassTab(Routes.RECORDS, "Records", Icons.Filled.Star),
    GlassTab(Routes.LOG, "Log", Icons.Filled.Edit),
)

private object Routes {
    const val HOME = "home"
    const val QUESTS = "quests"
    const val LOG = "log"
    const val RECORDS = "records"
    const val STAT = "stat/{key}"
    fun stat(stat: Stat) = "stat/${stat.key}"
}

/** Which quest the editor is open for: none, a new one, or an existing one. */
private sealed interface Editing {
    data object New : Editing
    data class Existing(val quest: Quest) : Editing
}

@Composable
fun SelfUpApp(repository: LifeRepository, health: HealthRepository, records: RecordsRepository) {
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
                            "New ${event.kind.displayName} best  +${RecordMath.PR_XP} ${event.kind.stat.displayName}"
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
                        "Missed $quests yesterday: −${event.report.xpLost} XP. Make-up quests added.",
                        duration = SnackbarDuration.Long,
                    )
                }
                is GameEvent.Completed -> launch {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val xp = event.changes.joinToString("  ") { "+${it.delta} ${it.stat.displayName}" }
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

    val layers = remember { GlassLayers(backdrop = HazeState(), chrome = HazeState()) }
    val onTab = route == Routes.HOME || route == Routes.QUESTS || route == Routes.RECORDS || route == Routes.LOG

    CompositionLocalProvider(LocalGlassLayers provides layers) {
        Box(Modifier.fillMaxSize()) {
            // The ambient light feeds both layers; the content feeds only the chrome layer, above it.
            AmbientBackground(
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
                    bottom = scaffoldPadding.calculateBottomPadding() + GlassTabBarHeight + 24.dp,
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
                        )
                    }
                    composable(Routes.QUESTS) {
                        QuestsScreen(state, allQuests, padding, onComplete = complete, onEdit = edit)
                    }
                    composable(Routes.RECORDS) {
                        RecordsScreen(recordsState, padding, onOpen = { recordSheet = it })
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
                SnackbarHost(snackbar) { data -> GlassSnackbar(data) }
                AnimatedVisibility(
                    visible = onTab,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassTabBar(
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
                            GlassIconButton(Icons.Filled.Add, contentDescription = "Add quest", onClick = { editing = Editing.New })
                        }
                    }
                }
            }

            newBest?.let { (kind, best) ->
                val headline = RecordFormat.metric(best.metric, best.value)
                CelebrationOverlay(
                    label = "NEW RECORD",
                    headline = headline.substringBefore(' '),
                    message = "${kind.displayName} · ${best.metric.displayName.lowercase()} ${headline.substringAfter(' ', "")}\n" +
                        "was ${RecordFormat.metric(best.metric, best.previous)}",
                    accent = kind.stat.color,
                    key = best,
                    onDismiss = { newBest = null },
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
