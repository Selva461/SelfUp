package app.selfup

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.selfup.focus.BlockerService
import app.selfup.ui.SelfUpApp
import app.selfup.ui.theme.SelfUpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so keep light system bar icons whatever the system theme is.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val app = application as SelfUpApplication
        setContent {
            SelfUpTheme {
                SelfUpApp(app.repository, app.health, app.records, app.skills, app.mind, app.goals, app.ai, app.planner, app.gym, app.focus, app.backup, app.work, app.hunter, app.dsa)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Gym (or from the permission prompt) shows what was just logged there.
        (application as SelfUpApplication).syncGym()
        // Also where the app lock starts after "Display over other apps" is granted.
        BlockerService.start(this)
    }
}
