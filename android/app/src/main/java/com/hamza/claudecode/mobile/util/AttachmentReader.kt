package com.hamza.claudecode.mobile.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class Attachment(
    val name: String,
    val content: String,
    val sizeBytes: Long,
    val truncated: Boolean
) {
    val language: String get() = languageForFile(name)
}

/**
 * Reads a text/code file picked through the Storage Access Framework.
 */
object AttachmentReader {

    const val MAX_BYTES = 200 * 1024

    suspend fun read(context: Context, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            buffer.copyOf(total)
        } ?: throw IOException("Cannot open $name")

        val truncated = bytes.size > MAX_BYTES
        val usable = if (truncated) bytes.copyOf(MAX_BYTES) else bytes
        if (looksBinary(usable)) {
            throw IOException("$name looks like a binary file. Only text/code files can be attached.")
        }
        Attachment(
            name = name,
            content = String(usable, Charsets.UTF_8),
            sizeBytes = if (size >= 0) size else bytes.size.toLong(),
            truncated = truncated
        )
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.take(8000)
        if (sample.isEmpty()) return false
        if (sample.any { it == 0.toByte() }) return true
        val control = sample.count { b ->
            val v = b.toInt() and 0xFF
            v < 0x09 || (v in 0x0E..0x1F)
        }
        return control > sample.size / 10
    }
}

fun languageForFile(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "js", "mjs", "cjs" -> "javascript"
    "jsx" -> "jsx"
    "ts" -> "typescript"
    "tsx" -> "tsx"
    "py" -> "python"
    "rb" -> "ruby"
    "go" -> "go"
    "rs" -> "rust"
    "c", "h" -> "c"
    "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
    "cs" -> "csharp"
    "swift" -> "swift"
    "dart" -> "dart"
    "php" -> "php"
    "sh", "bash", "zsh" -> "bash"
    "ps1" -> "powershell"
    "sql" -> "sql"
    "html", "htm" -> "html"
    "xml" -> "xml"
    "css" -> "css"
    "scss" -> "scss"
    "json" -> "json"
    "yaml", "yml" -> "yaml"
    "toml" -> "toml"
    "md" -> "markdown"
    "gradle" -> "groovy"
    else -> ""
}
