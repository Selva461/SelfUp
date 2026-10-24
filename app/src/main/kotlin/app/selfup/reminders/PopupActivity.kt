package app.selfup.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.selfup.MainActivity
import app.selfup.SelfUpApplication
import app.selfup.alert.AlertFrame
import app.selfup.core.AutoCheck
import app.selfup.core.CompletionInput
import app.selfup.core.Drill
import app.selfup.core.Quest
import app.selfup.core.XpRules
import app.selfup.data.FocusEventKind
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.theme.SelfUpTheme
import app.selfup.ui.theme.SystemColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A quest's System order as a screen of its own, for when it can't be drawn over other apps
 * (no "Display over other apps", or the phone is locked and the notification's full-screen
 * intent opens it). Like the overlay alert it can't be backed or tapped out of: only Done,
 * Report or Later closes it.
 */
class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SelfUpApplication
        val questId = intent.getLongExtra(EXTRA_QUEST_ID, -1L)
        if (questId == -1L) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            SelfUpTheme {
                QuestPopup(app, questId, onClose = ::finish, onOpen = {
                    startActivity(MainActivity.report(this))
                    finish()
                })
            }
        }
    }

    companion object {
        private const val EXTRA_QUEST_ID = "quest"

        private fun base(context: Context) = Intent(context, PopupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun quest(context: Context, questId: Long): Intent = base(context)
            .putExtra(EXTRA_QUEST_ID, questId)
            .setData(Uri.parse("selfup://popup/quest/$questId"))

        /**
         * Whether a popup can open over other apps. Android only lets an app start a screen from
         * the background with "Display over other apps".
         */
        fun canPopUp(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun overlaySettings(context: Context): Intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Android 14+ needs this for the screen-off path; older versions always allow it. */
        fun canUseFullScreen(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() == true
    }
}

@Composable
private fun QuestPopup(app: SelfUpApplication, questId: Long, onClose: () -> Unit, onOpen: () -> Unit) {
    var quest by remember { mutableStateOf<Quest?>(null) }
    var check by remember { mutableStateOf<AutoCheck?>(null) }
    var dodges by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(questId) {
        quest = withContext(Dispatchers.IO) { app.repository.getQuest(questId) }
        quest?.let { q ->
            check = withContext(Dispatchers.IO) { app.repository.checkOf(q) }
            dodges = withContext(Dispatchers.IO) { app.focus.countToday(FocusEventKind.DODGED, q.id) }
            withContext(Dispatchers.IO) { app.focus.record(FocusEventKind.ORDERED, q.id) }
        }
        if (quest == null) onClose()
    }
    val q = quest ?: return
    val seed = remember { System.currentTimeMillis() / 60_000 }
    val record = { kind: String -> app.appScope.launch(Dispatchers.IO) { app.focus.record(kind, q.id) } }
    AlertFrame("System order", accent = if (dodges > 0) SystemColors.Danger else SystemColors.Blue) {
        SystemLine(q.title, style = MaterialTheme.typography.titleLarge)
        MutedText("${questSubtitle(q)} · +${XpRules.completionXp(q.difficulty)} XP")
        SystemLine(Drill.order(dodges, seed), color = SystemColors.Danger)
        MutedText("Left undone, it costs XP at reset. Every \"Later\" is logged.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemButton("Later", onClick = {
                record(FocusEventKind.DODGED)
                onClose()
            }, modifier = Modifier.weight(1f), filled = false, accent = SystemColors.TextMuted)
            if (q.input is CompletionInput.Number || check != null) {
                SystemButton("Report", onClick = {
                    record(FocusEventKind.OBEYED)
                    onOpen()
                }, modifier = Modifier.weight(1f))
            } else {
                SystemButton("Done", onClick = {
                    record(FocusEventKind.OBEYED)
                    scope.launch {
                        withContext(Dispatchers.IO) { app.repository.complete(questId) }
                        QuestNotifications.cancel(app, questId)
                        onClose()
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}
