package app.selfup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.selfup.ui.SelfUpApp
import app.selfup.ui.theme.SelfUpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SelfUpApplication
        setContent {
            SelfUpTheme {
                SelfUpApp(app.repository, app.health)
            }
        }
    }
}
