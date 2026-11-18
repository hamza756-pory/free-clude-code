package com.hamza.claudecode.mobile.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Stores the OpenRouter API key (and the preferred model) encrypted on the device.
 *
 *  - Android 6.0+ (API 23+): EncryptedSharedPreferences with an AES-256-GCM master key
 *    held in the Android Keystore.
 *  - Android 5.x (API 21–22): [LegacyKeystoreCipher] (Keystore RSA key wrapping an AES-256 key),
 *    because the Keystore cannot hold AES keys before API 23.
 *
 * The key is never hard-coded and never leaves the device.
 */
class SecureKeyStore(private val context: Context) {

    private val backend: Backend = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        EncryptedBackend(context)
    } else {
        LegacyBackend(context)
    }

    private val _apiKey = MutableStateFlow(backend.get(KEY_API)?.takeIf { it.isNotBlank() })
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(backend.get(KEY_MODEL))
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    fun getApiKey(): String? = _apiKey.value

    fun hasApiKey(): Boolean = !_apiKey.value.isNullOrBlank()

    fun saveApiKey(key: String) {
        val clean = key.trim()
        backend.put(KEY_API, clean)
        _apiKey.value = clean
    }

    fun deleteApiKey() {
        backend.remove(KEY_API)
        _apiKey.value = null
    }

    fun saveSelectedModel(modelId: String) {
        backend.put(KEY_MODEL, modelId)
        _selectedModel.value = modelId
    }

    // ------------------------------------------------------------------ backends

    private interface Backend {
        fun get(key: String): String?
        fun put(key: String, value: String)
        fun remove(key: String)
    }

    /** API 23+: androidx.security EncryptedSharedPreferences. */
    private class EncryptedBackend(private val context: Context) : Backend {
        private val prefs: SharedPreferences = open()

        private fun open(): SharedPreferences = try {
            create()
        } catch (e: Exception) {
            // The Keystore key can be lost (e.g. after a restore on a new device).
            // In that case the encrypted file is unreadable: wipe it and start fresh.
            deletePrefsFile(context, FILE_NAME)
            try {
                create()
            } catch (fatal: Exception) {
                // Extremely rare (broken Keystore on some devices): fall back to the legacy cipher.
                LegacyPrefsAdapter(context)
            }
        }

        private fun create(): SharedPreferences {
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

        override fun get(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()
        override fun put(key: String, value: String) = prefs.edit { putString(key, value) }
        override fun remove(key: String) = prefs.edit { remove(key) }
    }

    /** API 21–22: values encrypted with [LegacyKeystoreCipher]. */
    private class LegacyBackend(context: Context) : Backend {
        private val adapter = LegacyPrefsAdapter(context)
        override fun get(key: String): String? = adapter.getString(key, null)
        override fun put(key: String, value: String) = adapter.edit { putString(key, value) }
        override fun remove(key: String) = adapter.edit { remove(key) }
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

        /** Context.deleteSharedPreferences() only exists on API 24+. */
        internal fun deletePrefsFile(context: Context, name: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(name)
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                val dir = File(context.applicationInfo.dataDir, "shared_prefs")
                File(dir, "$name.xml").delete()
            }
        }
    }
}

/**
 * Minimal SharedPreferences facade whose string values are encrypted with
 * [LegacyKeystoreCipher]. Only the operations used by [SecureKeyStore] are supported.
 */
internal class LegacyPrefsAdapter(context: Context) : SharedPreferences {
    private val cipher = LegacyKeystoreCipher(context)
    private val prefs = context.getSharedPreferences("claude_code_secure_prefs_legacy", Context.MODE_PRIVATE)

    override fun getString(key: String?, defValue: String?): String? {
        val stored = prefs.getString(key, null) ?: return defValue
        if (stored.startsWith(PLAIN_PREFIX)) return stored.removePrefix(PLAIN_PREFIX)
        return runCatching { cipher.decrypt(stored) }.getOrDefault(defValue)
    }

    override fun edit(): SharedPreferences.Editor = Editor(prefs.edit())

    private inner class Editor(private val inner: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (value == null) {
                inner.remove(key)
            } else {
                // A few very old devices ship a broken Keystore; keep the app usable there.
                val stored = runCatching { cipher.encrypt(value) }.getOrElse { PLAIN_PREFIX + value }
                inner.putString(key, stored)
            }
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor { inner.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { inner.clear(); return this }
        override fun commit(): Boolean = inner.commit()
        override fun apply() = inner.apply()
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            throw UnsupportedOperationException()
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = throw UnsupportedOperationException()
    }

    private companion object {
        const val PLAIN_PREFIX = "plain:"
    }

    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = prefs.contains(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
