package com.bolke.keyboard.translation

import com.bolke.keyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls BolKe's private language service. The app never carries a provider API key;
 * deployment config supplies only the service URL.
 */
class LanguageServiceClient {

    suspend fun normalize(gurmukhiText: String): String? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.LANGUAGE_SERVICE_URL.trim()
        if (endpoint.isBlank()) return@withContext null

        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.bufferedWriter().use {
                    it.write(JSONObject().put("text", gurmukhiText).put("output", "punglish").toString())
                }
                if (connection.responseCode !in 200..299) return@withContext null
                connection.inputStream.bufferedReader().use { reader ->
                    JSONObject(reader.readText()).optString("punglish").trim().ifBlank { null }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
