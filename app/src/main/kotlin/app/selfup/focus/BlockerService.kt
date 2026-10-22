package app.selfup.focus

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import app.selfup.SelfUpApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Locks entertainment apps once the day's budget is spent. It only listens for which app
 * comes to the front (no window content is read) and shows [BlockActivity] over a locked
 * one. While a watched app stays open it re-checks every [RECHECK_MS], so a long reels
 * session is cut off when the budget runs out, not only on the next app switch.
 */
class BlockerService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var foreground: String? = null

    private val focus get() = (application as SelfUpApplication).focus

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // System UI (notification shade, keyboard) passes over an app without leaving it.
        if (pkg == foreground || pkg in passThrough) return
        foreground = pkg
        watch?.cancel()
        if (pkg == packageName) return
        watch = scope.launch {
            while (isActive && foreground == pkg) {
                if (focus.shouldBlock(pkg)) {
                    startActivity(BlockActivity.intent(this@BlockerService, pkg))
                    foreground = null
                    return@launch
                }
                delay(RECHECK_MS)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val RECHECK_MS = 30_000L
        private val passThrough = setOf("com.android.systemui", "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard")

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, BlockerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
