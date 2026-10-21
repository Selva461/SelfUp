@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.selfup.ai.PlanReply
import app.selfup.core.ImportedGoal
import app.selfup.ui.glass.hairline
import app.selfup.ui.skills.PrimaryButton
import app.selfup.ui.theme.Surfaces
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface AiStep {
    data object Ask : AiStep
    data object Thinking : AiStep
    data class Done(val reply: PlanReply) : AiStep
}

/**
 * Describe what you want in your own words; a model on Groq writes the plan code, and you see
 * exactly what it would add before anything is saved. Your current bests go along with the
 * request so targets start from real numbers.
 */
@Composable
fun AiPlanSheet(
    hasKey: Boolean,
    activeCount: Int,
    today: LocalDate,
    onSaveKey: (String) -> Unit,
    onForgetKey: () -> Unit,
    plan: suspend (String) -> PlanReply,
    onImport: (List<ImportedGoal>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val accent = MaterialTheme.colorScheme.primary
    var keyDraft by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<AiStep>(AiStep.Ask) }

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
            Text("Plan with AI", style = MaterialTheme.typography.headlineSmall)
            if (!hasKey) {
                Text(
                    "Planning runs on Groq, which has a free tier. Make a key at console.groq.com/keys and paste it here. " +
                        "It stays on this phone and is only sent to Groq.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Link("Open console.groq.com/keys") { uri.openUri("https://console.groq.com/keys") }
                Field(keyDraft, { keyDraft = it }, placeholder = "gsk_…", secret = true, minHeight = 52)
                PrimaryButton("Save key", accent, { onSaveKey(keyDraft) }, enabled = keyDraft.trim().length >= 20)
            } else {
                AnimatedContent(targetState = step, label = "ai") { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (current) {
                            AiStep.Ask -> {
                                Text(
                                    "Say what you want to get better at and by when. Your current bests are sent along so the targets start from real numbers.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Field(request, { request = it }, placeholder = "Bench 80 kg by January, run 5 km without stopping, get solid at Java collections", minHeight = 110)
                                PrimaryButton("Make a plan", accent, {
                                    step = AiStep.Thinking
                                    scope.launch { step = AiStep.Done(plan(request)) }
                                }, enabled = request.trim().length >= 5)
                                Link("Forget my Groq key", MaterialTheme.colorScheme.onSurfaceVariant, onForgetKey)
                            }
                            AiStep.Thinking -> Row(
                                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.5.dp)
                                Text("Writing your plan…", style = MaterialTheme.typography.bodyLarge)
                            }
                            is AiStep.Done -> when (val reply = current.reply) {
                                is PlanReply.Failed -> {
                                    Text(reply.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A7A))
                                    PrimaryButton("Try again", accent, { step = AiStep.Ask })
                                }
                                is PlanReply.Ok -> {
                                    PlanPreview(reply.result.goals, activeCount, today) {
                                        scope.launch { sheetState.hide() }.invokeOnCompletion { onImport(reply.result.goals) }
                                    }
                                    Text(
                                        "Written by ${reply.model} on Groq. AI plans can be off; check the numbers before you lift them.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Link("Change the request") { step = AiStep.Ask }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, secret: Boolean = false, minHeight: Int) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = secret,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .background(Surfaces.Fill, RoundedCornerShape(16.dp))
            .hairline(RoundedCornerShape(16.dp))
            .padding(14.dp),
        decorationBox = { field ->
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            field()
        },
    )
}

@Composable
private fun Link(text: String, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    )
}
