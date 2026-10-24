package app.selfup.focus

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.selfup.core.ScreenTime
import app.selfup.reminders.BudgetPopup
import app.selfup.reminders.Scrim
import app.selfup.ui.theme.SelfUpTheme

/**
 * The lock screen and the budget warning, drawn as windows straight over the app in front
 * with "Display over other apps". Opening an activity from a background service is not
 * reliable: Android 15 refuses it unless the app already shows an overlay window, and
 * Xiaomi, Vivo and Oppo block it behind their own "pop-up windows" switch, silently in both
 * cases. A window added here needs nothing more than the overlay permission.
 *
 * Main thread only, like the service that owns it.
 */
class LockOverlay(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var root: View? = null
    private var owner: OverlayOwner? = null
    private var audioFocus: AudioFocusRequest? = null

    /** The app the lock window covers, or null when no lock window is up. */
    var lockedPkg: String? = null
        private set

    val isLocked: Boolean get() = lockedPkg != null

    /**
     * Covers [pkg] with the lock screen. It stays until [hide], which the service calls once
     * another app (the home screen, or [BlockActivity] taking over) comes to the front.
     */
    fun showLock(pkg: String, focus: FocusRepository, onLeave: () -> Unit, onGrace: () -> Unit) {
        val app = ScreenTime.scrollApps[pkg] ?: "This app"
        if (!show(onBack = onLeave) { LockScreen(app, focus, onLeave = onLeave, onGrace = onGrace) }) return
        lockedPkg = pkg
        // Video and music under the lock stop, as they would behind a full screen.
        audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .build()
            .also { request -> runCatching { audio?.requestAudioFocus(request) } }
    }

    /** The once-a-day warning that the budget is nearly spent; a tap anywhere closes it. */
    fun showWarning(minutesLeft: Int) {
        if (isLocked) return
        show(onBack = ::hide) {
            Scrim(onDismiss = ::hide) { BudgetPopup(minutesLeft, onClose = ::hide) }
        }
    }

    fun hide() {
        lockedPkg = null
        audioFocus?.let { request -> runCatching { audio?.abandonAudioFocusRequest(request) } }
        audioFocus = null
        root?.let { view -> runCatching { windows?.removeView(view) } }
        root = null
        owner?.destroy()
        owner = null
    }

    private fun show(onBack: () -> Unit, content: @Composable () -> Unit): Boolean {
        hide()
        val manager = windows ?: return false
        val owner = OverlayOwner().also { it.resume() }
        val view = OverlayRoot(context, onBack).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(ComposeView(context).apply { setContent { SelfUpTheme(content) } })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // Cover the status and navigation bars too; the content pads itself clear of them.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        return runCatching { manager.addView(view, params) }
            .onSuccess {
                root = view
                this.owner = owner
            }
            .onFailure {
                Log.w(TAG, "Could not draw over the app in front", it)
                owner.destroy()
            }
            .isSuccess
    }

    private companion object {
        const val TAG = "LockOverlay"
    }
}

/** Takes the back gesture or key, which would otherwise do nothing over another app's window. */
private class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private var backCallback: Any? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // Android 16 stops sending the back key to apps that target it; this is what replaces it.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { onBack() }
            findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            backCallback = callback
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let { findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it) }
        }
        backCallback = null
        super.onDetachedFromWindow()
    }
}

/** What Compose needs from an activity, for a window that has none. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun resume() {
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
