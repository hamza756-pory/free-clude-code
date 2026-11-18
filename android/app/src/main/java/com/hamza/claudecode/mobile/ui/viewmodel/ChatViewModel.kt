package com.hamza.claudecode.mobile.ui.viewmodel

import com.hamza.claudecode.mobile.util.Glyph
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamza.claudecode.mobile.data.db.ConversationSummary
import com.hamza.claudecode.mobile.data.db.MessageEntity
import com.hamza.claudecode.mobile.data.db.MessageRole
import com.hamza.claudecode.mobile.data.network.FreeModel
import com.hamza.claudecode.mobile.data.network.OpenRouterClient
import com.hamza.claudecode.mobile.data.network.OpenRouterException
import com.hamza.claudecode.mobile.data.network.StreamEvent
import com.hamza.claudecode.mobile.data.repository.ChatRepository
import com.hamza.claudecode.mobile.data.repository.ModelCatalog
import com.hamza.claudecode.mobile.data.security.SecureKeyStore
import com.hamza.claudecode.mobile.domain.SlashCommand
import com.hamza.claudecode.mobile.ui.components.ModelListState
import com.hamza.claudecode.mobile.ui.components.TranscriptItem
import com.hamza.claudecode.mobile.util.Attachment
import com.hamza.claudecode.mobile.util.AttachmentReader
import com.hamza.claudecode.mobile.util.ConversationExporter
import com.hamza.claudecode.mobile.util.ExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One-shot UI events emitted by the chat. */
sealed interface ChatEvent {
    data object OpenModelPicker : ChatEvent
    data object OpenAttachPicker : ChatEvent
    data object OpenHistory : ChatEvent
    data object OpenSettings : ChatEvent
    data class Export(val format: ExportFormat, val fileName: String, val content: String) : ChatEvent
    data class Toast(val message: String) : ChatEvent
}

data class StreamingState(
    val text: String = "",
    val reasoning: String = "",
    val modelId: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val catalog: ModelCatalog
) : AndroidViewModel(application) {

    private val _conversationId = MutableStateFlow<Long?>(null)
    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()

    private val _streaming = MutableStateFlow<StreamingState?>(null)
    val streaming: StateFlow<StreamingState?> = _streaming.asStateFlow()

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    val selectedModel: StateFlow<String> = keyStore.selectedModel
        .map { it ?: OpenRouterClient.PREFERRED_CODING_MODELS.first() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, keyStore.selectedModel.value ?: OpenRouterClient.PREFERRED_CODING_MODELS.first())

    val modelState: StateFlow<ModelListState> = catalog.state

    val conversations: StateFlow<List<ConversationSummary>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messages: StateFlow<List<MessageEntity>> = _conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transcript: StateFlow<List<TranscriptItem>> = combine(messages, _streaming) { list, live ->
        val items = list.map { m ->
            TranscriptItem(
                key = "m${m.id}",
                role = m.role,
                text = m.displayText ?: m.content,
                attachments = m.attachments,
                modelId = m.modelId
            )
        }
        if (live != null) {
            items + TranscriptItem(
                key = "streaming",
                role = MessageRole.ASSISTANT,
                text = live.text,
                modelId = live.modelId,
                streaming = true,
                reasoning = live.reasoning.ifBlank { null }
            )
        } else items
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var streamJob: Job? = null

    init {
        catalog.ensureLoaded()
        viewModelScope.launch {
            val latest = repository.latestConversation()
            _conversationId.value = latest?.id ?: repository.createConversation(selectedModel.value)
        }
    }

    val isBusy: Boolean get() = streamJob?.isActive == true

    // ------------------------------------------------------------------ input

    fun submit(rawInput: String) {
        val input = rawInput.trimEnd()
        if (input.isBlank() && _attachments.value.isEmpty()) return
        if (input.trimStart().startsWith("/") && !input.contains('\n')) {
            runCommand(input.trim())
            return
        }
        if (isBusy) {
            emit(ChatEvent.Toast("Wait for the current answer or tap ■ to interrupt"))
            return
        }
        viewModelScope.launch {
            val convId = ensureConversation()
            val files = _attachments.value
            _attachments.value = emptyList()
            val prompt = buildPrompt(input, files)
            repository.addMessage(
                conversationId = convId,
                role = MessageRole.USER,
                content = prompt,
                displayText = input.ifBlank { "(attached ${files.size} file${if (files.size == 1) "" else "s"})" },
                attachments = files.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
            )
            startStreaming(convId)
        }
    }

    fun stop() {
        streamJob?.cancel()
    }

    /** Re-sends the conversation (useful after a 429 / network error). */
    fun retry() {
        if (isBusy) return
        viewModelScope.launch { startStreaming(ensureConversation()) }
    }

    private fun buildPrompt(text: String, files: List<Attachment>): String {
        if (files.isEmpty()) return text
        return buildString {
            files.forEach { f ->
                append("File: ").append(f.name)
                if (f.truncated) append(" (truncated to ${AttachmentReader.MAX_BYTES / 1024} KB)")
                append("\n```").append(f.language).append('\n')
                append(f.content.trimEnd())
                append("\n```\n\n")
            }
            append(text.ifBlank { "Review the attached file(s): explain what they do and point out bugs or improvements." })
        }
    }

    // --------------------------------------------------------------- streaming

    private fun startStreaming(convId: Long) {
        val model = selectedModel.value
        streamJob = viewModelScope.launch {
            val context = repository.buildContext(convId, SYSTEM_PROMPT)
            val received = StringBuilder()
            val reasoning = StringBuilder()
            var networkDone = false
            var finishReason: String? = null
            var modelUsed: String = model
            _streaming.value = StreamingState(modelId = model)

            // Typewriter: reveals the received text a few characters per frame so the
            // output "types" like a real terminal even when chunks arrive in bursts.
            val typer = launch {
                var shown = 0
                while (true) {
                    val target = received.length
                    if (shown < target) {
                        val backlog = target - shown
                        val step = maxOf(1, backlog / 6)
                        shown = minOf(target, shown + step)
                        val visible = received.substring(0, shown)
                        _streaming.update { it?.copy(text = visible) }
                    } else if (networkDone) {
                        break
                    }
                    delay(16)
                }
            }

            try {
                client.streamChat(model, context).collect { event ->
                    when (event) {
                        is StreamEvent.Content -> received.append(event.text)
                        is StreamEvent.Reasoning -> {
                            reasoning.append(event.text)
                            val r = reasoning.toString()
                            _streaming.update { it?.copy(reasoning = r) }
                        }
                        is StreamEvent.Finished -> {
                            finishReason = event.finishReason
                            event.model?.let { modelUsed = it }
                        }
                    }
                }
                networkDone = true
                typer.join()

                if (received.isBlank()) {
                    persistAndRelease(
                        convId, MessageRole.ERROR,
                        OpenRouterException(
                            null, "Empty response",
                            "The model returned no content" + (finishReason?.let { " (finish_reason=$it)" } ?: "") + ".",
                            "Free models are sometimes overloaded. Retry or switch model with /model."
                        ).toTerminal(),
                        null
                    )
                } else {
                    var text = received.toString()
                    if (finishReason == "length") text += "\n\n_[output truncated: max tokens reached]_"
                    persistAndRelease(convId, MessageRole.ASSISTANT, text, modelUsed)
                }
            } catch (e: CancellationException) {
                networkDone = true
                typer.cancel()
                withContext(NonCancellable) {
                    val partial = received.toString()
                    if (partial.isNotBlank()) {
                        persistAndRelease(convId, MessageRole.ASSISTANT, "$partial\n\n_[interrupted by user]_", modelUsed)
                    } else {
                        persistAndRelease(convId, MessageRole.LOCAL, "Interrupted by user", null)
                    }
                }
                throw e
            } catch (e: Throwable) {
                networkDone = true
                typer.cancel()
                val err = OpenRouterException.fromThrowable(e)
                val partial = received.toString()
                if (partial.isNotBlank()) {
                    repository.addMessage(convId, MessageRole.ASSISTANT, partial, modelId = modelUsed)
                }
                persistAndRelease(convId, MessageRole.ERROR, err.toTerminal() + "\n  ${Glyph.ELBOW} model: $model", null)
            } finally {
                _streaming.value = null
            }
        }
    }

    /**
     * Saves a message and only hides the live streaming row once Room has
     * emitted the saved row, to avoid a one-frame flicker.
     */
    private suspend fun persistAndRelease(convId: Long, role: String, content: String, modelId: String?) {
        val id = repository.addMessage(convId, role, content, modelId = modelId)
        withTimeoutOrNull(1_500) { messages.first { list -> list.any { it.id == id } } }
        _streaming.value = null
    }

    // ---------------------------------------------------------------- commands

    private fun runCommand(input: String) {
        val parsed = SlashCommand.parse(input)
        viewModelScope.launch {
            val convId = ensureConversation()
            if (parsed == null) {
                repository.addMessage(convId, MessageRole.COMMAND, input)
                repository.addMessage(
                    convId, MessageRole.LOCAL,
                    "Unknown command: ${input.substringBefore(' ')}\nType /help to see available commands."
                )
                return@launch
            }
            val (cmd, args) = parsed
            when (cmd) {
                SlashCommand.HELP -> {
                    repository.addMessage(convId, MessageRole.COMMAND, input)
                    repository.addMessage(convId, MessageRole.LOCAL, SlashCommand.helpText())
                }
                SlashCommand.CLEAR -> {
                    if (isBusy) stop()
                    _attachments.value = emptyList()
                    repository.clearConversation(convId)
                    emit(ChatEvent.Toast("Conversation cleared"))
                }
                SlashCommand.MODEL -> {
                    if (args.isBlank()) {
                        catalog.ensureLoaded()
                        emit(ChatEvent.OpenModelPicker)
                    } else {
                        repository.addMessage(convId, MessageRole.COMMAND, input)
                        val known = catalog.state.value.models.any { it.id == args }
                        selectModelInternal(args, convId)
                        repository.addMessage(
                            convId, MessageRole.LOCAL,
                            "Model set to $args" + if (!known) "\n(warning: not found in the free model list — requests may fail)" else ""
                        )
                    }
                }
                SlashCommand.EXPORT -> {
                    val format = when (args.lowercase()) {
                        "txt", "text" -> ExportFormat.TEXT
                        else -> ExportFormat.MARKDOWN
                    }
                    val conv = repository.conversation(convId)
                    val msgs = repository.messages(convId)
                    if (msgs.none { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }) {
                        emit(ChatEvent.Toast("Nothing to export yet"))
                        return@launch
                    }
                    val title = conv?.title ?: ChatRepository.NEW_TITLE
                    emit(
                        ChatEvent.Export(
                            format = format,
                            fileName = ConversationExporter.fileName(title, format),
                            content = ConversationExporter.export(title, selectedModel.value, msgs, format)
                        )
                    )
                }
                SlashCommand.ATTACH -> emit(ChatEvent.OpenAttachPicker)
                SlashCommand.NEW -> newConversation()
                SlashCommand.HISTORY -> emit(ChatEvent.OpenHistory)
                SlashCommand.SETTINGS -> emit(ChatEvent.OpenSettings)
                SlashCommand.STATUS -> {
                    repository.addMessage(convId, MessageRole.COMMAND, input)
                    val msgs = repository.messages(convId)
                    val models = catalog.state.value
                    val status = buildString {
                        appendLine("Claude Code (Mobile) status")
                        appendLine("  API key      ${SecureKeyStore.mask(keyStore.getApiKey())}  (${keyStore.storageDescription()})")
                        appendLine("  Model        ${selectedModel.value}")
                        appendLine("  Free models  ${if (models.loading) "loading…" else models.models.size.toString()}")
                        appendLine("  Session      #$convId · ${msgs.count { it.role == MessageRole.USER }} prompts")
                        append("  Endpoint     ${OpenRouterClient.BASE_URL}/chat/completions")
                    }
                    repository.addMessage(convId, MessageRole.LOCAL, status)
                }
            }
        }
    }

    // ----------------------------------------------------------- conversations

    fun newConversation() {
        if (isBusy) stop()
        viewModelScope.launch {
            _attachments.value = emptyList()
            _conversationId.value = repository.createConversation(selectedModel.value)
        }
    }

    fun openConversation(id: Long) {
        if (isBusy) stop()
        _attachments.value = emptyList()
        _conversationId.value = id
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_conversationId.value == id) {
                val latest = repository.latestConversation()
                _conversationId.value = latest?.id ?: repository.createConversation(selectedModel.value)
            }
        }
    }

    fun deleteAllConversations() {
        if (isBusy) stop()
        viewModelScope.launch {
            repository.deleteAll()
            _conversationId.value = repository.createConversation(selectedModel.value)
        }
    }

    private suspend fun ensureConversation(): Long {
        _conversationId.value?.let { return it }
        val id = repository.latestConversation()?.id ?: repository.createConversation(selectedModel.value)
        _conversationId.value = id
        return id
    }

    // ------------------------------------------------------------------ models

    fun refreshModels() = catalog.refresh()

    fun selectModel(model: FreeModel) {
        viewModelScope.launch {
            val convId = ensureConversation()
            selectModelInternal(model.id, convId)
            repository.addMessage(convId, MessageRole.LOCAL, "Model set to ${model.id}")
        }
    }

    private suspend fun selectModelInternal(modelId: String, convId: Long) {
        keyStore.saveSelectedModel(modelId)
        repository.setModel(convId, modelId)
    }

    // ------------------------------------------------------------- attachments

    fun attachFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = AttachmentReader.read(getApplication(), uri)
                _attachments.update { current -> current.filterNot { it.name == file.name } + file }
                if (file.truncated) emit(ChatEvent.Toast("${file.name} truncated to ${AttachmentReader.MAX_BYTES / 1024} KB"))
            } catch (e: Exception) {
                val convId = ensureConversation()
                repository.addMessage(convId, MessageRole.ERROR, "Attach failed · ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        _attachments.update { list -> list.filterNot { it == attachment } }
    }

    // ------------------------------------------------------------------ export

    fun writeExport(uri: Uri, content: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open destination")
                }
                emit(ChatEvent.Toast("Exported ✓"))
            } catch (e: Exception) {
                val convId = ensureConversation()
                repository.addMessage(convId, MessageRole.ERROR, "Export failed · ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun emit(event: ChatEvent) {
        _events.tryEmit(event)
    }

    companion object {
        val SYSTEM_PROMPT = """
            You are Claude Code (Mobile), an expert software engineering assistant running inside a
            terminal-style Android app. The user is a developer working from a phone.
            Guidelines:
            - Be concise and direct, like a senior engineer in a terminal. Avoid filler.
            - Always put code in fenced Markdown blocks with a language tag (```kotlin, ```python, ...).
            - When files are attached, they appear as "File: <name>" followed by a code block; refer to them by name.
            - When editing code, show the complete updated snippet or a clear diff, then a short explanation.
            - Prefer correct, production-quality, secure code. Mention assumptions briefly.
        """.trimIndent()
    }
}
