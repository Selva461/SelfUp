package app.selfup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selfup.core.StatProgress
import app.selfup.data.LifeRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SelfUpApplication).repository
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    StatsPlaceholder(repository)
                }
            }
        }
    }
}

/** Temporary screen proving the database works end to end. Replaced by Home in step 2. */
@Composable
private fun StatsPlaceholder(repository: LifeRepository) {
    val statsFlow = remember(repository) { repository.observeStats() }
    val stats by statsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Level ${StatProgress.overallLevel(stats)}", style = MaterialTheme.typography.headlineMedium)
        stats.forEach { stat ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${stat.stat.displayName} · Lv ${stat.level} · ${stat.rank.displayName}")
                LinearProgressIndicator(progress = { stat.progress.fraction })
            }
        }
    }
}
