package app.selfup.alert

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf

/**
 * One message that takes over the whole screen until one of its buttons is pressed. Lower
 * [priority] goes first; posting a [key] that is already queued replaces it in place.
 */
class SystemAlert(
    val key: String,
    val priority: Int,
    val content: @Composable (AlertScope) -> Unit,
) {
    companion object {
        const val LOCK = 0
        const val WARNING = 1
        const val NAG = 2
        const val ORDER = 3
    }
}

/** What an alert's buttons can do. */
interface AlertScope {
    /** How many more alerts wait behind this one. */
    val waiting: Int

    /**
     * Closes this alert and shows the next. With [closeApp], an entertainment app that was
     * in front is sent away too: answering the System is never a way back into the feed.
     */
    fun dismiss(closeApp: Boolean = true)
}

/**
 * Every System alert goes through here, so however many fire at once, exactly one owns the
 * screen and the rest queue behind it. All of them share one opaque full-screen window
 * drawn over other apps (see [OverlayWindow]), which a swipe can't clear.
 *
 * Safe to call from any thread; the queue and the window live on the main thread.
 */
class AlertCenter(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val window by lazy { OverlayWindow(context) }
    private val queue = mutableStateListOf<SystemAlert>()

    /** The app in front, as the app lock last saw it, or null when it isn't running. */
    @Volatile var front: String? = null

    /** Packages a dismiss sends away from. */
    @Volatile var entertainment: Set<String> = emptySet()

    /** Whether alerts can be drawn over other apps at all. */
    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** Queues [alert]. Returns false when "Display over other apps" is off, so nothing will show. */
    fun post(alert: SystemAlert): Boolean {
        if (!canShow()) return false
        onMain {
            val at = queue.indexOfFirst { it.key == alert.key }
            if (at >= 0) {
                queue[at] = alert
            } else {
                val before = queue.indexOfFirst { it.priority > alert.priority }
                if (before < 0) queue.add(alert) else queue.add(before, alert)
            }
            if (!window.show { Host() }) queue.clear()
        }
        return true
    }

    /** Takes [key] off the screen or out of the queue, if it is there. */
    fun remove(key: String) = onMain {
        queue.removeAll { it.key == key }
        if (queue.isEmpty()) window.hide()
    }

    /** Whether [key] is showing or queued. Main thread only. */
    fun has(key: String): Boolean = queue.any { it.key == key }

    @Composable
    private fun Host() {
        val top = queue.firstOrNull() ?: return
        val scope = object : AlertScope {
            override val waiting: Int get() = queue.size - 1

            override fun dismiss(closeApp: Boolean) = onMain {
                if (closeApp && front in entertainment) goHome()
                queue.remove(top)
                if (queue.isEmpty()) window.hide()
            }
        }
        key(top.key) { top.content(scope) }
    }

    /**
     * Sends the user to the home screen. The alert's window is still up when this runs, which
     * is what lets Android 15 open a screen from the background.
     */
    fun goHome() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "Could not leave the app in front", it) }
    }

    /** Opens [intent] from an alert button, while the window still allows it. */
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "Could not open from an alert", it) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "AlertCenter"
    }
}
