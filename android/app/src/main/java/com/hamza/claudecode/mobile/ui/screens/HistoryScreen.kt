package com.hamza.claudecode.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hamza.claudecode.mobile.data.db.ConversationSummary
import com.hamza.claudecode.mobile.ui.components.TermButton
import com.hamza.claudecode.mobile.ui.components.TerminalConfirmDialog
import com.hamza.claudecode.mobile.ui.components.TerminalHeader
import com.hamza.claudecode.mobile.ui.theme.Term
import com.hamza.claudecode.mobile.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val currentId by chatViewModel.conversationId.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    val format = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TerminalHeader(title = "Sessions", onBack = onBack) {
            TermButton("+ new", color = Term.Coral) {
                chatViewModel.newConversation()
                onBack()
            }
        }
        if (conversations.isEmpty()) {
            Text(
                "  ⎿ No saved sessions yet.",
                color = Term.TextDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
            items(conversations, key = { it.id }) { conv ->
                val isCurrent = conv.id == currentId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            chatViewModel.openConversation(conv.id)
                            onBack()
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Text(if (isCurrent) "❯ " else "  ", color = Term.Blue, style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.weight(1f)) {
                        Text(
                            conv.title,
                            color = if (isCurrent) Term.Blue else Term.Text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                        Text(
                            "${format.format(Date(conv.updatedAt))} · ${conv.messageCount} msgs · ${conv.modelId}",
                            color = Term.TextFaint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    TermButton("x", color = Term.Red) { toDelete = conv }
                }
            }
        }
    }

    toDelete?.let { conv ->
        TerminalConfirmDialog(
            title = "Delete session?",
            message = "\"${conv.title}\" and its ${conv.messageCount} messages will be removed from this device.",
            confirmLabel = "delete",
            onConfirm = {
                chatViewModel.deleteConversation(conv.id)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}
