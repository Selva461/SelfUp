package app.selfup

import android.app.Application
import app.selfup.ai.AiSettings
import app.selfup.ai.GroqPlanner
import app.selfup.data.GoalsRepository
import app.selfup.data.HealthRepository
import app.selfup.data.LifeRepository
import app.selfup.data.MindRepository
import app.selfup.data.RecordsRepository
import app.selfup.data.SkillsRepository
import app.selfup.data.ReminderInput
import app.selfup.data.SelfUpDatabase
import app.selfup.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    val ai: AiSettings by lazy { AiSettings(this) }

    val planner: GroqPlanner by lazy { GroqPlanner(ai) }

    private val reminders by lazy { ReminderScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedDefaultsOnce() }
        // Any change to quests or today's completions re-arms reminders, so no call site
        // (editor, completion, undo, notification action) has to remember to.
        appScope.launch { repository.observeReminderInputs().collect(::syncReminders) }
    }

    /** One-off re-arm for receivers, which may run before the collector above emits. */
    suspend fun syncRemindersNow() = syncReminders(repository.observeReminderInputs().first())

    private fun syncReminders(inputs: List<ReminderInput>) =
        reminders.sync(inputs, repository.dayBoundary, repository.clock.instant())
}
