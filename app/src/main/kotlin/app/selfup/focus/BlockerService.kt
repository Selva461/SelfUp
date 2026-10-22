package app.selfup.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.selfup.MainActivity
import app.selfup.R
import app.selfup.SelfUpApplication
import app.selfup.core.UsageKind
import app.selfup.reminders.PopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * Locks entertainment apps once the day's budget is spent. It reads which app came to the
 * front from Android's usage log (the same read-only log screen time uses, never what is on
 * screen) and shows [BlockActivity] over a locked one. While a watched app stays open it
 * re-checks every [RECHECK_MS], so a long reels session is cut off when the budget runs
 * out, not only on the next app switch.
 *
 * This is a foreground service rather than an accessibility service on purpose: Play
 * Protect blocks sideloaded installs of any app that declares an accessibility service.
 * Opening a screen over other apps from here relies on "Display over other apps".
 */
class BlockerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usage by lazy { UsageReader(this) }
    private var poll: Job? = null
    private var watch: Job? = null
    private var foreground: String? = null

    private val focus get() = (application as SelfUpApplication).focus

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started with startForegroundService must go foreground even if it then stops.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        if (!isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (poll == null) poll = scope.launch { pollFront() }
        return START_STICKY
    }

    /** Follows the usage log for the app in front, about once a second while the screen is on. */
    private suspend fun pollFront() {
        val power = getSystemService(PowerManager::class.java)
        // Looking back a little on start picks up the app that is already open.
        var seen = Instant.now().minus(LOOKBACK)
        while (scope.isActive) {
            if (power?.isInteractive == false) {
                delay(IDLE_MS)
                continue
            }
            val now = Instant.now()
            // Events can land in the log a moment after their timestamp, so the window starts
            // at the last event handled rather than at the last poll.
            val from = maxOf(seen, now.minus(LOOKBACK))
            val fresh = withContext(Dispatchers.IO) { runCatching { usage.events(from, now) }.getOrDefault(emptyList()) }
                .filter { it.at.isAfter(seen) }
            fresh.maxOfOrNull { it.at }?.let { seen = it }
            fresh.lastOrNull { it.kind == UsageKind.RESUMED }?.let { onFront(it.pkg) }
            delay(POLL_MS)
        }
    }

    private fun onFront(pkg: String) {
        // System UI (notification shade, keyboard) passes over an app without leaving it.
        if (pkg == foreground || pkg in passThrough) return
        foreground = pkg
        watch?.cancel()
        if (pkg == packageName) return
        watch = scope.launch {
            while (isActive && foreground == pkg) {
                focus.budgetWarning(pkg)?.let { left ->
                    open(PopupActivity.budgetWarning(this@BlockerService, left))
                }
                if (focus.shouldBlock(pkg)) {
                    open(BlockActivity.intent(this@BlockerService, pkg))
                    foreground = null
                    return@launch
                }
                delay(RECHECK_MS)
            }
        }
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "Could not open over the app in front", it) }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.blocker_label), NotificationManager.IMPORTANCE_MIN)
                .apply {
                    description = getString(R.string.blocker_description)
                    setShowBadge(false)
                },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.blocker_label))
            .setContentText(getString(R.string.blocker_running))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BlockerService"
        private const val CHANNEL_ID = "selfup.app_lock"
        private const val NOTIFICATION_ID = 0x5e1f
        private const val RECHECK_MS = 30_000L
        private const val POLL_MS = 1_000L
        private const val IDLE_MS = 5_000L
        private val LOOKBACK: Duration = Duration.ofMinutes(5)
        private const val PREFS = "app_lock"
        private const val KEY_ON = "on"
        private val passThrough = setOf("com.android.systemui", "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard")

        /** Whether the lock is switched on and can open over other apps. */
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false) &&
                Settings.canDrawOverlays(context)

        /**
         * Switches the lock on. When "Display over other apps" is still off, opens its settings
         * page; the lock starts once the user comes back with it granted.
         */
        fun turnOn(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, true).apply()
            if (Settings.canDrawOverlays(context)) start(context) else context.startActivity(PopupActivity.overlaySettings(context))
        }

        /** Starts the lock if it is on. Safe to call often; Android may refuse from the background. */
        fun start(context: Context) {
            if (!isEnabled(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BlockerService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start the app lock", it) }
        }
    }
}
