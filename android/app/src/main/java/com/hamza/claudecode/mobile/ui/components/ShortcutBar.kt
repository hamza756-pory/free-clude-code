package com.hamza.claudecode.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hamza.claudecode.mobile.domain.SlashCommand
import com.hamza.claudecode.mobile.ui.theme.Term

/** Keys shown above the soft keyboard. "Tab" is handled specially by the caller. */
val SHORTCUT_KEYS = listOf("Tab", "/", "{", "}", "(", ")", "<", ">", "[", "]", ";", "=", "\"", "`", "_", "|")

@Composable
fun ShortcutBar(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Term.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SHORTCUT_KEYS.forEach { key ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Term.SurfaceHigh)
                    .border(1.dp, Term.Border, RoundedCornerShape(5.dp))
                    .clickable { onKey(key) }
                    .widthIn(min = if (key == "Tab") 52.dp else 36.dp)
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = if (key == "Tab") "⇥ Tab" else key,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (key == "Tab") Term.Blue else Term.Text
                )
            }
        }
    }
}

@Composable
fun SlashSuggestions(
    suggestions: List<SlashCommand>,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Term.Background)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        suggestions.forEachIndexed { index, cmd ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPick(cmd) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (index == 0) "❯ " else "  ") + cmd.command.padEnd(11),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index == 0) Term.Blue else Term.Text
                )
                Text(
                    text = cmd.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Term.TextDim
                )
            }
        }
    }
}
