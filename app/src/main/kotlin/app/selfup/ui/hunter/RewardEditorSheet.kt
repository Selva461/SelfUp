@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.hunter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.selfup.core.Gold
import app.selfup.core.Reward
import app.selfup.core.RewardKind
import app.selfup.ui.components.FormSection
import app.selfup.ui.components.SheetTitle
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemSegmented
import app.selfup.ui.components.SystemSheet
import app.selfup.ui.components.SystemTextButton
import app.selfup.ui.theme.SystemColors

private enum class Kind(val label: String) { TREAT("Real-life treat"), SCREEN("Screen minutes") }

/** Add or edit a shop reward: what it is and what it costs. */
@Composable
fun RewardEditorSheet(
    initial: Reward?,
    onSave: (Reward) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var kind by remember { mutableStateOf(if (initial?.kind == RewardKind.SCREEN_MINUTES) Kind.SCREEN else Kind.TREAT) }
    var minutes by remember { mutableStateOf((initial?.minutes?.takeIf { it > 0 } ?: 15).toString()) }

    val priceValue = price.toIntOrNull()
    val minutesValue = minutes.toIntOrNull()
    val canSave = title.isNotBlank() &&
        priceValue != null && priceValue in Gold.MIN_PRICE..Gold.MAX_PRICE &&
        (kind == Kind.TREAT || (minutesValue != null && minutesValue in 5..60))

    SystemSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SheetTitle(if (initial == null) "New reward" else "Edit reward", SystemColors.Gold)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                label = { Text("Reward") },
                placeholder = { Text("Order in, a new game, a lazy morning") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FormSection("Price in gold") {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter(Char::isDigit).take(6) },
                    suffix = { Text("G") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "A clear day with every quest done well pays about 100 G. Price it so it takes real effort.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SystemColors.TextMuted,
                )
            }
            FormSection("Type") {
                SystemSegmented(Kind.entries, kind, label = { it.label }, onSelect = { kind = it }, accent = SystemColors.Gold)
                if (kind == Kind.SCREEN) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes added to today's budget (5–60)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "At most ${Gold.SCREEN_BUYS_PER_DAY} screen rewards a day, and they only last until the 04:00 reset.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SystemColors.TextMuted,
                    )
                }
            }
            SystemButton(
                if (initial == null) "Add reward" else "Save",
                onClick = {
                    onSave(
                        Reward(
                            id = initial?.id ?: 0,
                            title = title.trim(),
                            price = requireNotNull(priceValue),
                            kind = if (kind == Kind.SCREEN) RewardKind.SCREEN_MINUTES else RewardKind.CUSTOM,
                            minutes = if (kind == Kind.SCREEN) requireNotNull(minutesValue) else 0,
                        ),
                    )
                },
                enabled = canSave,
                accent = SystemColors.Gold,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    SystemTextButton("Delete", onClick = { onDelete(initial.id) }, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                SystemTextButton("Cancel", onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
