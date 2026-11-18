package com.hamza.claudecode.mobile.ui.screens

import com.hamza.claudecode.mobile.util.Glyph
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.claudecode.mobile.BuildConfig
import com.hamza.claudecode.mobile.data.network.OpenRouterClient
import com.hamza.claudecode.mobile.data.security.SecureKeyStore
import com.hamza.claudecode.mobile.ui.components.ApiKeyInput
import com.hamza.claudecode.mobile.ui.components.DottedDivider
import com.hamza.claudecode.mobile.ui.components.KeyStatus
import com.hamza.claudecode.mobile.ui.components.ModelPickerSheet
import com.hamza.claudecode.mobile.ui.components.TermButton
import com.hamza.claudecode.mobile.ui.components.TerminalConfirmDialog
import com.hamza.claudecode.mobile.ui.components.TerminalHeader
import com.hamza.claudecode.mobile.ui.theme.Term
import com.hamza.claudecode.mobile.ui.viewmodel.ChatViewModel
import com.hamza.claudecode.mobile.ui.viewmodel.KeyViewModel

@Composable
fun SettingsScreen(
    keyViewModel: KeyViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onKeyDeleted: () -> Unit
) {
    val apiKey by keyViewModel.apiKey.collectAsStateWithLifecycle()
    val keyState by keyViewModel.state.collectAsStateWithLifecycle()
    val model by chatViewModel.selectedModel.collectAsStateWithLifecycle()
    val modelState by chatViewModel.modelState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var editingKey by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteKey by rememberSaveable { mutableStateOf(false) }
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(keyState.saved) {
        if (keyState.saved) editingKey = false
    }
    DisposableEffect(Unit) {
        onDispose { keyViewModel.clearMessages() }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        TerminalHeader(title = "Settings", onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Section("API key")
            KeyValue("OpenRouter", SecureKeyStore.mask(apiKey))
            KeyValue("Storage", keyViewModel.storageDescription())
            Row(verticalAlignment = Alignment.CenterVertically) {
                TermButton(if (editingKey) "cancel" else "change key") {
                    keyViewModel.clearMessages()
                    editingKey = !editingKey
                }
                TermButton("delete key", color = Term.Red, enabled = !apiKey.isNullOrBlank()) {
                    confirmDeleteKey = true
                }
                TermButton("get key ↗", color = Term.TextDim) { uriHandler.openUri(KeyViewModel.KEYS_URL) }
            }
            if (editingKey) {
                Spacer(Modifier.height(8.dp))
                ApiKeyInput(enabled = !keyState.validating, onSubmit = { keyViewModel.submit(it) })
            }
            KeyStatus(
                validating = keyState.validating,
                error = keyState.error,
                warning = keyState.warning,
                info = keyState.info,
                onSaveAnyway = if (keyState.unverifiedKey != null) ({ keyViewModel.saveUnverified() }) else null
            )

            Spacer(Modifier.height(18.dp))
            Section("Model")
            KeyValue("Default", model)
            KeyValue(
                "Free models",
                when {
                    modelState.loading -> "loading…"
                    modelState.error != null -> "error · ${modelState.error}"
                    else -> modelState.models.size.toString()
                }
            )
            Row {
                TermButton("choose model") {
                    chatViewModel.refreshModels()
                    showPicker = true
                }
                TermButton("refresh list", color = Term.TextDim) { chatViewModel.refreshModels() }
            }

            Spacer(Modifier.height(18.dp))
            Section("Data")
            KeyValue("History", "Room database · on device")
            TermButton("clear all sessions", color = Term.Red) { confirmClearAll = true }

            Spacer(Modifier.height(18.dp))
            Section("About")
            KeyValue("Version", BuildConfig.VERSION_NAME)
            KeyValue("Endpoint", "${OpenRouterClient.BASE_URL}/chat/completions")
            KeyValue("Headers", "Authorization, HTTP-Referer, X-Title")
            KeyValue("X-Title", OpenRouterClient.APP_TITLE)
            Spacer(Modifier.height(8.dp))
            TermButton("source on GitHub ↗", color = Term.TextDim) { uriHandler.openUri(OpenRouterClient.APP_REFERER) }
            Spacer(Modifier.height(16.dp))
            DottedDivider()
            Text(
                "Designed by Hamza Alfarai",
                color = Term.TextFaint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (showPicker) {
        ModelPickerSheet(
            state = modelState,
            selectedId = model,
            onSelect = {
                chatViewModel.selectModel(it)
                showPicker = false
            },
            onRefresh = { chatViewModel.refreshModels() },
            onDismiss = { showPicker = false }
        )
    }

    if (confirmDeleteKey) {
        TerminalConfirmDialog(
            title = "Delete API key?",
            message = "The encrypted key will be erased from this device. You will need to paste it again to chat.",
            confirmLabel = "delete",
            onConfirm = {
                confirmDeleteKey = false
                chatViewModel.stop()
                keyViewModel.deleteKey()
                onKeyDeleted()
            },
            onDismiss = { confirmDeleteKey = false }
        )
    }

    if (confirmClearAll) {
        TerminalConfirmDialog(
            title = "Clear all sessions?",
            message = "Every saved conversation will be permanently deleted from the local database.",
            confirmLabel = "clear all",
            onConfirm = {
                confirmClearAll = false
                chatViewModel.deleteAllConversations()
            },
            onDismiss = { confirmClearAll = false }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text("${Glyph.STAR} $title", color = Term.Coral, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key.padEnd(12), color = Term.TextDim, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Term.Text, style = MaterialTheme.typography.bodySmall)
    }
}
