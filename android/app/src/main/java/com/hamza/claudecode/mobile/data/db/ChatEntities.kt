package com.hamza.claudecode.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * role is one of [MessageRole] constant values.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversation_id")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val role: String,
    /** Full text sent to the model (includes attached file contents). */
    val content: String,
    /** What the user actually typed, shown in the UI (null = show [content]). */
    @ColumnInfo(name = "display_text") val displayText: String? = null,
    /** Optional: names of attached files (comma separated) for display. */
    val attachments: String? = null,
    /** Optional: model that produced an assistant message. */
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

object MessageRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    /** Echo of a slash command typed by the user. Never sent to the model. */
    const val COMMAND = "command"
    /** Local CLI output (e.g. /help). Never sent to the model. */
    const val LOCAL = "local"
    /** Error rendered in terminal style. Never sent to the model. */
    const val ERROR = "error"
}

data class ConversationSummary(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "message_count") val messageCount: Int
)
