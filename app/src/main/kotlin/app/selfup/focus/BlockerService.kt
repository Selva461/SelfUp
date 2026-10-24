package app.selfup.focus

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
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
import app.selfup.alert.Alerts
import app.selfup.core.ForegroundTracker
import app.selfup.core.UsageEvent
import app.selfup.reminders.PopupActivity
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * screen) and covers a locked one with the lockdown alert. While a watched app stays open
 * it re-checks every [RECHECK_MS], and every [LOCKED_RECHECK_MS] once its budget is gone, so
 * a long reading session is cut off when the budget runs out and a lock that was somehow
 * cleared comes straight back.
 *
 * The lock is a window drawn over the app (see [app.selfup.alert.AlertCenter]), then
 * [BlockActivity] opens on top of it so the app underneath stops. Where Android or the phone
 * maker refuses to open a screen from the background, the window alone keeps the app locked.
 *
 * This is a foreground service rather than an accessibility service on purpose: Play
 * Protect blocks sideloaded installs of any app that declares an accessibility service.
 */
class BlockerService : Service() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.w(TAG, "App lock check failed", e) },
    )
    private val usage by lazy { UsageReader(this) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val tracker = ForegroundTracker(passThrough)
    private var poll: Job? = null
    private var watch: Job? = null
    private var foreground: String? = null

    /** The app under the lockdown alert, or null when none is up. */
    private var lockedPkg: String? = null
    private var audioFocus: AudioFocusRequest? = null

    private val app get() = application as SelfUpApplication
    private val focus get() = app.focus

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started with startForegroundService must go foreground even if it then stops.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        if (!isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (poll?.isActive != true) poll = scope.launch { pollFront() }
        return START_STICKY
    }

    /** Follows the usage log for the app in front, about once a second while the screen is on. */
    private suspend fun pollFront() {
        val power = getSystemService(PowerManager::class.java)
        // The first read looks back far enough to find an app that was already open.
        var seen = Instant.now().minus(START_LOOKBACK)
        // Events logged in the same millisecond as [seen], already handled.
        var handled = emptySet<UsageEvent>()
        var first = true
        app.alerts.entertainment = withContext(Dispatchers.IO) { runCatching { usage.entertainment() }.getOrDefault(emptySet()) }
        while (scope.isActive) {
            if (power?.isInteractive == false) {
                // Every app pauses with the screen off; start clean when it comes back on.
                tracker.clear()
                delay(IDLE_MS)
                continue
            }
            val now = Instant.now()
            // Events can land in the log a moment after their timestamp, so the window starts
            // at the last event handled rather than at the last poll, and includes its millisecond.
            val from = if (first) seen else maxOf(seen, now.minus(LOOKBACK))
            first = false
            val fresh = withContext(Dispatchers.IO) { runCatching { usage.events(from, now) }.getOrDefault(emptyList()) }
                .filter { !it.at.isBefore(seen) && it !in handled }
            if (fresh.isNotEmpty()) {
                val last = fresh.maxOf { it.at }
                handled = if (last == seen) handled + fresh else fresh.filter { it.at == last }.toSet()
                seen = last
                when (val front = tracker.apply(fresh)) {
                    foreground -> Unit
                    null -> onBackground()
                    else -> onFront(front)
                }
            }
            delay(POLL_MS)
        }
    }

    private fun onFront(pkg: String) {
        foreground = pkg
        app.alerts.front = pkg
        watch?.cancel()
        // Anything else in front (the home screen, another app, or BlockActivity taking over)
        // ends the lock. Coming back to the locked app keeps it, or brings it straight back.
        if (lockedPkg != pkg) unlock()
        if (pkg == packageName) return
        watch = scope.launch {
            var spent = false
            while (isActive && foreground == pkg) {
                // Checked against the alert itself, so a lock closed without leaving the app
                // (its home button failed, or the grace minutes ran out) comes straight back.
                if (!app.alerts.has(Alerts.LOCK_KEY)) {
                    focus.budgetWarning(pkg)?.let { app.alerts.post(Alerts.finalWarning(pkg, it)) }
                    spent = focus.shouldBlock(pkg)
                    if (spent) lockOut(pkg)
                }
                delay(if (spent) LOCKED_RECHECK_MS else RECHECK_MS)
            }
        }
    }

    /** The app in front went to the background with nothing new in front: the screen went off. */
    private fun onBackground() {
        foreground = null
        app.alerts.front = null
        watch?.cancel()
    }

    private fun lockOut(pkg: String) {
        unlock()
        lockedPkg = pkg
        app.alerts.post(Alerts.lockdown(app, pkg))
        // Video and music under the lock stop, as they would behind a full screen.
        audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .build()
            .also { request -> runCatching { audio?.requestAudioFocus(request) } }
        // With the lock window showing, Android 15 allows opening a screen from the background.
        open(BlockActivity.intent(this, pkg))
    }

    private fun unlock() {
        if (lockedPkg == null) return
        lockedPkg = null
        app.alerts.remove(Alerts.LOCK_KEY)
        audioFocus?.let { request -> runCatching { audio?.abandonAudioFocusRequest(request) } }
        audioFocus = null
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
        unlock()
        app.alerts.front = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BlockerService"
        private const val CHANNEL_ID = "selfup.app_lock"
        private const val NOTIFICATION_ID = 0x5e1f
        private const val RECHECK_MS = 15_000L
        private const val LOCKED_RECHECK_MS = 2_000L
        private const val POLL_MS = 1_000L
        private const val IDLE_MS = 5_000L
        private val LOOKBACK: Duration = Duration.ofMinutes(5)
        private val START_LOOKBACK: Duration = Duration.ofHours(1)
        private const val PREFS = "app_lock"
        private const val KEY_ON = "on"
        private val passThrough = setOf("com.android.systemui", "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard")

        /** Whether the lock is switched on and can open over other apps. */
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false) &&
                Settings.canDrawOverlays(context)

        /**
         * Switches the lock on. When "Display over other apps" is still off, opens its settings
         * page; the lock starts once the user comes back with it granted. Then asks to lift
         * battery limits, so the phone doesn't kill it.
         */
        fun turnOn(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, true).apply()
            if (!Settings.canDrawOverlays(context)) {
                context.startActivity(PopupActivity.overlaySettings(context))
                return
            }
            start(context)
            if (!isUnrestricted(context)) runCatching { context.startActivity(unrestrictIntent(context)) }
        }

        /**
         * Whether the phone lets the lock run without battery limits. Without it, many phones
         * (Xiaomi, Oppo, Vivo, Samsung) kill the service minutes after the screen goes off, and
         * Android refuses to restart it from the background.
         */
        fun isUnrestricted(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

        /** Android's one-tap "let it run in the background" prompt for this app. */
        @SuppressLint("BatteryLife")
        fun unrestrictIntent(context: Context): Intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Starts the lock if it is on. Safe to call often; Android may refuse from the background. */
        fun start(context: Context) {
            if (!isEnabled(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BlockerService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start the app lock", it) }
        }
    }
}
