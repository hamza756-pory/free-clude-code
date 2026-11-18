package com.hamza.claudecode.mobile.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.claudecode.mobile.domain.SlashCommand
import com.hamza.claudecode.mobile.ui.components.DottedDivider
import com.hamza.claudecode.mobile.ui.components.MessageItem
import com.hamza.claudecode.mobile.ui.components.ModelPickerSheet
import com.hamza.claudecode.mobile.ui.components.ShortcutBar
import com.hamza.claudecode.mobile.ui.components.SlashSuggestions
import com.hamza.claudecode.mobile.ui.components.TermButton
import com.hamza.claudecode.mobile.ui.theme.Term
import com.hamza.claudecode.mobile.ui.viewmodel.ChatEvent
import com.hamza.claudecode.mobile.ui.viewmodel.ChatViewModel
import com.hamza.claudecode.mobile.util.ExportFormat
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val transcript by chatViewModel.transcript.collectAsStateWithLifecycle()
    val streaming by chatViewModel.streaming.collectAsStateWithLifecycle()
    val model by chatViewModel.selectedModel.collectAsStateWithLifecycle()
    val modelState by chatViewModel.modelState.collectAsStateWithLifecycle()
    val attachments by chatViewModel.attachments.collectAsStateWithLifecycle()

    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ChatEvent.Export?>(null) }
    var followOutput by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isStreaming = streaming != null

    // ---- Storage Access Framework launchers -------------------------------------------
    val exportMarkdown = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mimeType)
    ) { uri ->
        val export = pendingExport
        if (uri != null && export != null) chatViewModel.writeExport(uri, export.content)
        pendingExport = null
    }
    val exportText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.TEXT.mimeType)
    ) { uri ->
        val export = pendingExport
        if (uri != null && export != null) chatViewModel.writeExport(uri, export.content)
        pendingExport = null
    }
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> uris.forEach { chatViewModel.attachFile(it) } }

    // ---- One-shot events ----------------------------------------------------------------
    LaunchedEffect(Unit) {
        chatViewModel.events.collect { event ->
            when (event) {
                ChatEvent.OpenModelPicker -> showPicker = true
                ChatEvent.OpenAttachPicker -> attachLauncher.launch(arrayOf("*/*"))
                ChatEvent.OpenHistory -> onOpenHistory()
                ChatEvent.OpenSettings -> onOpenSettings()
                is ChatEvent.Export -> {
                    pendingExport = event
                    when (event.format) {
                        ExportFormat.MARKDOWN -> exportMarkdown.launch(event.fileName)
                        ExportFormat.TEXT -> exportText.launch(event.fileName)
                    }
                }
                is ChatEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Auto-scroll that follows the streaming output unless the user scrolls up --------
    LaunchedEffect(listState) {
        // Only *user* drags change follow mode; programmatic scrolls never do.
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> followOutput = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    followOutput = !listState.canScrollForward
                }
            }
        }
    }
    LaunchedEffect(listState) {
        // Re-enable following once the user flings back to the very bottom.
        snapshotFlow { listState.canScrollForward }.collect { canScroll ->
            if (!canScroll && !listState.isScrollInProgress) followOutput = true
        }
    }
    val lastLength = transcript.lastOrNull()?.let { it.text.length + (it.reasoning?.length ?: 0) } ?: 0
    LaunchedEffect(transcript.size, lastLength, followOutput) {
        if (followOutput && transcript.isNotEmpty()) {
            listState.scrollToItem(transcript.size) // the bottom anchor item
        }
    }

    val suggestions = SlashCommand.suggestions(input.text)

    fun insert(text: String) {
        val sel = input.selection
        val newText = input.text.replaceRange(sel.min, sel.max, text)
        input = TextFieldValue(newText, TextRange(sel.min + text.length))
    }

    fun send() {
        val text = input.text
        if (text.isBlank() && attachments.isEmpty()) return
        chatViewModel.submit(text)
        input = TextFieldValue("")
        followOutput = true
    }

    fun runSuggestion(cmd: SlashCommand) {
        chatViewModel.submit(cmd.command)
        input = TextFieldValue("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ---- Header ------------------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Term.Coral, fontWeight = FontWeight.Bold)) { append("✻ Claude Code") }
                        withStyle(SpanStyle(color = Term.TextDim)) { append(" (Mobile)") }
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "model: $model ▾",
                    color = Term.Blue,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            chatViewModel.refreshModels()
                            showPicker = true
                        }
                        .padding(vertical = 2.dp)
                )
            }
            TermButton("+new", color = Term.TextDim) { chatViewModel.newConversation() }
            TermButton("≡", color = Term.Text, onClick = onOpenHistory)
            TermButton("⚙", color = Term.Text, onClick = onOpenSettings)
        }
        DottedDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        // ---- Transcript --------------------------------------------------------------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            ) {
                if (transcript.isEmpty()) {
                    item(key = "welcome") { WelcomeBox(model) }
                }
                items(transcript, key = { it.key }) { item ->
                    MessageItem(item)
                }
                item(key = "bottom-anchor") { Spacer(Modifier.height(8.dp)) }
            }
            if (!followOutput && transcript.isNotEmpty()) {
                Text(
                    text = "[↓ jump to bottom]",
                    color = Term.Blue,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Term.Surface)
                        .clickable {
                            followOutput = true
                            scope.launch { listState.scrollToItem(transcript.size) }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // ---- Attachments ------------------------------------------------------------------
        if (attachments.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                attachments.forEach { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Term.Blue.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            "⎘ ${file.name} · ${formatSize(file.sizeBytes)}",
                            color = Term.Blue,
                            style = MaterialTheme.typography.labelSmall
                        )
                        TermButton("x", color = Term.TextDim) { chatViewModel.removeAttachment(file) }
                    }
                }
            }
        }

        // ---- Slash command suggestions -----------------------------------------------------
        SlashSuggestions(suggestions = suggestions, onPick = { runSuggestion(it) })

        // ---- Prompt input ------------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, if (isStreaming) Term.Border else Term.TextFaint, RoundedCornerShape(6.dp))
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                "> ",
                color = Term.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 9.dp)
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .padding(vertical = 8.dp)
            ) {
                if (input.text.isEmpty()) {
                    Text(
                        text = if (isStreaming) "Claude is responding… (■ to interrupt)" else "Try \"write a Kotlin function…\" or /help",
                        color = Term.TextFaint,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Text),
                    cursorBrush = SolidColor(Term.Coral),
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TermButton("+", color = Term.TextDim) { attachLauncher.launch(arrayOf("*/*")) }
            if (isStreaming && input.text.isBlank()) {
                SendKey("■", Term.Red) { chatViewModel.stop() }
            } else {
                SendKey("⏎", if (input.text.isNotBlank() || attachments.isNotEmpty()) Term.Coral else Term.TextFaint) { send() }
            }
        }

        // ---- Status line -------------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 4.dp)
        ) {
            Text(
                text = if (isStreaming) "⏵⏵ streaming · ■ to interrupt" else "⏵⏵ ? /help for commands",
                color = Term.TextFaint,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    modelState.loading -> "loading models…"
                    modelState.error != null -> "models: offline"
                    else -> "${modelState.models.size} free models"
                },
                color = Term.TextFaint,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // ---- Shortcut bar above the keyboard -----------------------------------------------
        ShortcutBar(onKey = { key ->
            if (key == "Tab") {
                val first = suggestions.firstOrNull()
                if (first != null) {
                    val completed = first.command + " "
                    input = TextFieldValue(completed, TextRange(completed.length))
                } else {
                    insert("    ")
                }
            } else {
                insert(key)
            }
        })
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
}

@Composable
private fun SendKey(symbol: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 2.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Term.SurfaceHigh)
            .clickable(onClick = onClick)
    ) {
        Text(symbol, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WelcomeBox(model: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Term.Coral, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Term.Coral)) { append("✻ ") }
                append("Welcome to ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Claude Code (Mobile)") }
                append("!")
            },
            color = Term.Text,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "  /help for help, /model to switch model\n  /attach to add a code file, /export to save",
            color = Term.TextDim,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Text("  model: ", color = Term.TextDim, style = MaterialTheme.typography.bodySmall)
            Text(model, color = Term.Blue, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(4.dp))
        Text("  api:   openrouter.ai (free models)", color = Term.TextDim, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Tips for getting started:\n  1. Ask Claude to write, explain or refactor code\n  2. Attach a file with [+] for context\n  3. Tap [copy] on any code block",
        color = Term.TextDim,
        style = MaterialTheme.typography.bodySmall
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
