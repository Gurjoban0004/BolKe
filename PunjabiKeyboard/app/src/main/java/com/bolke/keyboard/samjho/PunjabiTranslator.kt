package com.bolke.keyboard.samjho

import com.bolke.keyboard.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Why a translation could not be produced, so the card can say something useful. */
enum class TranslationError { NO_KEY, NO_NETWORK, API_ERROR }

sealed class TranslationResult {
    data class Ok(val text: String) : TranslationResult()
    data class Failed(val error: TranslationError) : TranslationResult()
}

/**
 * Turns an English message into Gurmukhi Punjabi via Gemini.
 *
 * The surrounding messages are sent as context. That is the whole point of using a
 * model here rather than a phrase translator: "he said he'll come tomorrow" only
 * resolves if the model can see who "he" is.
 */
class PunjabiTranslator(private val prefs: PreferencesManager) {

    /** Access-ordered map that drops its least recently used entry past the cap. */
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>): Boolean =
            size > CACHE_ENTRIES
    }

    init {
        loadCache()
    }

    suspend fun translate(target: String, context: List<String>): TranslationResult {
        cache[target]?.let { return TranslationResult.Ok(it) }

        val key = prefs.geminiApiKey.trim()
        if (key.isEmpty()) return TranslationResult.Failed(TranslationError.NO_KEY)

        return when (val result = request(target, context, key)) {
            is TranslationResult.Ok -> {
                cache[target] = result.text
                saveCache()
                result
            }

            else -> result
        }
    }

    private suspend fun request(
        target: String,
        context: List<String>,
        apiKey: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            try {
                connection.outputStream.bufferedWriter().use {
                    it.write(requestBody(target, context).toString())
                }

                if (connection.responseCode !in 200..299) {
                    return@withContext TranslationResult.Failed(TranslationError.API_ERROR)
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val text = parseTranslation(body)
                if (text.isNullOrBlank()) {
                    TranslationResult.Failed(TranslationError.API_ERROR)
                } else {
                    TranslationResult.Ok(text)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            // No route, DNS failure, or timeout. All of it reads as "check the internet".
            TranslationResult.Failed(TranslationError.NO_NETWORK)
        } catch (_: Exception) {
            TranslationResult.Failed(TranslationError.API_ERROR)
        }
    }

    private fun requestBody(target: String, context: List<String>): JSONObject {
        val prompt = buildString {
            if (context.isNotEmpty()) {
                appendLine("Messages visible on screen, in order:")
                context.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("Translate this one message:")
            append(target)
        }

        return JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 512)
                    // 2.5 Flash thinks by default, which adds seconds to a tap-and-wait
                    // interaction. Nothing here needs reasoning.
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                }
            )
        }
    }

    private fun parseTranslation(body: String): String? = runCatching {
        JSONObject(body)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }.getOrNull()

    // region Cache

    private fun loadCache() {
        val stored = prefs.samjhoCache
        if (stored.isBlank()) return
        runCatching {
            val json = JSONObject(stored)
            for (key in json.keys()) {
                cache[key] = json.getString(key)
            }
        }
    }

    private fun saveCache() {
        val json = JSONObject()
        for ((key, value) in cache) {
            json.put(key, value)
        }
        prefs.samjhoCache = json.toString()
    }

    // endregion

    companion object {
        // Verify against ai.google.dev if translations start failing with API_ERROR.
        private const val MODEL = "gemini-2.5-flash"

        // ponytail: 200 entries, no expiry. It only pays off on re-reads; new messages
        // are new by definition. Revisit if the prefs blob gets uncomfortable.
        private const val CACHE_ENTRIES = 200

        private const val SYSTEM_PROMPT =
            "You translate messages for a Punjabi woman who reads Gurmukhi but not English. " +
                "Translate only the message you are asked to translate, into simple, natural " +
                "Punjabi in Gurmukhi script.\n" +
                "- Convey meaning and tone, never word by word.\n" +
                "- Use everyday spoken Punjabi, the way a family member would explain it.\n" +
                "- Use the surrounding messages only to understand context.\n" +
                "- Keep names, numbers, amounts, dates, OTPs and links exactly as they are.\n" +
                "- Output only the translation. No quotes, no explanation, no English."
    }
}
