package com.hamza.claudecode.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColors = darkColorScheme(
    primary = Term.Coral,
    onPrimary = Term.Background,
    primaryContainer = Term.CoralDim,
    onPrimaryContainer = Term.Text,
    secondary = Term.Blue,
    onSecondary = Term.Background,
    tertiary = Term.Green,
    background = Term.Background,
    onBackground = Term.Text,
    surface = Term.Background,
    onSurface = Term.Text,
    surfaceVariant = Term.Surface,
    onSurfaceVariant = Term.TextDim,
    surfaceContainer = Term.Surface,
    surfaceContainerHigh = Term.SurfaceHigh,
    surfaceContainerHighest = Term.SurfaceHigh,
    surfaceContainerLow = Term.Background,
    outline = Term.Border,
    outlineVariant = Term.Border,
    error = Term.Red,
    onError = Term.Background
)

@Composable
fun ClaudeCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColors,
        typography = TerminalTypography,
        content = content
    )
}
