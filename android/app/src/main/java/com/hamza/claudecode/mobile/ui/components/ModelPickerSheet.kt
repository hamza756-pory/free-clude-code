package com.hamza.claudecode.mobile.ui.components

import com.hamza.claudecode.mobile.util.Glyph
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hamza.claudecode.mobile.data.network.FreeModel
import com.hamza.claudecode.mobile.ui.theme.Term

data class ModelListState(
    val models: List<FreeModel> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    state: ModelListState,
    selectedId: String?,
    onSelect: (FreeModel) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Term.Background,
        contentColor = Term.Text,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Term.Border)
                    .height(4.dp)
                    .fillMaxWidth(0.12f)
            )
        }
    ) {
        ModelPickerContent(state, selectedId, onSelect, onRefresh)
    }
}

@Composable
fun ModelPickerContent(
    state: ModelListState,
    selectedId: String?,
    onSelect: (FreeModel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(state.models, query) {
        if (query.isBlank()) state.models
        else state.models.filter { it.id.contains(query, true) || it.name.contains(query, true) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Select model", style = MaterialTheme.typography.titleMedium, color = Term.Coral)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.loading) "[loading…]" else "[refresh]",
                style = MaterialTheme.typography.labelLarge,
                color = Term.Blue,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !state.loading) { onRefresh() }
                    .padding(6.dp)
            )
        }
        Text(
            text = "Free OpenRouter models (:free / price 0) · ${state.models.size} available",
            style = MaterialTheme.typography.labelSmall,
            color = Term.TextDim
        )
        Spacer(Modifier.height(8.dp))
        DottedDivider()
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Term.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text("${Glyph.SEARCH} ", color = Term.TextDim, style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("filter models…", color = Term.TextFaint, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Term.Text),
                    cursorBrush = SolidColor(Term.Coral),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (state.error != null) {
            Text("✗ ${state.error}", color = Term.Red, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        if (state.loading && state.models.isEmpty()) {
            ThinkingSpinner(label = "Fetching free models…", showHint = false)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filtered, key = { it.id }) { model ->
                val selected = model.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) Term.Surface else Term.Background)
                        .clickable { onSelect(model) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = if (selected) "❯ " else "  ",
                        color = Term.Blue,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = model.id,
                            color = if (selected) Term.Blue else Term.Text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        val ctx = model.contextLength?.let { formatContext(it) }
                        Text(
                            text = listOfNotNull(model.name.takeIf { it != model.id }, ctx?.let { "ctx $it" })
                                .joinToString(" · "),
                            color = Term.TextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun formatContext(tokens: Long): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}
