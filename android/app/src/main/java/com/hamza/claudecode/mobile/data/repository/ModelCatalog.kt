package com.hamza.claudecode.mobile.data.repository

import com.hamza.claudecode.mobile.data.network.OpenRouterClient
import com.hamza.claudecode.mobile.data.network.OpenRouterException
import com.hamza.claudecode.mobile.data.security.SecureKeyStore
import com.hamza.claudecode.mobile.ui.components.ModelListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared, app-wide cache of the free models available on OpenRouter.
 */
class ModelCatalog(
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow(ModelListState())
    val state: StateFlow<ModelListState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.models.isEmpty() && !_state.value.loading) refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val models = client.fetchFreeModels()
                _state.value = ModelListState(models = models, loading = false, error = null)
                val current = keyStore.selectedModel.value
                if (current.isNullOrBlank() || models.none { it.id == current }) {
                    if (current.isNullOrBlank()) {
                        OpenRouterClient.pickDefault(models)?.let { keyStore.saveSelectedModel(it.id) }
                    }
                }
            } catch (e: Exception) {
                val err = OpenRouterException.fromThrowable(e)
                _state.update { it.copy(loading = false, error = "${err.title}: ${err.detail}") }
            }
        }
    }
}
