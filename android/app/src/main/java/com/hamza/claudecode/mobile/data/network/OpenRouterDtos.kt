package com.hamza.claudecode.mobile.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------- /api/v1/models ----------

@Serializable
data class ModelsResponse(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    val pricing: PricingDto? = null
)

@Serializable
data class PricingDto(
    val prompt: String? = null,
    val completion: String? = null
)

/** Domain model shown in the model picker. */
data class FreeModel(
    val id: String,
    val name: String,
    val contextLength: Long?,
    val description: String?
)

// ---------- /api/v1/chat/completions ----------

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChunkChoice> = emptyList(),
    val usage: UsageDto? = null,
    val error: ApiErrorBody? = null
)

@Serializable
data class ChunkChoice(
    val index: Int? = null,
    val delta: ChunkDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val error: ApiErrorBody? = null
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

// ---------- errors ----------

@Serializable
data class ErrorEnvelope(
    val error: ApiErrorBody? = null
)

@Serializable
data class ApiErrorBody(
    val code: JsonElement? = null,
    val message: String? = null,
    val metadata: JsonElement? = null
)

// ---------- /api/v1/key ----------

@Serializable
data class KeyInfoEnvelope(
    val data: KeyInfo? = null
)

@Serializable
data class KeyInfo(
    val label: String? = null,
    val usage: Double? = null,
    val limit: Double? = null,
    @SerialName("is_free_tier") val isFreeTier: Boolean? = null
)

/** Events emitted while a completion is streaming. */
sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class Finished(val finishReason: String?, val usage: UsageDto?, val model: String?) : StreamEvent
}
