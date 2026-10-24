package app.selfup.reminders

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import app.selfup.SelfUpApplication
import app.selfup.alert.Alerts
import app.selfup.data.FocusEventKind
import app.selfup.focus.BlockerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Turns a fired alarm into a System order or nag, and handles the fallback notification's Done action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val questId = intent.getLongExtra(EXTRA_QUEST_ID, -1L)
        if (questId == -1L && intent.action != ACTION_NAG) return
        val app = context.applicationContext as SelfUpApplication

        // onReceive must not block: goAsync keeps the process alive until the work is done.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(app, questId)
                    ACTION_NAG -> nag(app)
                    ACTION_DONE -> {
                        app.repository.complete(questId)
                        QuestNotifications.cancel(app, questId)
                    }
                }
                // Arm the next occurrence; also covers the just-completed case.
                app.syncRemindersNow()
            } catch (e: Exception) {
                Log.e(TAG, "Failed handling ${intent.action} for quest $questId", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The reminder takes over the screen as a System order: a window over every app that a
     * swipe can't clear. Queued while the phone is locked, it is waiting on unlock. Only
     * without "Display over other apps" does it fall back to a notification and its popup.
     */
    private suspend fun fire(app: SelfUpApplication, questId: Long) {
        val repository = app.repository
        val quest = repository.getQuest(questId) ?: return
        // The quest may have been done, rescheduled or expired since the alarm was armed.
        if (repository.isDoneToday(questId) || !quest.schedule.isDueOn(repository.today())) return
        val check = repository.checkOf(quest)
        val dodges = app.focus.countToday(FocusEventKind.DODGED, questId)
        if (app.alerts.post(Alerts.order(app, quest, check, dodges))) {
            app.focus.record(FocusEventKind.ORDERED, questId)
            return
        }
        if (!QuestNotifications.show(app, quest, checked = check != null)) Log.w(TAG, "Notifications are blocked")
        // With the screen on and unlocked, pop the System window over whatever is open. When
        // it's off or locked, the notification's full-screen intent shows the same popup.
        if (isInUse(app)) {
            runCatching { app.startActivity(PopupActivity.quest(app, questId)) }
                .onFailure { Log.w(TAG, "Popup blocked", it) }
        }
    }

    /** Comes back about every quest still open, harder each time today (see [app.selfup.core.Enforcer]). */
    private suspend fun nag(app: SelfUpApplication) {
        val open = app.repository.openToday()
        if (open.isEmpty()) return
        val level = app.focus.countToday(FocusEventKind.NAGGED)
        if (app.alerts.post(Alerts.nag(app, open.map { it.quest }, level, app.repository.failRun()))) {
            app.focus.record(FocusEventKind.NAGGED)
        }
    }

    private fun isInUse(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return power?.isInteractive == true && keyguard?.isKeyguardLocked != true
    }

    companion object {
        const val ACTION_FIRE = "app.selfup.action.REMINDER_FIRE"
        const val ACTION_DONE = "app.selfup.action.REMINDER_DONE"
        const val ACTION_NAG = "app.selfup.action.NAG"
        const val EXTRA_QUEST_ID = "app.selfup.extra.QUEST_ID"
        private const val TAG = "ReminderReceiver"
    }
}

/**
 * Alarms don't survive a reboot, an app update or a clock/timezone change, so every
 * reminder is re-armed after each of them. The actions are protected broadcasts only the
 * OS can send, which is why exporting this receiver is safe.
 */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val app = context.applicationContext as SelfUpApplication
        // Boot and app updates are moments Android allows a foreground service to start.
        BlockerService.start(app)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.syncRemindersNow()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
