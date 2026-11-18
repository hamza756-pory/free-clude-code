package com.hamza.claudecode.mobile.domain

/**
 * Claude Code style slash commands available on mobile.
 */
enum class SlashCommand(val command: String, val args: String, val description: String) {
    HELP("/help", "", "Show available commands"),
    CLEAR("/clear", "", "Clear the current conversation"),
    MODEL("/model", "[model-id]", "Pick another free model (or set it directly)"),
    EXPORT("/export", "[md|txt]", "Export the conversation as .md or .txt"),
    ATTACH("/attach", "", "Attach a code file from storage as context"),
    NEW("/new", "", "Start a new session"),
    HISTORY("/history", "", "Browse saved sessions"),
    SETTINGS("/settings", "", "API key, default model, data"),
    STATUS("/status", "", "Show key, model and session info");

    companion object {
        fun parse(input: String): Pair<SlashCommand, String>? {
            val trimmed = input.trim()
            if (!trimmed.startsWith("/")) return null
            val name = trimmed.substringBefore(' ').lowercase()
            val args = trimmed.substringAfter(' ', "").trim()
            val cmd = entries.firstOrNull { it.command == name } ?: return null
            return cmd to args
        }

        fun suggestions(input: String): List<SlashCommand> {
            if (!input.startsWith("/") || input.contains(' ') || input.contains('\n')) return emptyList()
            val prefix = input.lowercase()
            return entries.filter { it.command.startsWith(prefix) }
        }

        fun helpText(): String = buildString {
            appendLine("Claude Code (Mobile) — commands")
            appendLine()
            entries.forEach { cmd ->
                val left = (cmd.command + " " + cmd.args).trim()
                append("  ").append(left.padEnd(22)).append(cmd.description).appendLine()
            }
            appendLine()
            appendLine("Tips")
            appendLine("  • Type a message and press ⏎ to send. Responses stream live.")
            appendLine("  • Tap [+] or use /attach to add a code file to the context.")
            appendLine("  • Use the shortcut bar for / { } ( ) < > and Tab.")
            append("  • Tap [copy] above any code block to copy it.")
        }
    }
}
