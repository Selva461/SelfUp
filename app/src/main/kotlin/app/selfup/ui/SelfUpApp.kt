package app.selfup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import app.selfup.core.Quest
import app.selfup.core.Stat
import app.selfup.core.StatChange
import app.selfup.data.LifeRepository
import app.selfup.ui.components.LevelUpOverlay
import app.selfup.ui.components.NumberEntryDialog
import app.selfup.ui.home.HomeScreen
import app.selfup.ui.quests.QuestEditorSheet
import app.selfup.ui.quests.QuestsScreen
import app.selfup.ui.stat.StatDetailScreen
import app.selfup.ui.stat.StatDetailViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val QUESTS = "quests"
    const val STAT = "stat/{key}"
    fun stat(stat: Stat) = "stat/${stat.key}"
}

/** Which quest the editor is open for: none, a new one, or an existing one. */
private sealed interface Editing {
    data object New : Editing
    data class Existing(val quest: Quest) : Editing
}

@Composable
fun SelfUpApp(repository: LifeRepository) {
    val game: GameViewModel = viewModel(factory = viewModelFactory { initializer { GameViewModel(repository) } })
    val state by game.state.collectAsStateWithLifecycle()
    val allQuests by game.allQuests.collectAsStateWithLifecycle()

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Editing?>(null) }
    var logging by remember { mutableStateOf<Pair<Quest, Double?>?>(null) }
    var levelUp by remember { mutableStateOf<List<StatChange>?>(null) }

    LaunchedEffect(game) {
        game.events.collect { event ->
            when (event) {
                is GameEvent.LeveledUp -> levelUp = event.changes
                is GameEvent.DayClosed -> launch {
                    val count = event.report.missed.size
                    val quests = if (count == 1) "1 daily quest" else "$count daily quests"
                    snackbar.showSnackbar(
                        "Missed $quests yesterday: −${event.report.xpLost} XP. Make-up quests added.",
                        duration = SnackbarDuration.Long,
                    )
                }
                is GameEvent.Completed -> launch {
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (route == Routes.HOME || route == Routes.QUESTS) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        NavigationBarItem(
                            selected = route == Routes.HOME,
                            onClick = { nav.switchTab(Routes.HOME) },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = route == Routes.QUESTS,
                            onClick = { nav.switchTab(Routes.QUESTS) },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            label = { Text("Quests") },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (route == Routes.QUESTS) {
                    FloatingActionButton(onClick = { editing = Editing.New }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add quest")
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Routes.HOME) {
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

        levelUp?.let { changes -> LevelUpOverlay(changes, onDismiss = { levelUp = null }) }
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
