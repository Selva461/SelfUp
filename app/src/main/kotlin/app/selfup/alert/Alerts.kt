package app.selfup.alert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.selfup.MainActivity
import app.selfup.SelfUpApplication
import app.selfup.core.AutoCheck
import app.selfup.core.CompletionInput
import app.selfup.core.Drill
import app.selfup.core.Quest
import app.selfup.core.ScreenTime
import app.selfup.core.XpRules
import app.selfup.data.FocusEventKind
import app.selfup.focus.LockScreen
import app.selfup.reminders.QuestNotifications
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.questSubtitle
import app.selfup.ui.system.SystemBackground
import app.selfup.ui.theme.SystemColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The full-screen frame every System alert sits in: solid black, a red edge that breathes,
 * and one window in the middle. Nothing behind it shows through and nothing outside the
 * window reacts to a tap.
 */
@Composable
fun AlertFrame(
    title: String,
    accent: Color = SystemColors.Danger,
    waiting: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val appear = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
    }
    val edge by rememberInfiniteTransition(label = "alarm").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "edge",
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        SystemBackground(Modifier.fillMaxSize(), accent = accent)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).border(3.dp, accent.copy(alpha = edge)))
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .graphicsLayer {
                        scaleX = appear.value
                        scaleY = appear.value
                        alpha = ((appear.value - 0.92f) * 12.5f).coerceIn(0f, 1f)
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SystemLine("[ SYSTEM ALERT ]", color = accent.copy(alpha = edge.coerceAtLeast(0.5f)), style = MaterialTheme.typography.labelMedium)
                SystemWindow(title = title, accent = accent, notice = true, spacing = 14.dp, content = content)
                if (waiting > 0) {
                    MutedText(if (waiting == 1) "1 more alert waiting" else "$waiting more alerts waiting")
                }
            }
        }
    }
}

/** The alerts the System raises over other apps. */
object Alerts {
    const val LOCK_KEY = "lock"
    private const val WARNING_KEY = "warning"
    private const val NAG_KEY = "nag"

    private fun seed(): Long = System.currentTimeMillis() / 60_000

    /** The lock over an entertainment app whose budget is spent. */
    fun lockdown(app: SelfUpApplication, pkg: String): SystemAlert = SystemAlert(LOCK_KEY, SystemAlert.LOCK) { scope ->
        LockScreen(
            app = ScreenTime.scrollApps[pkg] ?: "This app",
            focus = app.focus,
            waiting = scope.waiting,
            onLeave = {
                app.alerts.goHome()
                scope.dismiss(closeApp = false)
            },
            onGrace = { scope.dismiss(closeApp = false) },
        )
    }

    /** The last warning before [pkg] locks. Its only button closes the app. */
    fun finalWarning(pkg: String, minutesLeft: Int): SystemAlert = SystemAlert(WARNING_KEY, SystemAlert.WARNING) { scope ->
        val name = ScreenTime.scrollApps[pkg] ?: "this app"
        AlertFrame("Final warning", accent = SystemColors.Gold, waiting = scope.waiting) {
            SystemLine("[$minutesLeft min to lockdown]", color = SystemColors.Gold, style = MaterialTheme.typography.titleLarge)
            SystemLine(Drill.finalWarning(name, minutesLeft, seed()), style = MaterialTheme.typography.bodyMedium)
            SystemButton("Close $name", onClick = { scope.dismiss(closeApp = true) }, accent = SystemColors.Gold)
        }
    }

    /** A quest's reminder, as an order. [dodges] is how often it was put off today. */
    fun order(app: SelfUpApplication, quest: Quest, check: AutoCheck?, dodges: Int): SystemAlert =
        SystemAlert("order:${quest.id}", SystemAlert.ORDER) { scope ->
            AlertFrame("System order", accent = if (dodges > 0) SystemColors.Danger else SystemColors.Blue, waiting = scope.waiting) {
                SystemLine(quest.title, style = MaterialTheme.typography.titleLarge)
                MutedText("${questSubtitle(quest)} · +${XpRules.completionXp(quest.difficulty)} XP", Modifier.fillMaxWidth())
                SystemLine(Drill.order(dodges, seed()), color = SystemColors.Danger)
                MutedText("Left undone, it costs XP at reset. Every \"Later\" is logged.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SystemButton("Later", onClick = {
                        app.record(FocusEventKind.DODGED, quest.id)
                        scope.dismiss()
                    }, modifier = Modifier.weight(1f), filled = false, accent = SystemColors.TextMuted)
                    when {
                        quest.input is CompletionInput.Number || check == AutoCheck.MEALS -> SystemButton("Report", onClick = {
                            app.record(FocusEventKind.OBEYED, quest.id)
                            app.alerts.open(MainActivity.report(app))
                            scope.dismiss(closeApp = false)
                        }, modifier = Modifier.weight(1f))
                        check != null -> SystemButton("On it", onClick = {
                            app.record(FocusEventKind.OBEYED, quest.id)
                            scope.dismiss()
                        }, modifier = Modifier.weight(1f))
                        else -> SystemButton("Done", onClick = {
                            app.record(FocusEventKind.OBEYED, quest.id)
                            app.appScope.launch(Dispatchers.IO) {
                                app.repository.complete(quest.id)
                                QuestNotifications.cancel(app, quest.id)
                            }
                            scope.dismiss()
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

    /** The nag over every quest still open. [level] counts nags already shown today. */
    fun nag(app: SelfUpApplication, open: List<Quest>, level: Int, failRun: Int): SystemAlert =
        SystemAlert(NAG_KEY, SystemAlert.NAG) { scope ->
            AlertFrame(if (level == 0) "Penalty warning" else "Penalty warning ${level + 1}", waiting = scope.waiting) {
                SystemLine(Drill.nag(open.size, level, failRun, seed()), color = SystemColors.Danger)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    open.take(SHOWN).forEach { SystemLine("· ${it.title}", style = MaterialTheme.typography.bodyMedium) }
                    if (open.size > SHOWN) MutedText("+${open.size - SHOWN} more", Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SystemButton("Ignore", onClick = {
                        app.record(FocusEventKind.DODGED)
                        scope.dismiss()
                    }, modifier = Modifier.weight(1f), filled = false, accent = SystemColors.TextMuted)
                    SystemButton("Report now", onClick = {
                        app.record(FocusEventKind.OBEYED)
                        app.alerts.open(MainActivity.report(app))
                        scope.dismiss(closeApp = false)
                    }, modifier = Modifier.weight(1f), accent = SystemColors.Danger)
                }
                MutedText("Ignoring is logged. The System keeps count.", Modifier.fillMaxWidth())
            }
        }

    private const val SHOWN = 5
}

private fun SelfUpApplication.record(kind: String, questId: Long? = null) {
    appScope.launch(Dispatchers.IO) { focus.record(kind, questId) }
}
