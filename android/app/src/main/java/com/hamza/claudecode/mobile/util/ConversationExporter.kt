package com.hamza.claudecode.mobile.util

import com.hamza.claudecode.mobile.data.db.MessageEntity
import com.hamza.claudecode.mobile.data.db.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/markdown"),
    TEXT("txt", "text/plain")
}

object ConversationExporter {

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)

    fun fileName(title: String, format: ExportFormat): String {
        val safe = title.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "session" }
        return "claude-code-$safe-${fileStamp.format(Date())}.${format.extension}"
    }

    fun export(title: String, model: String, messages: List<MessageEntity>, format: ExportFormat): String =
        when (format) {
            ExportFormat.MARKDOWN -> toMarkdown(title, model, messages)
            ExportFormat.TEXT -> toText(title, model, messages)
        }

    private fun toMarkdown(title: String, model: String, messages: List<MessageEntity>): String = buildString {
        appendLine("# $title")
        appendLine()
        appendLine("- **App:** Claude Code (Mobile)")
        appendLine("- **Model:** `$model`")
        appendLine("- **Exported:** ${stamp.format(Date())}")
        appendLine()
        appendLine("---")
        appendLine()
        messages.forEach { m ->
            when (m.role) {
                MessageRole.USER -> {
                    appendLine("### > You")
                    if (!m.attachments.isNullOrBlank()) appendLine("_Attached: ${m.attachments}_\n")
                    appendLine(m.content)
                }
                MessageRole.ASSISTANT -> {
                    appendLine("### ⏺ Assistant" + (m.modelId?.let { " (`$it`)" } ?: ""))
                    appendLine(m.content)
                }
                MessageRole.ERROR -> {
                    appendLine("> **Error**")
                    m.content.lines().forEach { appendLine("> $it") }
                }
                else -> {
                    appendLine("```text")
                    appendLine(m.content)
                    appendLine("```")
                }
            }
            appendLine()
        }
    }

    private fun toText(title: String, model: String, messages: List<MessageEntity>): String = buildString {
        appendLine("Claude Code (Mobile) — $title")
        appendLine("model: $model   exported: ${stamp.format(Date())}")
        appendLine("".padEnd(60, '·'))
        appendLine()
        messages.forEach { m ->
            when (m.role) {
                MessageRole.USER -> {
                    if (!m.attachments.isNullOrBlank()) appendLine("[attached: ${m.attachments}]")
                    m.content.lines().forEachIndexed { i, line -> appendLine(if (i == 0) "> $line" else "  $line") }
                }
                MessageRole.ASSISTANT -> {
                    m.content.lines().forEachIndexed { i, line -> appendLine(if (i == 0) "⏺ $line" else "  $line") }
                }
                MessageRole.ERROR -> m.content.lines().forEach { appendLine("✗ $it") }
                else -> m.content.lines().forEach { appendLine("  ⎿ $it") }
            }
            appendLine()
        }
    }
}
