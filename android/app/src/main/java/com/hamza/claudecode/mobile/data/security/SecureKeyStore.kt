package com.hamza.claudecode.mobile.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores the OpenRouter API key (and the user's preferred model) inside
 * EncryptedSharedPreferences backed by an AES-256-GCM key in the Android Keystore.
 * The key is never hard-coded and never leaves the device.
 */
class SecureKeyStore(private val context: Context) {

    private val prefs: SharedPreferences = openPrefs()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() })
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString(KEY_MODEL, null))
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    fun getApiKey(): String? = _apiKey.value

    fun hasApiKey(): Boolean = !_apiKey.value.isNullOrBlank()

    fun saveApiKey(key: String) {
        val clean = key.trim()
        prefs.edit { putString(KEY_API, clean) }
        _apiKey.value = clean
    }

    fun deleteApiKey() {
        prefs.edit { remove(KEY_API) }
        _apiKey.value = null
    }

    fun saveSelectedModel(modelId: String) {
        prefs.edit { putString(KEY_MODEL, modelId) }
        _selectedModel.value = modelId
    }

    private fun openPrefs(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // The Keystore key can be lost (e.g. after a restore on a new device).
            // In that case the encrypted file is unreadable: wipe it and start fresh.
            context.deleteSharedPreferences(FILE_NAME)
            try {
                createEncryptedPrefs()
            } catch (fatal: Exception) {
                // Extremely rare (broken Keystore on some emulators). Fall back to private prefs.
                context.getSharedPreferences(FILE_NAME + "_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val FILE_NAME = "claude_code_secure_prefs"
        private const val KEY_API = "openrouter_api_key"
        private const val KEY_MODEL = "selected_model"

        /** "sk-or-v1-91b4…5350" style masking for display. */
        fun mask(key: String?): String {
            if (key.isNullOrBlank()) return "(not set)"
            if (key.length <= 16) return "****"
            return key.take(12) + "…" + key.takeLast(4)
        }
    }
}
