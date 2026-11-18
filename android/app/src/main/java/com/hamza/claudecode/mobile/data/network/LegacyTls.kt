package com.hamza.claudecode.mobile.data.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.hamza.claudecode.mobile.R
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem

/**
 * Android 5.0 – 7.0 (API 21–24) ship an outdated set of root certificates: for example
 * "ISRG Root X1" (Let's Encrypt) only arrived in Android 7.1.1. HTTPS calls to modern
 * hosts can therefore fail with "Trust anchor for certification path not found".
 *
 * On those versions we trust the system roots PLUS the current Mozilla root store
 * (res/raw/extra_root_cas.pem, from certifi). Newer Android versions are untouched.
 */
object LegacyTls {

    private const val TAG = "LegacyTls"

    fun configure(context: Context, builder: OkHttpClient.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return
        try {
            val pem = context.resources.openRawResource(R.raw.extra_root_cas)
                .bufferedReader()
                .use { it.readText() }
            val certificates = Regex(
                "-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
                RegexOption.DOT_MATCHES_ALL
            ).findAll(pem).mapNotNull { match ->
                runCatching { match.value.decodeCertificatePem() }.getOrNull()
            }.toList()

            val handshake = HandshakeCertificates.Builder()
                .addPlatformTrustedCertificates()
                .apply { certificates.forEach { addTrustedCertificate(it) } }
                .build()
            builder.sslSocketFactory(handshake.sslSocketFactory(), handshake.trustManager)
        } catch (t: Throwable) {
            // Fall back to the platform defaults; errors will surface as TLS errors in the chat.
            Log.w(TAG, "Could not install extra root certificates", t)
        }
    }
}
