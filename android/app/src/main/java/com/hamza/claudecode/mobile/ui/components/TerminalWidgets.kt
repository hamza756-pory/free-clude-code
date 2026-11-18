package com.hamza.claudecode.mobile.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hamza.claudecode.mobile.ui.theme.Term

/** "[label]" style clickable text button. */
@Composable
fun TermButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Term.Blue,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = "[$text]",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = if (enabled) color else Term.TextFaint,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
fun TerminalHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (onBack != null) {
                TermButton("←", color = Term.Text, onClick = onBack)
                Spacer(Modifier.width(4.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = Term.Coral)
            Spacer(Modifier.weight(1f))
            actions()
        }
        DottedDivider(Modifier.padding(horizontal = 12.dp))
    }
}

/** Status lines shown under key input (validating / error / warning / info). */
@Composable
fun KeyStatus(
    validating: Boolean,
    error: String?,
    warning: String?,
    info: String?,
    onSaveAnyway: (() -> Unit)?
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (validating) {
            ThinkingSpinner(label = "Verifying key with openrouter.ai…", showHint = false)
        }
        if (error != null) {
            Text("✗ $error", color = Term.Red, style = MaterialTheme.typography.bodySmall)
        }
        if (warning != null) {
            Text("! $warning", color = Term.Yellow, style = MaterialTheme.typography.bodySmall)
            if (onSaveAnyway != null) TermButton("save anyway", color = Term.Yellow, onClick = onSaveAnyway)
        }
        if (info != null) {
            Text("✓ $info", color = Term.Green, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Terminal-like key input: "> sk-or-v1-…" with [paste], [show] and [continue ⏎].
 */
@Composable
fun ApiKeyInput(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    initial: String = ""
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var visible by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Paste your OpenRouter API key:", style = MaterialTheme.typography.bodyMedium, color = Term.Text)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Term.Coral.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text("> ", color = Term.Coral, style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("sk-or-v1-…", color = Term.TextFaint, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Text),
                    cursorBrush = SolidColor(Term.Coral),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation('•'),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (enabled) onSubmit(value) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            TermButton("paste", enabled = enabled) {
                clipboard.getText()?.text?.let { value = it.trim() }
            }
            TermButton(if (visible) "hide" else "show", color = Term.TextDim) { visible = !visible }
            TermButton("clear", color = Term.TextDim, enabled = enabled && value.isNotEmpty()) { value = "" }
            Spacer(Modifier.weight(1f))
            TermButton("continue ⏎", color = Term.Coral, enabled = enabled && value.isNotBlank()) { onSubmit(value) }
        }
    }
}

@Composable
fun TerminalConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Term.Surface,
        titleContentColor = Term.Coral,
        textContentColor = Term.Text,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TermButton(confirmLabel, color = Term.Red, onClick = onConfirm) },
        dismissButton = { TermButton("cancel", color = Term.TextDim, onClick = onDismiss) }
    )
}
