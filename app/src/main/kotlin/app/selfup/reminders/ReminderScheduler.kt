package app.selfup.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.selfup.core.DayBoundary
import app.selfup.core.ReminderPlanner
import app.selfup.data.ReminderInput
import java.time.Instant

/**
 * Keeps exactly one alarm armed per quest reminder, ported from Notify's AlarmScheduler.
 *
 * - Inexact `setAndAllowWhileIdle`: it still wakes the device in Doze and may drift a few
 *   minutes, which is fine for a habit nudge and needs no exact-alarm permission.
 * - [PendingIntent.FLAG_IMMUTABLE], and a per-quest data URI and request code, so
 *   re-arming replaces a quest's alarm instead of stacking duplicates.
 * - The set of armed quest ids is persisted so alarms of deleted quests get cancelled.
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /** Re-arms every reminder from [inputs] and cancels any armed quest not in it. */
    fun sync(inputs: List<ReminderInput>, boundary: DayBoundary, now: Instant) {
        val armed = mutableSetOf<Long>()
        inputs.forEach { input ->
            val fireAt = ReminderPlanner.nextFire(input.quest, now, boundary, input.doneToday, input.finished)
            if (fireAt == null) {
                cancel(input.quest.id)
            } else {
                arm(input.quest.id, fireAt)
                armed += input.quest.id
            }
        }
        val previously = prefs.getStringSet(KEY_ARMED, emptySet()).orEmpty().mapNotNull(String::toLongOrNull)
        (previously - armed).forEach(::cancel)
        prefs.edit().putStringSet(KEY_ARMED, armed.map(Long::toString).toSet()).apply()
    }

    private fun arm(questId: Long, fireAt: Instant) {
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pendingIntent(questId))
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not arm reminder for quest $questId", e)
        }
    }

    private fun cancel(questId: Long) {
        val existing = PendingIntent.getBroadcast(
            context,
            questId.toInt(),
            fireIntent(questId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(questId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        questId.toInt(),
        fireIntent(questId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun fireIntent(questId: Long) = Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_FIRE
        putExtra(ReminderReceiver.EXTRA_QUEST_ID, questId)
        // Extras don't make PendingIntents distinct; the data URI does.
        data = Uri.parse("selfup://reminder/$questId")
    }

    private companion object {
        const val TAG = "ReminderScheduler"
        const val KEY_ARMED = "armed_quest_ids"
    }
}
