package com.radaralert.app.data.radar

import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/**
 * Busca o CSV de radares e o VERSION.txt (hash) direto do branch no GitHub
 * (raw.githubusercontent.com), e decide se precisa atualizar o cache local
 * comparando com o hash salvo em [prefs].
 */
class GitCsvFetcher(
    private val prefs: SharedPreferences,
    private val client: OkHttpClient = OkHttpClient(),
    private val csvUrl: String = DEFAULT_CSV_URL,
    private val versionUrl: String = DEFAULT_VERSION_URL
) {
    fun remoteVersionChanged(): Boolean {
        val remoteVersion = fetch(versionUrl)?.trim() ?: return false
        val localVersion = prefs.getString(KEY_VERSION, null)
        return remoteVersion != localVersion
    }

    fun fetchCsvAndMarkVersion(): String? {
        val csv = fetch(csvUrl) ?: return null
        val remoteVersion = fetch(versionUrl)?.trim() ?: sha256(csv)
        prefs.edit { putString(KEY_VERSION, remoteVersion) }
        return csv
    }

    private fun fetch(url: String): String? {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_VERSION = "radares_version"
        private const val DEFAULT_CSV_URL =
            "https://raw.githubusercontent.com/CmteInacio/RadarAlert/main/data/radares_rs_sc.csv"
        private const val DEFAULT_VERSION_URL =
            "https://raw.githubusercontent.com/CmteInacio/RadarAlert/main/data/VERSION.txt"
    }
}
