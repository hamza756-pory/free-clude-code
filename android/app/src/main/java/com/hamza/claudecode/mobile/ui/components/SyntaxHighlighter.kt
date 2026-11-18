package com.hamza.claudecode.mobile.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.hamza.claudecode.mobile.ui.theme.Term

/**
 * Lightweight, dependency-free syntax highlighter (Tokyo-Night-like palette).
 * A single-pass tokenizer handles comments, strings, numbers, keywords,
 * types, annotations/decorators and function calls for most popular languages.
 */
object SyntaxHighlighter {

    private val KEYWORD = SpanStyle(color = Term.Purple)
    private val STRING = SpanStyle(color = Term.Green)
    private val NUMBER = SpanStyle(color = Term.Orange)
    private val COMMENT = SpanStyle(color = Term.TextFaint, fontStyle = FontStyle.Italic)
    private val TYPE = SpanStyle(color = Term.Cyan)
    private val FUNCTION = SpanStyle(color = Term.Blue)
    private val ANNOTATION = SpanStyle(color = Term.Yellow)
    private val LITERAL = SpanStyle(color = Term.Orange)
    private val PUNCT = SpanStyle(color = Color(0xFF89DDFF))
    private val TAG = SpanStyle(color = Term.Red)
    private val PLAIN = SpanStyle(color = Term.Text)

    private val KEYWORDS = setOf(
        // C-family / JVM / JS / TS
        "abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "continue",
        "default", "delete", "do", "else", "enum", "export", "extends", "final", "finally", "for",
        "from", "function", "if", "implements", "import", "in", "instanceof", "interface", "is",
        "let", "new", "of", "override", "package", "private", "protected", "public", "return",
        "sealed", "static", "super", "switch", "this", "throw", "throws", "try", "typeof", "var",
        "void", "while", "with", "yield", "goto", "volatile", "synchronized", "transient", "native",
        "struct", "union", "typedef", "extern", "register", "sizeof", "namespace", "using", "template",
        "typename", "virtual", "friend", "operator", "inline", "constexpr", "auto", "readonly",
        "declare", "type", "keyof", "satisfies", "module", "require",
        // Kotlin
        "fun", "val", "object", "data", "when", "companion", "internal", "open", "lateinit",
        "suspend", "inner", "out", "reified", "crossinline", "noinline", "init", "constructor",
        "by", "get", "set", "where", "typealias", "vararg", "tailrec", "operator", "infix", "expect", "actual",
        // Python / Ruby
        "def", "elif", "except", "lambda", "pass", "raise", "global", "nonlocal", "assert", "del",
        "not", "and", "or", "end", "begin", "rescue", "ensure", "unless", "until", "then", "elsif", "self",
        // Go / Rust / Swift / Dart
        "func", "go", "chan", "defer", "select", "map", "range", "fallthrough", "mut", "pub", "impl",
        "trait", "crate", "mod", "use", "match", "loop", "move", "ref", "dyn", "unsafe", "where",
        "guard", "protocol", "extension", "init", "deinit", "inout", "mutating", "required", "convenience",
        "late", "factory", "mixin", "part", "library", "show", "hide",
        // Shell / SQL (lowercase)
        "fi", "esac", "done", "echo", "export", "local", "select", "insert", "update", "into", "values",
        "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer", "on", "group",
        "by", "order", "having", "limit", "distinct", "union", "primary", "key", "references", "index"
    )

    private val LITERALS = setOf(
        "true", "false", "null", "nil", "None", "True", "False", "undefined", "NaN", "Infinity",
        "nullptr", "Unit", "it"
    )

    private val HASH_COMMENT_LANGS = setOf(
        "python", "py", "ruby", "rb", "bash", "sh", "shell", "zsh", "yaml", "yml", "toml",
        "perl", "r", "powershell", "ps1", "dockerfile", "makefile", "ini", "conf", "properties", "elixir"
    )

    private val SQL_LANGS = setOf("sql", "mysql", "postgresql", "sqlite")
    private val MARKUP_LANGS = setOf("html", "xml", "svg", "vue", "jsx", "tsx")

    fun highlight(code: String, language: String): AnnotatedString {
        val lang = language.lowercase().trim()
        val hashComments = lang in HASH_COMMENT_LANGS
        val slashComments = lang !in HASH_COMMENT_LANGS || lang == "php"
        val sql = lang in SQL_LANGS
        val markup = lang in MARKUP_LANGS
        val caseInsensitiveKw = sql

        return buildAnnotatedString {
            withStyle(PLAIN) {
                var i = 0
                val n = code.length
                while (i < n) {
                    val c = code[i]

                    // Block comments /* ... */
                    if (slashComments && c == '/' && i + 1 < n && code[i + 1] == '*') {
                        val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                        withStyle(COMMENT) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // Line comments //
                    if (slashComments && c == '/' && i + 1 < n && code[i + 1] == '/') {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(COMMENT) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // SQL / Lua line comments --
                    if (sql && c == '-' && i + 1 < n && code[i + 1] == '-') {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(COMMENT) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // Hash comments (but not shebang-less "#include" in C which is not in the hash set)
                    if (hashComments && c == '#') {
                        val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                        withStyle(COMMENT) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // HTML/XML comments
                    if (markup && code.startsWith("<!--", i)) {
                        val end = code.indexOf("-->", i + 4).let { if (it < 0) n else it + 3 }
                        withStyle(COMMENT) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // Triple-quoted strings
                    if ((c == '"' || c == '\'') && code.startsWith("$c$c$c", i)) {
                        val quote = "$c$c$c"
                        val end = code.indexOf(quote, i + 3).let { if (it < 0) n else it + 3 }
                        withStyle(STRING) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // Strings "..." '...' `...`
                    if (c == '"' || c == '\'' || c == '`') {
                        var j = i + 1
                        while (j < n) {
                            val d = code[j]
                            if (d == '\\') { j += 2; continue }
                            if (d == c) { j++; break }
                            if (d == '\n' && c != '`') break
                            j++
                        }
                        val end = minOf(j, n)
                        withStyle(STRING) { append(code, i, end) }
                        i = end
                        continue
                    }
                    // Annotations / decorators / preprocessor
                    if ((c == '@') && i + 1 < n && code[i + 1].isLetter()) {
                        var j = i + 1
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                        withStyle(ANNOTATION) { append(code, i, j) }
                        i = j
                        continue
                    }
                    if (c == '#' && !hashComments && i + 1 < n && code[i + 1].isLetter()) {
                        var j = i + 1
                        while (j < n && code[j].isLetter()) j++
                        withStyle(ANNOTATION) { append(code, i, j) }
                        i = j
                        continue
                    }
                    // Numbers
                    if (c.isDigit() && (i == 0 || !(code[i - 1].isLetterOrDigit() || code[i - 1] == '_'))) {
                        var j = i
                        if (c == '0' && i + 1 < n && (code[i + 1] == 'x' || code[i + 1] == 'X' || code[i + 1] == 'b')) j += 2
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                        withStyle(NUMBER) { append(code, i, j) }
                        i = j
                        continue
                    }
                    // Identifiers / keywords
                    if (c.isLetter() || c == '_' || c == '$') {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '$')) j++
                        val word = code.substring(i, j)
                        val kwWord = if (caseInsensitiveKw) word.lowercase() else word
                        val prevNonSpace = previousNonSpace(code, i)
                        val nextNonSpace = nextNonSpace(code, j)
                        val style = when {
                            markup && prevNonSpace == '<' || markup && prevNonSpace == '/' && i >= 2 && code[i - 2] == '<' -> TAG
                            word in LITERALS -> LITERAL
                            kwWord in KEYWORDS -> KEYWORD
                            nextNonSpace == '(' -> FUNCTION
                            word.first().isUpperCase() && word.length > 1 -> TYPE
                            else -> null
                        }
                        if (style != null) withStyle(style) { append(word) } else append(word)
                        i = j
                        continue
                    }
                    // Operators / punctuation
                    if (c in "{}[]()<>=+-*/%!&|^~?:;,.") {
                        withStyle(PUNCT) { append(c) }
                        i++
                        continue
                    }
                    append(c)
                    i++
                }
            }
        }
    }

    private fun previousNonSpace(s: String, from: Int): Char? {
        var k = from - 1
        while (k >= 0 && s[k] == ' ') k--
        return if (k >= 0) s[k] else null
    }

    private fun nextNonSpace(s: String, from: Int): Char? {
        var k = from
        while (k < s.length && s[k] == ' ') k++
        return if (k < s.length) s[k] else null
    }
}
