package app.selfup.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.selfup.MainActivity
import app.selfup.SelfUpApplication
import app.selfup.core.AutoCheck
import app.selfup.core.CompletionInput
import app.selfup.core.Quest
import app.selfup.core.ScreenTime
import app.selfup.core.XpRules
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.theme.SelfUpTheme
import app.selfup.ui.theme.SystemColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A System window that pops up over whatever is on screen: a quest reminder with Done and
 * Later, or the warning that the entertainment budget is nearly spent. It sits on a dim
 * scrim in its own translucent task, so dismissing it returns to what the user was doing.
 */
class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SelfUpApplication
        val questId = intent.getLongExtra(EXTRA_QUEST_ID, -1L)
        val warning = intent.getIntExtra(EXTRA_MINUTES_LEFT, -1)
        setContent {
            SelfUpTheme {
                Scrim(onDismiss = ::finish) {
                    when {
                        questId != -1L -> QuestPopup(app, questId, onClose = ::finish, onOpen = {
                            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        })
                        warning >= 0 -> BudgetPopup(warning, onClose = ::finish)
                        else -> finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_QUEST_ID = "quest"
        private const val EXTRA_MINUTES_LEFT = "minutes_left"

        private fun base(context: Context) = Intent(context, PopupActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun quest(context: Context, questId: Long): Intent = base(context)
            .putExtra(EXTRA_QUEST_ID, questId)
            .setData(Uri.parse("selfup://popup/quest/$questId"))

        fun budgetWarning(context: Context, minutesLeft: Int): Intent = base(context)
            .putExtra(EXTRA_MINUTES_LEFT, minutesLeft)
            .setData(Uri.parse("selfup://popup/budget"))

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
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val appear = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                    alpha = ((appear.value - 0.9f) * 10f).coerceIn(0f, 1f)
                }
                // Taps on the window itself must not dismiss it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) { content() }
    }
}

@Composable
private fun QuestPopup(app: SelfUpApplication, questId: Long, onClose: () -> Unit, onOpen: () -> Unit) {
    var quest by remember { mutableStateOf<Quest?>(null) }
    var check by remember { mutableStateOf<AutoCheck?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(questId) {
        quest = withContext(Dispatchers.IO) { app.repository.getQuest(questId) }
        quest?.let { q -> check = withContext(Dispatchers.IO) { app.repository.checkOf(q) } }
        if (quest == null) onClose()
    }
    val q = quest ?: return
    SystemWindow(title = "Quest Info", notice = true) {
        SystemLine("[Reminder]", color = SystemColors.Blue, style = MaterialTheme.typography.labelMedium)
        SystemLine(q.title, style = MaterialTheme.typography.titleLarge)
        MutedText(
            if (check != null) {
                "${questSubtitle(q)} · up to +${XpRules.completionXp(q.difficulty)} XP · checked from the ${check!!.source.lowercase()}"
            } else {
                "${questSubtitle(q)} · +${XpRules.completionXp(q.difficulty)} XP"
            },
        )
        SystemLine(
            "WARNING: Failing to complete the daily quest will bring a penalty.",
            color = SystemColors.Danger,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemButton("Later", onClick = onClose, modifier = Modifier.weight(1f), filled = false, accent = SystemColors.TextMuted)
            if (q.input is CompletionInput.Number || check != null) {
                SystemButton(if (check != null) "Open" else "Log it", onClick = onOpen, modifier = Modifier.weight(1f))
            } else {
                SystemButton("Done", onClick = {
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

@Composable
private fun BudgetPopup(minutesLeft: Int, onClose: () -> Unit) {
    SystemWindow(title = "Notification", notice = true, accent = SystemColors.Gold) {
        SystemLine("[Entertainment budget almost spent]", color = SystemColors.Gold)
        SystemLine(
            "$minutesLeft min left of ${ScreenTime.DAILY_BUDGET_MINUTES} today. After that, these apps lock until reset.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SystemButton("Understood", onClick = onClose, accent = SystemColors.Gold)
    }
}
