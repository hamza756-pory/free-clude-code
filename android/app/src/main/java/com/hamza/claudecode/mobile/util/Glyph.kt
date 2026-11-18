package com.hamza.claudecode.mobile.util

import android.os.Build

/**
 * Terminal glyphs used across the UI.
 *
 * Android 7.0+ ships fonts covering Claude Code's symbols (✻ ⏺ ⎿ ⏵ …).
 * Android 5.x / 6.x lack several of them (they would render as "tofu" boxes),
 * so on those versions we use look-alike characters that are bundled in
 * JetBrains Mono and therefore always render correctly.
 */
object Glyph {
    private val modern: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /** Claude asterisk (logo / sections). */
    val STAR: String = if (modern) "✻" else "✶"

    /** Assistant answer bullet. */
    val DOT: String = if (modern) "⏺" else "●"

    /** Tool-output elbow. */
    val ELBOW: String = if (modern) "⎿" else "└"

    /** Status-line play arrow. */
    val PLAY: String = if (modern) "⏵" else "▶"

    /** Dotted rule character. */
    val DOTS: String = if (modern) "‥" else "·"

    /** Search / filter prefix. */
    val SEARCH: String = if (modern) "⌕" else "»"

    /** Attached file marker. */
    val FILE: String = if (modern) "⎘" else "@"

    /** Settings button. */
    val GEAR: String = if (modern) "⚙" else "⋮"

    /** Thinking spinner frames. */
    val SPINNER: List<String> =
        if (modern) listOf("·", "✢", "✳", "✶", "✻", "✽", "✻", "✶", "✳", "✢")
        else listOf("·", "∙", "*", "✶", "⊛", "✶", "*", "∙")
}
