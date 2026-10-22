package app.selfup.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import app.selfup.ui.theme.SystemColors

/**
 * Number entry for a logging quest, built for the 10-second rule: the keyboard opens
 * immediately, the last value is prefilled and selected so typing replaces it, and the
 * keyboard's Done key submits.
 */
@Composable
fun NumberEntryDialog(
    title: String,
    unit: String,
    initial: Double?,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = initial?.let(::formatNumber).orEmpty()
    var field by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val value = field.text.trim().toDoubleOrNull()?.takeIf { it >= 0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    SystemDialog(
        title = "Log",
        onDismiss = onDismiss,
        confirmText = "Log",
        onConfirm = { value?.let(onConfirm) },
        confirmEnabled = value != null,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = SystemColors.Text)
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            singleLine = true,
            prefix = { if (unit.isNotEmpty()) Text(unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { value?.let(onConfirm) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    }
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
