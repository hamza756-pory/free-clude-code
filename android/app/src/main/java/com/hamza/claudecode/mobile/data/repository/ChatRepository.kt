package com.hamza.claudecode.mobile.data.repository

import com.hamza.claudecode.mobile.data.db.ChatDao
import com.hamza.claudecode.mobile.data.db.ConversationEntity
import com.hamza.claudecode.mobile.data.db.ConversationSummary
import com.hamza.claudecode.mobile.data.db.MessageEntity
import com.hamza.claudecode.mobile.data.db.MessageRole
import com.hamza.claudecode.mobile.data.network.ChatMessageDto
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {

    fun observeConversations(): Flow<List<ConversationSummary>> = dao.observeConversations()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> = dao.observeMessages(conversationId)

    suspend fun latestConversation(): ConversationEntity? = dao.getLatestConversation()

    suspend fun conversation(id: Long): ConversationEntity? = dao.getConversation(id)

    suspend fun createConversation(modelId: String): Long =
        dao.insertConversation(ConversationEntity(title = NEW_TITLE, modelId = modelId))

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        displayText: String? = null,
        attachments: String? = null,
        modelId: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                displayText = displayText,
                attachments = attachments,
                modelId = modelId,
                createdAt = now
            )
        )
        dao.touchConversation(conversationId, now)
        if (role == MessageRole.USER && dao.countUserMessages(conversationId) == 1) {
            dao.renameConversation(conversationId, makeTitle(displayText ?: content, attachments))
        }
        return id
    }

    suspend fun messages(conversationId: Long): List<MessageEntity> = dao.getMessages(conversationId)

    suspend fun clearConversation(conversationId: Long) {
        dao.clearMessages(conversationId)
        dao.renameConversation(conversationId, NEW_TITLE)
    }

    suspend fun setModel(conversationId: Long, modelId: String) = dao.setConversationModel(conversationId, modelId)

    suspend fun deleteConversation(id: Long) = dao.deleteConversation(id)

    suspend fun deleteAll() = dao.deleteAllConversations()

    /** Builds the context sent to the model: system prompt + user/assistant turns only. */
    suspend fun buildContext(conversationId: Long, systemPrompt: String, maxChars: Int = 60_000): List<ChatMessageDto> {
        val turns = dao.getMessages(conversationId)
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .map { ChatMessageDto(role = it.role, content = it.content) }

        // Keep the most recent turns within a rough character budget.
        val kept = ArrayDeque<ChatMessageDto>()
        var total = 0
        for (msg in turns.asReversed()) {
            if (kept.isNotEmpty() && total + msg.content.length > maxChars) break
            kept.addFirst(msg)
            total += msg.content.length
        }
        return listOf(ChatMessageDto("system", systemPrompt)) + kept
    }

    private fun makeTitle(content: String, attachments: String?): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val base = firstLine.ifBlank { attachments ?: NEW_TITLE }
        return if (base.length > 48) base.take(47) + "…" else base
    }

    companion object {
        const val NEW_TITLE = "New session"
    }
}
