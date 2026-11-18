package com.hamza.claudecode.mobile.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Stores the OpenRouter API key (and the preferred model) with
 * **AndroidX Security `EncryptedSharedPreferences`** on every supported Android version:
 *
 *  - Keys are encrypted with AES-256-SIV and values with AES-256-GCM.
 *  - Android 6.0+ (API 23+): the Tink keyset is protected by an AES-256-GCM [MasterKey]
 *    held in the Android Keystore.
 *  - Android 5.x (API 21–22): the Keystore cannot hold AES keys, so Tink keeps its keyset
 *    unprotected. To keep the API key protected by hardware-backed storage, each value is
 *    additionally sealed with [LegacyKeystoreCipher] (Keystore RSA key wrapping an AES-256
 *    key) *before* being written to EncryptedSharedPreferences.
 *
 * The key is never hard-coded, never written in plain text, and never leaves the device.
 * If EncryptedSharedPreferences cannot be opened at all (broken Keystore), the key is kept
 * in memory for the current session only, never on disk unencrypted.
 */
class SecureKeyStore(private val context: Context) {

    private val prefs: SharedPreferences? = openEncryptedPrefs()

    /** Extra Keystore-backed sealing layer, only on API 21–22. */
    private val legacySeal: LegacyKeystoreCipher? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) LegacyKeystoreCipher(context) else null

    /** Used only when EncryptedSharedPreferences is unavailable: nothing is persisted. */
    private val sessionOnly = mutableMapOf<String, String>()

    init {
        migrateFromOlderVersions()
    }

    private val _apiKey = MutableStateFlow(read(KEY_API)?.takeIf { it.isNotBlank() })
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(read(KEY_MODEL))
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    /** True when values are persisted in EncryptedSharedPreferences. */
    val isPersistent: Boolean get() = prefs != null

    fun getApiKey(): String? = _apiKey.value

    fun hasApiKey(): Boolean = !_apiKey.value.isNullOrBlank()

    fun saveApiKey(key: String) {
        val clean = key.trim()
        write(KEY_API, clean)
        _apiKey.value = clean
    }

    fun deleteApiKey() {
        remove(KEY_API)
        _apiKey.value = null
    }

    fun saveSelectedModel(modelId: String) {
        write(KEY_MODEL, modelId)
        _selectedModel.value = modelId
    }

    /** Human-readable description of the storage, shown in Settings. */
    fun storageDescription(): String = when {
        prefs == null -> "session only (Keystore unavailable)"
        legacySeal != null -> "EncryptedSharedPreferences + Keystore RSA"
        else -> "EncryptedSharedPreferences · AES-256"
    }

    // ------------------------------------------------------------------ storage

    private fun read(key: String): String? {
        val p = prefs ?: return sessionOnly[key]
        val stored = runCatching { p.getString(key, null) }.getOrNull() ?: return null
        return unseal(stored)
    }

    private fun write(key: String, value: String) {
        val p = prefs
        if (p == null) {
            sessionOnly[key] = value
            return
        }
        p.edit { putString(key, seal(value)) }
    }

    private fun remove(key: String) {
        sessionOnly.remove(key)
        prefs?.edit { remove(key) }
    }

    private fun seal(value: String): String {
        val cipher = legacySeal ?: return value
        // If the legacy Keystore misbehaves, the value is still protected by
        // EncryptedSharedPreferences' own AES-256-GCM encryption.
        return runCatching { SEALED_PREFIX + cipher.encrypt(value) }.getOrElse { value }
    }

    private fun unseal(stored: String): String? {
        if (!stored.startsWith(SEALED_PREFIX)) return stored
        val cipher = legacySeal ?: LegacyKeystoreCipher(context)
        return runCatching { cipher.decrypt(stored.removePrefix(SEALED_PREFIX)) }.getOrNull()
    }

    private fun openEncryptedPrefs(): SharedPreferences? {
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // The Keystore key can be lost (e.g. after a restore on a new device).
            // In that case the encrypted file is unreadable: wipe it and start fresh.
            Log.w(TAG, "EncryptedSharedPreferences unreadable, recreating", e)
            deletePrefsFile(context, FILE_NAME)
            try {
                createEncryptedPrefs()
            } catch (fatal: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences unavailable, key kept in memory only", fatal)
                null
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

    /**
     * Moves values written by earlier builds (v1.0.0 fallback file, v1.1.0 Android 5 file)
     * into EncryptedSharedPreferences and deletes the old files.
     */
    private fun migrateFromOlderVersions() {
        val target = prefs ?: return
        for (oldFile in listOf(OLD_FALLBACK_FILE, OLD_LEGACY_FILE)) {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (!File(dir, "$oldFile.xml").exists()) continue
            val old = context.getSharedPreferences(oldFile, Context.MODE_PRIVATE)
            for (key in listOf(KEY_API, KEY_MODEL)) {
                val raw = old.getString(key, null) ?: continue
                val value = when {
                    oldFile == OLD_FALLBACK_FILE -> raw
                    raw.startsWith(OLD_PLAIN_PREFIX) -> raw.removePrefix(OLD_PLAIN_PREFIX)
                    else -> runCatching { LegacyKeystoreCipher(context).decrypt(raw) }.getOrNull()
                } ?: continue
                if (runCatching { target.getString(key, null) }.getOrNull() == null) {
                    target.edit { putString(key, seal(value)) }
                }
            }
            deletePrefsFile(context, oldFile)
        }
    }

    companion object {
        private const val TAG = "SecureKeyStore"
        private const val FILE_NAME = "claude_code_secure_prefs"
        private const val KEY_API = "openrouter_api_key"
        private const val KEY_MODEL = "selected_model"
        private const val SEALED_PREFIX = "ks1:"

        // Files written by previous app versions (migrated, then deleted).
        private const val OLD_FALLBACK_FILE = "claude_code_secure_prefs_fallback"
        private const val OLD_LEGACY_FILE = "claude_code_secure_prefs_legacy"
        private const val OLD_PLAIN_PREFIX = "plain:"

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
