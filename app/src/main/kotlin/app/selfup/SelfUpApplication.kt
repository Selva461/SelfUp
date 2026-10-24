package app.selfup

import android.app.Application
import app.selfup.ai.AiSettings
import app.selfup.ai.GroqPlanner
import app.selfup.data.Backup
import app.selfup.data.GoalsRepository
import app.selfup.data.HealthRepository
import app.selfup.data.HunterRepository
import app.selfup.data.LifeRepository
import app.selfup.data.MindRepository
import app.selfup.data.RecordsRepository
import app.selfup.data.SkillsRepository
import app.selfup.data.WorkRepository
import app.selfup.data.ReminderInput
import app.selfup.data.SelfUpDatabase
import app.selfup.gym.GymBridge
import app.selfup.gym.GymSync
import app.selfup.gym.GymSyncWorker
import app.selfup.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.selfup.focus.BlockerService
import app.selfup.focus.BlockerWatchdog
import app.selfup.focus.FocusRepository
import app.selfup.focus.UsageReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SelfUpApplication : Application() {
    /** App-wide scope for work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob())

    private val database by lazy { SelfUpDatabase.create(this) }

    val repository: LifeRepository by lazy { LifeRepository(database) }

    val health: HealthRepository by lazy { HealthRepository(database, repository.clock, repository.dayBoundary) }

    val records: RecordsRepository by lazy { RecordsRepository(database, repository.clock, repository.dayBoundary) }

    val skills: SkillsRepository by lazy { SkillsRepository(database, repository.clock, repository.dayBoundary) }

    val mind: MindRepository by lazy { MindRepository(database, repository.clock, repository.dayBoundary) }

    val goals: GoalsRepository by lazy { GoalsRepository(database, repository.clock, repository.dayBoundary) }

    val focus: FocusRepository by lazy {
        FocusRepository(UsageReader(this), repository, database.metaDao(), database.trackingDao(), blockerEnabled = { BlockerService.isEnabled(this) }, blockerUnrestricted = { BlockerService.isUnrestricted(this) })
    }

    val hunter: HunterRepository by lazy { HunterRepository(database, repository) }

    val work: WorkRepository by lazy { WorkRepository(database, repository.clock, repository.dayBoundary) }

    val backup: Backup by lazy { Backup(this, database) }

    val ai: AiSettings by lazy { AiSettings(this) }

    val planner: GroqPlanner by lazy { GroqPlanner(ai) }

    val gym: GymSync by lazy {
        GymSync(
            GymBridge(this),
            records,
            database.metaDao(),
            repository.clock,
            zone = { repository.dayBoundary.zone },
            life = repository,
            tracking = database.trackingDao(),
        )
    }

    private val reminders by lazy { ReminderScheduler(this) }

    /** Pulls from Gym now, off the main thread. Called whenever SelfUp comes to the foreground. */
    fun syncGym() {
        appScope.launch(Dispatchers.IO) { gym.sync() }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedDefaultsOnce() }
        // Any change to quests or today's completions re-arms reminders, so no call site
        // (editor, completion, undo, notification action) has to remember to.
        appScope.launch { repository.observeReminderInputs().collect(::syncReminders) }
        GymSyncWorker.schedule(this)
        BlockerWatchdog.schedule(this)
    }

    /** One-off re-arm for receivers, which may run before the collector above emits. */
    suspend fun syncRemindersNow() = syncReminders(repository.observeReminderInputs().first())

    private fun syncReminders(inputs: List<ReminderInput>) =
        reminders.sync(inputs, repository.dayBoundary, repository.clock.instant())
}
