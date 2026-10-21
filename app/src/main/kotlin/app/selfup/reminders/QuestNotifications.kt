package app.selfup.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.selfup.MainActivity
import app.selfup.R
import app.selfup.core.CompletionInput
import app.selfup.core.Quest
import app.selfup.ui.components.questSubtitle

/** Posts quest reminders. One notification per quest, replaced rather than stacked. */
object QuestNotifications {
    private const val CHANNEL_ID = "selfup.reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_reminders_description) },
        )
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Returns false when the OS isn't letting the app post. */
    fun show(context: Context, quest: Quest): Boolean {
        ensureChannel(context)
        if (!canPost(context)) return false

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(quest.title)
            .setContentText(questSubtitle(quest))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, quest.id))
        // Logging quests need a number, so they open the app instead of completing in place.
        if (quest.input !is CompletionInput.Number) {
            builder.addAction(0, context.getString(R.string.action_done), doneAction(context, quest.id))
        }

        return try {
            NotificationManagerCompat.from(context).notify(quest.id.toInt(), builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, questId: Long) {
        NotificationManagerCompat.from(context).cancel(questId.toInt())
    }

    private fun openApp(context: Context, questId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        questId.toInt(),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("selfup://open/$questId")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun doneAction(context: Context, questId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        questId.toInt(),
        Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DONE
            putExtra(ReminderReceiver.EXTRA_QUEST_ID, questId)
            data = Uri.parse("selfup://done/$questId")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
