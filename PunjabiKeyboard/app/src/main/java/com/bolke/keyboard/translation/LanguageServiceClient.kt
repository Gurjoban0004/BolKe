package com.bolke.keyboard.translation

import com.bolke.keyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class LanguageResult(
    val text: String,
    val alternatives: List<String> = emptyList(),
    val confidence: Float? = null
)

enum class LanguageError { NOT_CONFIGURED, OFFLINE, TIMEOUT, UNSAFE, UNSUPPORTED, SERVICE }

sealed class LanguageResponse {
    data class Ok(val result: LanguageResult) : LanguageResponse()
    data class Failed(val error: LanguageError) : LanguageResponse()
}

/** Calls BolKe's private service. Provider credentials never ship in the app. */
class LanguageServiceClient {

    suspend fun punglish(gurmukhiText: String): LanguageResponse = request(
        "/v1/punglish",
        JSONObject().put("text", gurmukhiText).put("locale", "pa-IN"),
        "punglish"
    )

    suspend fun translate(englishText: String): LanguageResponse = request(
        "/v1/translate",
        JSONObject()
            .put("text", englishText)
            .put("target", "pa-Guru")
            .put("style", "natural-family"),
        "translation"
    )

    private suspend fun request(path: String, body: JSONObject, outputKey: String): LanguageResponse =
        withContext(Dispatchers.IO) {
            val base = BuildConfig.LANGUAGE_SERVICE_URL.trim().trimEnd('/')
            if (base.isBlank()) return@withContext LanguageResponse.Failed(LanguageError.NOT_CONFIGURED)

            try {
                val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
                    val responseBody = (if (connection.responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    })?.bufferedReader()?.use { it.readText() }.orEmpty()

                    if (connection.responseCode !in 200..299) {
                        return@withContext LanguageResponse.Failed(errorFrom(connection.responseCode, responseBody))
                    }

                    val json = JSONObject(responseBody)
                    val text = json.optString(outputKey).ifBlank { json.optString("text") }.trim()
                    if (text.isBlank()) return@withContext LanguageResponse.Failed(LanguageError.SERVICE)
                    LanguageResponse.Ok(
                        LanguageResult(
                            text,
                            json.optJSONArray("alternatives").toStrings(),
                            json.optDouble("confidence", Double.NaN)
                                .takeUnless { it.isNaN() }?.toFloat()
                        )
                    )
                } finally {
                    connection.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                LanguageResponse.Failed(LanguageError.TIMEOUT)
            } catch (_: java.io.IOException) {
                LanguageResponse.Failed(LanguageError.OFFLINE)
            } catch (_: Exception) {
                LanguageResponse.Failed(LanguageError.SERVICE)
            }
        }

    private fun errorFrom(status: Int, body: String): LanguageError {
        val code = runCatching { JSONObject(body).optString("code") }.getOrDefault("").lowercase()
        return when {
            code == "unsafe_input" -> LanguageError.UNSAFE
            code == "unsupported_text" || status == 422 -> LanguageError.UNSUPPORTED
            status == 408 || status == 504 -> LanguageError.TIMEOUT
            else -> LanguageError.SERVICE
        }
    }

    private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else buildList {
        for (i in 0 until length()) optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
    }
}
