package app.selfup

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.selfup.focus.BlockerService
import app.selfup.ui.SelfUpApp
import app.selfup.ui.theme.SelfUpTheme

class MainActivity : ComponentActivity() {
    /** Bumped each time an alert's "Report" opens the app, which opens the guided report. */
    private val reportRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so keep light system bar icons whatever the system theme is.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        if (savedInstanceState == null) take(intent)
        val app = application as SelfUpApplication
        setContent {
            SelfUpTheme {
                SelfUpApp(app.repository, app.health, app.records, app.skills, app.mind, app.goals, app.ai, app.planner, app.gym, app.focus, app.backup, app.work, app.hunter, app.dsa, reportRequest = reportRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REPORT, false) == true) reportRequests.intValue++
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Gym (or from the permission prompt) shows what was just logged there.
        (application as SelfUpApplication).syncGym()
        // Also where the app lock starts after "Display over other apps" is granted.
        BlockerService.start(this)
    }

    companion object {
        private const val EXTRA_REPORT = "report"

        /** Opens the app straight into the guided report. */
        fun report(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_REPORT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
