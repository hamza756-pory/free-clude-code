package com.hamza.claudecode.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hamza.claudecode.mobile.data.db.MessageRole
import com.hamza.claudecode.mobile.ui.theme.Term
import com.hamza.claudecode.mobile.util.MarkdownParser
import com.hamza.claudecode.mobile.util.MdBlock
import kotlinx.coroutines.delay

/** UI model for one line-group in the terminal transcript. */
data class TranscriptItem(
    val key: String,
    val role: String,
    val text: String,
    val attachments: String? = null,
    val modelId: String? = null,
    val streaming: Boolean = false,
    val reasoning: String? = null
)

@Composable
fun MessageItem(item: TranscriptItem, modifier: Modifier = Modifier) {
    when (item.role) {
        MessageRole.USER, MessageRole.COMMAND -> UserLine(item, modifier)
        MessageRole.ASSISTANT -> AssistantBlock(item, modifier)
        MessageRole.ERROR -> ErrorBlock(item.text, modifier)
        else -> LocalBlock(item.text, modifier)
    }
}

@Composable
private fun UserLine(item: TranscriptItem, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Term.Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (!item.attachments.isNullOrBlank()) {
            Text(
                text = "⎘ " + item.attachments.split(",").joinToString("  ⎘ ") { it.trim() },
                style = MaterialTheme.typography.labelSmall,
                color = Term.Blue,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row {
            Text("> ", style = MaterialTheme.typography.bodyMedium, color = Term.TextDim)
            SelectionContainer {
                Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Term.Text)
            }
        }
    }
}

@Composable
private fun AssistantBlock(item: TranscriptItem, modifier: Modifier) {
    val blocks = remember(item.text) { MarkdownParser.parse(item.text) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (!item.reasoning.isNullOrBlank()) {
            ReasoningBlock(item.reasoning, item.streaming && item.text.isEmpty())
        }
        if (item.text.isEmpty() && item.streaming) {
            ThinkingSpinner()
            return@Column
        }
        Row {
            Text(
                "⏺ ",
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.streaming) Term.Coral else Term.Text
            )
            Column(modifier = Modifier.weight(1f)) {
                blocks.forEachIndexed { index, block ->
                    val isLast = index == blocks.lastIndex
                    when (block) {
                        is MdBlock.Prose -> {
                            SelectionContainer {
                                Text(
                                    text = renderInline(block.text, showCursor = item.streaming && isLast),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Term.Text
                                )
                            }
                        }
                        is MdBlock.Code -> CodeBlock(
                            code = block.code,
                            language = block.language,
                            streaming = item.streaming && !block.closed
                        )
                    }
                }
                if (item.streaming && (blocks.isEmpty() || blocks.last() is MdBlock.Code)) {
                    BlinkingCursor()
                }
                if (!item.streaming && !item.modelId.isNullOrBlank()) {
                    Text(
                        text = "  ⎿ ${item.modelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Term.TextFaint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String, active: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = (if (expanded) "▾ " else "▸ ") + if (active) "✻ Thinking…" else "✻ Thought for a while (tap to ${if (expanded) "hide" else "show"})",
            style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
            color = Term.TextDim,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp)
        )
        if (expanded) {
            Text(
                text = reasoning.trim(),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = Term.TextFaint,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun ErrorBlock(text: String, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("✗ ", style = MaterialTheme.typography.bodyMedium, color = Term.Red)
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Term.Red)
        }
    }
}

@Composable
private fun LocalBlock(text: String, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("  ⎿ ", style = MaterialTheme.typography.bodySmall, color = Term.TextFaint)
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodySmall, color = Term.TextDim)
        }
    }
}

private val SPINNER_FRAMES = listOf("·", "✢", "✳", "✶", "✻", "✽", "✻", "✶", "✳", "✢")
private val SPINNER_WORDS = listOf(
    "Thinking", "Pondering", "Computing", "Brewing", "Clauding", "Crafting", "Synthesizing", "Noodling"
)

@Composable
fun ThinkingSpinner(modifier: Modifier = Modifier, label: String? = null, showHint: Boolean = true) {
    var frame by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(120)
            frame = (frame + 1) % SPINNER_FRAMES.size
            ticks++
            if (ticks % 8 == 0) seconds++
        }
    }
    val word = label ?: (SPINNER_WORDS[(seconds / 3) % SPINNER_WORDS.size] + "…")
    Row(modifier = modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(SPINNER_FRAMES[frame], style = MaterialTheme.typography.bodyMedium, color = Term.Coral)
        Spacer(Modifier.width(8.dp))
        Text(word, style = MaterialTheme.typography.bodyMedium, color = Term.Coral)
        if (showHint) {
            Spacer(Modifier.width(8.dp))
            Text("(${seconds}s · tap ■ to interrupt)", style = MaterialTheme.typography.labelSmall, color = Term.TextFaint)
        }
    }
}

@Composable
fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Text("▌", color = Term.Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(alpha))
}

/**
 * Inline markdown: `code`, **bold**, *italic*, # headings, - bullets and [links](url).
 */
fun renderInline(text: String, showCursor: Boolean = false): AnnotatedString = buildAnnotatedString {
    val lines = text.split('\n')
    lines.forEachIndexed { lineIndex, rawLine ->
        var line = rawLine
        val heading = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line)
        if (heading != null) {
            withStyle(SpanStyle(color = Term.Coral, fontWeight = FontWeight.Bold)) {
                appendInlineSpans(heading.groupValues[2])
            }
        } else {
            val bullet = Regex("^(\\s*)([-*+])\\s+(.*)$").matchEntire(line)
            val numbered = Regex("^(\\s*)(\\d+\\.)\\s+(.*)$").matchEntire(line)
            when {
                bullet != null -> {
                    append(bullet.groupValues[1])
                    withStyle(SpanStyle(color = Term.Coral)) { append("• ") }
                    line = bullet.groupValues[3]
                    appendInlineSpans(line)
                }
                numbered != null -> {
                    append(numbered.groupValues[1])
                    withStyle(SpanStyle(color = Term.Coral)) { append(numbered.groupValues[2] + " ") }
                    appendInlineSpans(numbered.groupValues[3])
                }
                line.trim() == "---" || line.trim() == "***" -> {
                    withStyle(SpanStyle(color = Term.TextFaint)) { append("‥".repeat(24)) }
                }
                line.startsWith("> ") -> {
                    withStyle(SpanStyle(color = Term.TextDim, fontStyle = FontStyle.Italic)) {
                        append("│ ")
                        appendInlineSpans(line.removePrefix("> "))
                    }
                }
                else -> appendInlineSpans(line)
            }
        }
        if (lineIndex < lines.lastIndex) append('\n')
    }
    if (showCursor) {
        withStyle(SpanStyle(color = Term.Coral)) { append("▌") }
    }
}

private val INLINE = Regex("(`[^`\\n]+`)|(\\*\\*[^*\\n]+\\*\\*)|(__[^_\\n]+__)|(\\*[^*\\n]+\\*)|(\\[[^\\]\\n]+]\\([^)\\n]+\\))")

private fun AnnotatedString.Builder.appendInlineSpans(text: String) {
    var last = 0
    for (match in INLINE.findAll(text)) {
        if (match.range.first > last) append(text.substring(last, match.range.first))
        val token = match.value
        when {
            token.startsWith("`") -> withStyle(
                SpanStyle(color = Term.Blue, background = Term.SurfaceHigh)
            ) { append(token.trim('`')) }
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Term.Text)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("__"))
            }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.removeSurrounding("*"))
            }
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore(']')
                val url = token.substringAfter("](").removeSuffix(")")
                withStyle(SpanStyle(color = Term.Blue, textDecoration = TextDecoration.Underline)) { append(label) }
                withStyle(SpanStyle(color = Term.TextFaint)) { append(" ($url)") }
            }
            else -> append(token)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Start) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Term.TextDim)
    }
}
