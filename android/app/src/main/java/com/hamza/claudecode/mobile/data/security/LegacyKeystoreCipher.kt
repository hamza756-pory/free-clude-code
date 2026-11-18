package com.hamza.claudecode.mobile.data.security

import android.content.Context
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/**
 * Extra sealing layer for Android 5.0 / 5.1 (API 21–22), applied to values *before*
 * they are written to EncryptedSharedPreferences.
 *
 * On those versions the Android Keystore cannot hold AES keys (that came in API 23),
 * so EncryptedSharedPreferences/Tink stores its keyset unprotected. This class adds
 * hardware-backed protection using the classic pattern:
 *   1. an RSA-2048 key pair generated *inside* the Android Keystore (never exportable),
 *   2. a random AES-256 key wrapped (encrypted) with that RSA public key,
 *   3. values encrypted with AES-256-GCM using the unwrapped AES key.
 */
@Suppress("DEPRECATION")
class LegacyKeystoreCipher(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = ByteArray(12).also { random.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey(), "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, 12)
        val body = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey(), "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun aesKey(): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(RSA_ALIAS)) {
            generateRsaKeyPair()
            prefs.edit().remove(WRAPPED_AES).apply()
        }
        val wrapped = prefs.getString(WRAPPED_AES, null)
        if (wrapped != null) {
            val privateKey = keyStore.getKey(RSA_ALIAS, null) as PrivateKey
            val rsa = Cipher.getInstance(RSA_MODE)
            rsa.init(Cipher.DECRYPT_MODE, privateKey)
            return rsa.doFinal(Base64.decode(wrapped, Base64.NO_WRAP))
        }
        val fresh = ByteArray(32).also { random.nextBytes(it) }
        val publicKey = keyStore.getCertificate(RSA_ALIAS).publicKey
        val rsa = Cipher.getInstance(RSA_MODE)
        rsa.init(Cipher.ENCRYPT_MODE, publicKey)
        val wrappedKey = Base64.encodeToString(rsa.doFinal(fresh), Base64.NO_WRAP)
        prefs.edit().putString(WRAPPED_AES, wrappedKey).apply()
        return fresh
    }

    private fun generateRsaKeyPair() {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = KeyPairGeneratorSpec.Builder(context)
            .setAlias(RSA_ALIAS)
            .setSubject(X500Principal("CN=$RSA_ALIAS"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(start.time)
            .setEndDate(end.time)
            .setKeySize(2048)
            .build()
        val generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA_ALIAS = "claude_code_mobile_rsa"
        private const val RSA_MODE = "RSA/ECB/PKCS1Padding"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val PREFS = "claude_code_legacy_keys"
        private const val WRAPPED_AES = "wrapped_aes_key"
    }
}
