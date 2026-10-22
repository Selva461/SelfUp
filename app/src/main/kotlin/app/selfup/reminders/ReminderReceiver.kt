package app.selfup.reminders

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import app.selfup.SelfUpApplication
import app.selfup.focus.BlockerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Turns a fired alarm into a notification, and handles the notification's Done action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val questId = intent.getLongExtra(EXTRA_QUEST_ID, -1L)
        if (questId == -1L) return
        val app = context.applicationContext as SelfUpApplication

        // onReceive must not block: goAsync keeps the process alive until the work is done.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(app, questId)
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

    private suspend fun fire(app: SelfUpApplication, questId: Long) {
        val repository = app.repository
        val quest = repository.getQuest(questId) ?: return
        // The quest may have been done, rescheduled or expired since the alarm was armed.
        if (repository.isDoneToday(questId) || !quest.schedule.isDueOn(repository.today())) return
        if (!QuestNotifications.show(app, quest)) Log.w(TAG, "Notifications are blocked")
        // With the screen on and unlocked, pop the System window over whatever is open. When
        // it's off or locked, the notification's full-screen intent shows the same popup.
        if (PopupActivity.canPopUp(app) && isInUse(app)) {
            runCatching { app.startActivity(PopupActivity.quest(app, questId)) }
                .onFailure { Log.w(TAG, "Popup blocked", it) }
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
