package com.hamza.claudecode.mobile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestConversation(): ConversationEntity?

    @Query(
        """
        SELECT c.id, c.title, c.model_id, c.updated_at,
               (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS message_count
        FROM conversations c
        ORDER BY c.updated_at DESC
        """
    )
    fun observeConversations(): Flow<List<ConversationSummary>>

    @Query("UPDATE conversations SET updated_at = :time WHERE id = :id")
    suspend fun touchConversation(id: Long, time: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: Long, title: String)

    @Query("UPDATE conversations SET model_id = :modelId WHERE id = :id")
    suspend fun setConversationModel(id: Long, modelId: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId AND role = 'user'")
    suspend fun countUserMessages(conversationId: Long): Int

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun clearMessages(conversationId: Long)
}
