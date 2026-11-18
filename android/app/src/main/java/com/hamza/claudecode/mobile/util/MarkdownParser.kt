package com.hamza.claudecode.mobile.util

/**
 * Splits a (possibly still streaming) markdown answer into prose and fenced code blocks.
 */
sealed interface MdBlock {
    data class Prose(val text: String) : MdBlock
    data class Code(val language: String, val code: String, val closed: Boolean) : MdBlock
}

object MarkdownParser {

    private val fence = Regex("^\\s*(```|~~~)\\s*([\\w#+.\\-]*)?.*$")

    fun parse(text: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val prose = StringBuilder()
        val code = StringBuilder()
        var inCode = false
        var fenceToken = "```"
        var language = ""

        fun flushProse() {
            if (prose.isNotEmpty()) {
                val t = prose.toString().trim('\n')
                if (t.isNotBlank()) blocks += MdBlock.Prose(t)
                prose.setLength(0)
            }
        }

        for (line in text.split('\n')) {
            val match = fence.matchEntire(line)
            if (!inCode && match != null) {
                flushProse()
                inCode = true
                fenceToken = match.groupValues[1]
                language = match.groupValues.getOrNull(2).orEmpty()
                code.setLength(0)
            } else if (inCode && line.trim() == fenceToken) {
                blocks += MdBlock.Code(language, code.toString().trimEnd('\n'), closed = true)
                inCode = false
                code.setLength(0)
            } else if (inCode) {
                code.append(line).append('\n')
            } else {
                prose.append(line).append('\n')
            }
        }
        if (inCode) {
            blocks += MdBlock.Code(language, code.toString().trimEnd('\n'), closed = false)
        } else {
            flushProse()
        }
        return blocks
    }
}
