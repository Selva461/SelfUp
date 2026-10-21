@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selfup.core.Goal
import app.selfup.core.GoalPlanner
import app.selfup.core.GoalRules
import app.selfup.core.ImportedGoal
import app.selfup.core.PlanCode
import app.selfup.core.PlanCodeResult
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.glass.hairline
import app.selfup.ui.skills.PrimaryButton
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.Surfaces
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Paste a plan Claude wrote in chat. It's checked as you paste, and nothing is saved until
 * you tap Add, so a bad paste costs nothing.
 */
@Composable
fun ImportPlanSheet(activeCount: Int, today: LocalDate, onImport: (List<ImportedGoal>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    val result = remember(text) { if (text.isBlank()) null else PlanCode.parse(text) }
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Import a plan", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Tell Claude your goals in the SelfUp project and ask for a plan code. Copy it, then paste it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .background(Surfaces.Fill, RoundedCornerShape(16.dp))
                    .hairline(RoundedCornerShape(16.dp))
                    .padding(14.dp),
                decorationBox = { field ->
                    if (text.isEmpty()) {
                        Text("${PlanCode.HEADER}\nGOAL …", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Paste",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable(role = Role.Button) { clipboard.getText()?.text?.let { text = it } },
                )
                if (text.isNotEmpty()) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp).clickable(role = Role.Button) { text = "" },
                    )
                }
            }
            when (result) {
                null -> Unit
                is PlanCodeResult.Error -> Text(
                    if (result.line > 0) "Line ${result.line}: ${result.message}" else result.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF8A7A),
                )
                is PlanCodeResult.Ok -> {
                    val free = (GoalRules.MAX_ACTIVE - activeCount).coerceAtLeast(0)
                    Text("${result.goals.size} goal${if (result.goals.size == 1) "" else "s"}".uppercase(), style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GlassGroup {
                        result.goals.forEachIndexed { i, goal ->
                            if (i > 0) GroupSeparator(start = 16.dp)
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val preview = Goal(measure = goal.measure, target = goal.target, start = today, deadline = today.plusWeeks(goal.weeks.toLong()))
                                Text(GoalPlanner.title(preview), style = MaterialTheme.typography.bodyLarge)
                                val tasks = goal.plan.days.values.sumOf { it.size }
                                Text(
                                    buildString {
                                        append("$tasks task${if (tasks == 1) "" else "s"} a week")
                                        if (i >= free) append(" · queued")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    PrimaryButton("Add ${result.goals.size} goal${if (result.goals.size == 1) "" else "s"}", accent, {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onImport(result.goals) }
                    })
                }
            }
        }
    }
}
