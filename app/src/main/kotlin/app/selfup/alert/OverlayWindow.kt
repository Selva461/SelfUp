package app.selfup.alert

import android.content.Context
import android.graphics.PixelFormat
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
import app.selfup.ui.theme.SelfUpTheme

/**
 * One full-screen window drawn straight over whatever is in front, with "Display over other
 * apps". Opening an activity from the background is not reliable: Android 15 refuses it
 * unless the app already shows an overlay window, and Xiaomi, Vivo and Oppo block it behind
 * their own "pop-up windows" switch, silently in both cases. A window added here needs
 * nothing more than the overlay permission, and it is not a notification: there is nothing
 * to swipe away. Back does nothing; only the window's own buttons close it.
 *
 * Main thread only.
 */
class OverlayWindow(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = root != null

    /** Shows [content] unless the window is already up. Returns false when Android refuses it. */
    fun show(content: @Composable () -> Unit): Boolean {
        if (root != null) return true
        val manager = windows ?: return false
        val owner = OverlayOwner().also { it.resume() }
        val view = OverlayRoot(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(ComposeView(context).apply { setContent { SelfUpTheme(content) } })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
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

    fun hide() {
        root?.let { view -> runCatching { windows?.removeView(view) } }
        root = null
        owner?.destroy()
        owner = null
    }

    private companion object {
        const val TAG = "OverlayWindow"
    }
}

/** Swallows the back gesture and key, so the window can't be backed out of. */
private class OverlayRoot(context: Context) : FrameLayout(context) {
    private var backCallback: Any? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }

    // Android 16 stops sending the back key to apps that target it; this is what replaces it.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { }
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
