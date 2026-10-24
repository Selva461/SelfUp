package app.selfup.focus

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import app.selfup.SelfUpApplication
import app.selfup.alert.AlertFrame
import app.selfup.core.Drill
import app.selfup.core.ScreenTime
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemLine
import app.selfup.ui.theme.SelfUpTheme
import app.selfup.ui.theme.SystemColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The lock screen shown over an entertainment app once the day's budget is spent. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        // Back must not drop the user into the app they were locked out of.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        val focus = (application as SelfUpApplication).focus
        val app = intent.getStringExtra(EXTRA_PACKAGE)?.let { ScreenTime.scrollApps[it] } ?: "This app"
        setContent {
            SelfUpTheme {
                LockScreen(app, focus, onLeave = ::leave, onGrace = ::finish)
            }
        }
    }

    private fun leave() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, pkg: String): Intent = Intent(context, BlockActivity::class.java)
            .putExtra(EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}

@Composable
fun LockScreen(app: String, focus: FocusRepository, waiting: Int = 0, onLeave: () -> Unit, onGrace: () -> Unit) {
    var minutes by remember { mutableStateOf<Int?>(null) }
    var budget by remember { mutableStateOf(ScreenTime.DAILY_BUDGET_MINUTES) }
    var graceLeft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val today = withContext(Dispatchers.IO) { focus.readToday() }
        minutes = today.minutes
        budget = today.budget
        graceLeft = focus.graceAvailable()
    }
    val seed = remember { System.currentTimeMillis() / 60_000 }
    AlertFrame("Lockdown", waiting = waiting) {
        SystemLine("[$app is locked]", color = SystemColors.Danger, style = MaterialTheme.typography.titleLarge)
        SystemLine(
            Drill.lockdown(app, minutes ?: budget, budget, seed),
            style = MaterialTheme.typography.bodyMedium,
        )
        MutedText("Locked until the daily reset. More minutes cost gold in the shop, and gold has to be earned first.")
        SystemButton("Close $app", onClick = onLeave, accent = SystemColors.Danger)
        if (graceLeft) {
            SystemButton(
                "Beg for ${ScreenTime.GRACE_MINUTES} minutes (once a day)",
                onClick = { scope.launch { if (focus.useGrace()) onGrace() } },
                accent = SystemColors.TextMuted,
                filled = false,
            )
        }
    }
}
