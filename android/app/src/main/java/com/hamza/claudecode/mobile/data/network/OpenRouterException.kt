package com.hamza.claudecode.mobile.data.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Any failure talking to OpenRouter, already translated into a terminal-style message.
 */
class OpenRouterException(
    val httpCode: Int?,
    val title: String,
    val detail: String,
    val hint: String? = null,
    cause: Throwable? = null
) : IOException("$title: $detail", cause) {

    /** Multi-line message rendered in the chat like a CLI error. */
    fun toTerminal(): String = buildString {
        append("Error")
        if (httpCode != null) append(" $httpCode")
        append(" · ").append(title)
        append("\n  ⎿ ").append(detail)
        if (!hint.isNullOrBlank()) append("\n  ⎿ hint: ").append(hint)
    }

    companion object {

        fun fromHttp(code: Int, serverMessage: String?): OpenRouterException {
            val msg = serverMessage?.takeIf { it.isNotBlank() }
            return when (code) {
                400 -> OpenRouterException(
                    code, "Bad request",
                    msg ?: "The request was rejected by OpenRouter.",
                    "The model may not support this input. Try another one with /model."
                )
                401 -> OpenRouterException(
                    code, "Invalid API key",
                    msg ?: "OpenRouter rejected your API key (unauthorized).",
                    "Open settings (/settings) and paste a valid key from openrouter.ai/keys."
                )
                402 -> OpenRouterException(
                    code, "Insufficient credits",
                    msg ?: "Your account has no credits for this request.",
                    "Use a free model (ids ending in :free) via /model."
                )
                403 -> OpenRouterException(
                    code, "Forbidden / moderated",
                    msg ?: "The input was flagged or access to this model is denied.",
                    "Rephrase the request or switch model with /model."
                )
                404 -> OpenRouterException(
                    code, "Model not found",
                    msg ?: "This model is no longer available.",
                    "Refresh the free model list with /model and pick another one."
                )
                408 -> OpenRouterException(
                    code, "Request timeout",
                    msg ?: "The model took too long to answer.",
                    "Retry, or choose a faster free model."
                )
                429 -> OpenRouterException(
                    code, "Rate limit exceeded",
                    msg ?: "Too many requests for the free tier.",
                    "Free models allow ~20 requests/min and a limited daily quota. " +
                        "Wait a minute or switch to another :free model with /model."
                )
                502 -> OpenRouterException(
                    code, "Upstream provider error",
                    msg ?: "The model provider is down or returned an invalid response.",
                    "Retry in a moment or choose another model with /model."
                )
                503 -> OpenRouterException(
                    code, "No provider available",
                    msg ?: "No provider can currently serve this model.",
                    "Try another free model with /model."
                )
                else -> OpenRouterException(
                    code, "HTTP error",
                    msg ?: "Unexpected response from OpenRouter.",
                    null
                )
            }
        }

        fun fromThrowable(t: Throwable): OpenRouterException = when (t) {
            is OpenRouterException -> t
            is UnknownHostException -> OpenRouterException(
                null, "Network unreachable",
                "Could not resolve openrouter.ai.",
                "Check your internet connection.", t
            )
            is SocketTimeoutException -> OpenRouterException(
                null, "Timeout",
                "The connection to OpenRouter timed out.",
                "Free models can be slow under load. Retry or switch model.", t
            )
            is SSLException -> OpenRouterException(
                null, "TLS error",
                t.message ?: "Secure connection failed.",
                "Check the device date/time and network.", t
            )
            is IOException -> OpenRouterException(
                null, "Connection error",
                t.message ?: "I/O failure while talking to OpenRouter.",
                "Check your internet connection and retry.", t
            )
            else -> OpenRouterException(
                null, "Unexpected error",
                t.message ?: t.javaClass.simpleName,
                null, t
            )
        }
    }
}
