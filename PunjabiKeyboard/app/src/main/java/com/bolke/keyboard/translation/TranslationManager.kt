package com.bolke.keyboard.translation

import com.bolke.keyboard.util.OutputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Processes recognized Gurmukhi text based on the selected output mode.
 *
 * PUNJABI  → pass-through (returns Gurmukhi as-is)
 * PUNGLISH → transliterates via ICU (offline, instant)
 * ENGLISH  → translates via Google Cloud Translation API (needs internet + API key)
 */
class TranslationManager {

    /**
     * Process the recognized Gurmukhi text according to the output mode.
     *
     * @param gurmukhiText The text in Gurmukhi script from speech recognition
     * @param mode The desired output mode
     * @param apiKey Google Cloud Translation API key (only needed for ENGLISH mode)
     * @return Processed text in the requested format
     */
    suspend fun process(gurmukhiText: String, mode: OutputMode, apiKey: String = ""): String {
        return when (mode) {
            OutputMode.PUNJABI -> gurmukhiText
            OutputMode.PUNGLISH -> TransliterationHelper.transliterate(gurmukhiText)
            OutputMode.ENGLISH -> {
                if (apiKey.isBlank()) {
                    // Fall back to transliteration if no API key
                    TransliterationHelper.transliterate(gurmukhiText)
                } else {
                    translateToEnglish(gurmukhiText, apiKey)
                }
            }
        }
    }

    /**
     * Translate Punjabi text to English using Google Cloud Translation API v2.
     * Uses a simple GET request — acceptable for a personal sideloaded app.
     */
    private suspend fun translateToEnglish(text: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlString = "https://translation.googleapis.com/language/translate/v2" +
                        "?key=${URLEncoder.encode(apiKey, "UTF-8")}" +
                        "&q=$encodedText" +
                        "&source=pa" +
                        "&target=en" +
                        "&format=text"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Accept", "application/json")

                try {
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()

                        parseTranslationResponse(response)
                    } else {
                        // Read error stream for debugging
                        val errorReader = BufferedReader(
                            InputStreamReader(connection.errorStream ?: connection.inputStream)
                        )
                        val errorBody = errorReader.readText()
                        errorReader.close()

                        "Translation error ($responseCode)"
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // On any failure, fall back to transliteration
                TransliterationHelper.transliterate(text)
            }
        }

    /**
     * Parse the Google Translate API JSON response.
     * Response format: { "data": { "translations": [{ "translatedText": "..." }] } }
     */
    private fun parseTranslationResponse(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val translations = json
                .getJSONObject("data")
                .getJSONArray("translations")
            translations.getJSONObject(0).getString("translatedText")
        } catch (e: Exception) {
            "Translation parse error"
        }
    }
}
