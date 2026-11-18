package com.hamza.claudecode.mobile.ui.viewmodel

import com.hamza.claudecode.mobile.util.Glyph
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamza.claudecode.mobile.data.network.OpenRouterClient
import com.hamza.claudecode.mobile.data.network.OpenRouterException
import com.hamza.claudecode.mobile.data.repository.ModelCatalog
import com.hamza.claudecode.mobile.data.security.SecureKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeyUiState(
    val validating: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val info: String? = null,
    /** True once a key was validated & stored in this flow. */
    val saved: Boolean = false,
    /** A key that failed for a non-auth reason (network) and can be saved anyway. */
    val unverifiedKey: String? = null
)

/**
 * Validates and stores the OpenRouter key (welcome / login + settings).
 */
class KeyViewModel(
    private val keyStore: SecureKeyStore,
    private val client: OpenRouterClient,
    private val catalog: ModelCatalog
) : ViewModel() {

    private val _state = MutableStateFlow(KeyUiState())
    val state: StateFlow<KeyUiState> = _state.asStateFlow()

    val apiKey: StateFlow<String?> = keyStore.apiKey

    fun submit(rawKey: String) {
        val key = rawKey.trim().replace("\\s".toRegex(), "")
        when {
            key.isEmpty() -> {
                _state.value = KeyUiState(error = "API key is empty. Paste a key from openrouter.ai/keys")
                return
            }
            !key.startsWith("sk-or-") -> {
                _state.value = KeyUiState(error = "That does not look like an OpenRouter key (expected sk-or-v1-…)")
                return
            }
        }
        _state.value = KeyUiState(validating = true)
        viewModelScope.launch {
            try {
                val info = client.validateKey(key)
                keyStore.saveApiKey(key)
                catalog.refresh()
                val tier = when (info?.isFreeTier) {
                    true -> " · free tier"
                    false -> " · paid tier"
                    null -> ""
                }
                _state.value = KeyUiState(saved = true, info = "Key verified$tier. Stored encrypted on this device.")
            } catch (e: OpenRouterException) {
                if (e.httpCode == 401 || e.httpCode == 403) {
                    _state.value = KeyUiState(error = e.toTerminal())
                } else {
                    _state.value = KeyUiState(
                        warning = e.toTerminal() + "\n  ${Glyph.ELBOW} Could not verify the key. Save it anyway?",
                        unverifiedKey = key
                    )
                }
            }
        }
    }

    fun saveUnverified() {
        val key = _state.value.unverifiedKey ?: return
        keyStore.saveApiKey(key)
        catalog.refresh()
        _state.value = KeyUiState(saved = true, info = "Key saved (not verified). Stored encrypted on this device.")
    }

    fun deleteKey() {
        keyStore.deleteApiKey()
        _state.value = KeyUiState(info = "API key deleted from this device.")
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, warning = null, info = null, saved = false, unverifiedKey = null) }
    }

    fun maskedKey(): String = SecureKeyStore.mask(keyStore.getApiKey())

    companion object {
        const val KEYS_URL = "https://openrouter.ai/keys"
        val ENDPOINT = OpenRouterClient.BASE_URL
    }
}
