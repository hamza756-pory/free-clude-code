package com.hamza.claudecode.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hamza.claudecode.mobile.R

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

private fun mono(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 6) = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

/** Every text style in the app is monospace — it is a terminal after all. */
val TerminalTypography = Typography(
    displayLarge = mono(32, FontWeight.Bold),
    displayMedium = mono(28, FontWeight.Bold),
    displaySmall = mono(24, FontWeight.Bold),
    headlineLarge = mono(22, FontWeight.Bold),
    headlineMedium = mono(20, FontWeight.Bold),
    headlineSmall = mono(18, FontWeight.Bold),
    titleLarge = mono(17, FontWeight.Bold),
    titleMedium = mono(15, FontWeight.Bold),
    titleSmall = mono(14, FontWeight.Bold),
    bodyLarge = mono(14),
    bodyMedium = mono(13),
    bodySmall = mono(12),
    labelLarge = mono(13, FontWeight.Bold),
    labelMedium = mono(12),
    labelSmall = mono(11)
)
