package com.hamza.claudecode.mobile.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenRouter client built on OkHttp:
 *  - GET  /api/v1/models            → list & filter free models
 *  - GET  /api/v1/key               → validate an API key
 *  - POST /api/v1/chat/completions  → streaming (SSE) chat completions
 */
class OpenRouterClient(
    private val context: Context,
    private val keyProvider: () -> String?
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Free models may "think" for a long time before the first token.
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply { LegacyTls.configure(context, this) }
        .build()

    private fun Request.Builder.openRouterHeaders(key: String?): Request.Builder {
        if (!key.isNullOrBlank()) header("Authorization", "Bearer $key")
        header("HTTP-Referer", APP_REFERER)
        header("X-Title", APP_TITLE)
        header("User-Agent", "ClaudeCodeMobile/1.0 (Android)")
        return this
    }

    private fun requireKey(explicit: String? = null): String {
        val key = explicit ?: keyProvider()
        if (key.isNullOrBlank()) {
            throw OpenRouterException(
                401, "No API key",
                "No OpenRouter API key is configured on this device.",
                "Open settings (/settings) and add your key."
            )
        }
        return key
    }

    /** Validates a key by calling /api/v1/key. Throws [OpenRouterException] on failure. */
    suspend fun validateKey(key: String): KeyInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/key")
            .get()
            .openRouterHeaders(key.trim())
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw OpenRouterException.fromHttp(response.code, parseErrorMessage(body))
                runCatching { json.decodeFromString(KeyInfoEnvelope.serializer(), body).data }.getOrNull()
            }
        } catch (t: Throwable) {
            throw OpenRouterException.fromThrowable(t)
        }
    }

    /** Fetches all models and keeps only the free ones, sorted with coding models first. */
    suspend fun fetchFreeModels(): List<FreeModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .get()
            .openRouterHeaders(keyProvider())
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw OpenRouterException.fromHttp(response.code, parseErrorMessage(body))
                val parsed = json.decodeFromString(ModelsResponse.serializer(), body)
                parsed.data
                    .filter { isFree(it) }
                    .map {
                        FreeModel(
                            id = it.id,
                            name = it.name ?: it.id,
                            contextLength = it.contextLength,
                            description = it.description
                        )
                    }
                    .distinctBy { it.id }
                    .sortedWith(compareBy<FreeModel> { -codingScore(it.id) }.thenBy { it.id })
            }
        } catch (t: Throwable) {
            throw OpenRouterException.fromThrowable(t)
        }
    }

    /**
     * Streams a chat completion. Emits [StreamEvent]s as Server-Sent Events arrive.
     * Cancelling the collector cancels the underlying HTTP call immediately.
     */
    fun streamChat(model: String, messages: List<ChatMessageDto>): Flow<StreamEvent> = callbackFlow {
        val key = try {
            requireKey()
        } catch (e: OpenRouterException) {
            close(e)
            awaitClose { }
            return@callbackFlow
        }

        val payload = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = model, messages = messages, stream = true)
        )

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .openRouterHeaders(key)
            .build()

        val call = http.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    close()
                } else {
                    close(OpenRouterException.fromThrowable(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = runCatching { resp.body?.string() }.getOrNull()
                        close(OpenRouterException.fromHttp(resp.code, parseErrorMessage(body)))
                        return
                    }
                    val source = resp.body?.source()
                    if (source == null) {
                        close(OpenRouterException(resp.code, "Empty response", "OpenRouter returned no body."))
                        return
                    }
                    var finishReason: String? = null
                    var usage: UsageDto? = null
                    var modelUsed: String? = null
                    try {
                        val dataBuffer = StringBuilder()
                        while (!call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                // Blank line terminates one SSE event.
                                line.isEmpty() -> {
                                    if (dataBuffer.isNotEmpty()) {
                                        val data = dataBuffer.toString()
                                        dataBuffer.setLength(0)
                                        if (data.trim() == "[DONE]") break
                                        val chunk = parseChunk(data) ?: continue
                                        chunk.error?.let { err ->
                                            throw buildStreamError(err)
                                        }
                                        if (chunk.model != null) modelUsed = chunk.model
                                        if (chunk.usage != null) usage = chunk.usage
                                        for (choice in chunk.choices) {
                                            choice.error?.let { err -> throw buildStreamError(err) }
                                            choice.delta?.reasoning?.takeIf { it.isNotEmpty() }?.let {
                                                trySend(StreamEvent.Reasoning(it))
                                            }
                                            choice.delta?.content?.takeIf { it.isNotEmpty() }?.let {
                                                trySend(StreamEvent.Content(it))
                                            }
                                            if (choice.finishReason != null) finishReason = choice.finishReason
                                        }
                                    }
                                }
                                // SSE comment, e.g. ": OPENROUTER PROCESSING" keep-alive.
                                line.startsWith(":") -> Unit
                                line.startsWith("data:") -> {
                                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                                    dataBuffer.append(line.removePrefix("data:").trimStart())
                                }
                                else -> Unit // "event:", "id:", "retry:" are ignored.
                            }
                        }
                        trySend(StreamEvent.Finished(finishReason, usage, modelUsed))
                        close()
                    } catch (e: OpenRouterException) {
                        close(e)
                    } catch (e: Throwable) {
                        if (call.isCanceled()) close() else close(OpenRouterException.fromThrowable(e))
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }

    private fun parseChunk(data: String): ChatChunk? =
        runCatching { json.decodeFromString(ChatChunk.serializer(), data) }.getOrNull()

    private fun buildStreamError(err: ApiErrorBody): OpenRouterException {
        val code = (err.code as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val raw = ((err.metadata as? JsonObject)?.get("raw") as? JsonPrimitive)?.contentOrNull
        val message = listOfNotNull(err.message, raw?.take(300)).joinToString(" — ")
        return if (code != null) {
            OpenRouterException.fromHttp(code, message)
        } else {
            OpenRouterException(null, "Stream error", message.ifBlank { "The provider aborted the stream." },
                "Retry or switch model with /model.")
        }
    }

    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val env = json.decodeFromString(ErrorEnvelope.serializer(), body)
            val err = env.error ?: return@runCatching null
            val raw = ((err.metadata as? JsonObject)?.get("raw") as? JsonPrimitive)?.contentOrNull
            listOfNotNull(err.message, raw?.take(300)).joinToString(" — ").ifBlank { null }
        }.getOrNull() ?: body.take(300)
    }

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val APP_REFERER = "https://github.com/hamza756-pory/free-clude-code"
        const val APP_TITLE = "Claude Code Mobile"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Preferred defaults for coding, in order. The first one available is used. */
        val PREFERRED_CODING_MODELS = listOf(
            "qwen/qwen3-coder:free",
            "deepseek/deepseek-chat-v3.1:free",
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-r1-0528:free",
            "mistralai/devstral-small:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemini-2.0-flash-exp:free"
        )

        fun isFree(model: ModelDto): Boolean {
            if (model.id.endsWith(":free")) return true
            val prompt = model.pricing?.prompt?.toDoubleOrNull()
            val completion = model.pricing?.completion?.toDoubleOrNull()
            // Router models such as openrouter/auto report "-1": exclude them.
            return prompt != null && prompt == 0.0 && (completion == null || completion == 0.0)
        }

        private fun codingScore(id: String): Int {
            val lower = id.lowercase()
            val preferredIndex = PREFERRED_CODING_MODELS.indexOf(id)
            return when {
                preferredIndex >= 0 -> 1000 - preferredIndex
                "coder" in lower || "code" in lower || "devstral" in lower -> 500
                "deepseek" in lower || "qwen" in lower -> 300
                "llama" in lower || "mistral" in lower || "gemini" in lower || "gpt" in lower -> 100
                else -> 0
            }
        }

        fun pickDefault(models: List<FreeModel>): FreeModel? =
            PREFERRED_CODING_MODELS.firstNotNullOfOrNull { pref -> models.firstOrNull { it.id == pref } }
                ?: models.firstOrNull()
    }
}
