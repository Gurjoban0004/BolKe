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
 * PUNGLISH → transliterates via Smart Romanization (Online) or ICU (Offline)
 * ENGLISH  → translates via Smart Translation (Online) or Google Cloud API
 */
class TranslationManager {

    /**
     * Process the recognized Gurmukhi text according to the output mode.
     *
     * @param gurmukhiText The text in Gurmukhi script from speech recognition
     * @param mode The desired output mode
     * @param apiKey Google Cloud Translation API key (optional fallback for ENGLISH mode)
     * @return Processed text in the requested format
     */
    suspend fun process(gurmukhiText: String, mode: OutputMode, apiKey: String = "", isOfflineMode: Boolean = false): String {
        return when (mode) {
            OutputMode.PUNJABI -> gurmukhiText
            
            OutputMode.PUNGLISH -> {
                if (isOfflineMode) {
                    TransliterationHelper.transliterate(gurmukhiText)
                } else {
                    // Smart Romanization: Get high-quality Punglish from Google's internal API
                    smartTranslate(gurmukhiText, "en", includeTranslit = true)?.second 
                        ?: TransliterationHelper.transliterate(gurmukhiText)
                }
            }
            
            OutputMode.ENGLISH -> {
                if (isOfflineMode) {
                    return TransliterationHelper.transliterate(gurmukhiText)
                }
                
                // 1. Try Smart Translation (Magic Mode - No API Key needed)
                val smartResult = smartTranslate(gurmukhiText, "en", includeTranslit = false)?.first
                if (!smartResult.isNullOrBlank()) return smartResult
                
                // 2. Fallback to Official API if key is present
                if (apiKey.isNotBlank()) {
                    val officialResult = translateToEnglish(gurmukhiText, apiKey)
                    if (officialResult != "Translation error") return officialResult
                }
                
                // 3. Ultimate Fallback: Punglish
                TransliterationHelper.transliterate(gurmukhiText)
            }
        }
    }

    /**
     * Fetches "Magic" translation or romanization from Google's unofficial "single" endpoint
     * using the structured dj=1 format.
     * 
     * @return A Pair where first is Translation (English) and second is Romanization (Punglish)
     */
    private suspend fun smartTranslate(text: String, targetLang: String, includeTranslit: Boolean): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                // dj=1 provides a structured JSON with named keys (sentences, trans, translit)
                val urlString = "https://translate.googleapis.com/translate_a/single" +
                        "?client=gtx&sl=pa&tl=$targetLang&dt=t&dt=rm&dj=1&q=$encodedText"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.setRequestProperty("Accept", "application/json")

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { reader ->
                            parseSmartResponse(reader.readText())
                        }
                    } else null
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Parse the dj=1 JSON structure.
     * Format: { "sentences": [ { "trans": "...", "orig": "...", "translit": "...", "src_translit": "..." } ] }
     */
    private fun parseSmartResponse(jsonString: String): Pair<String, String>? {
        return try {
            val root = JSONObject(jsonString)
            val sentences = root.optJSONArray("sentences") ?: return null
            
            val translationSb = StringBuilder()
            val translitSb = StringBuilder()
            
            for (i in 0 until sentences.length()) {
                val s = sentences.optJSONObject(i)
                
                val trans = s.optString("trans")
                if (!trans.isNullOrBlank()) {
                    translationSb.append(trans)
                }
                
                // Prioritize src_translit for high-quality Punglish
                val srcTranslit = s.optString("src_translit")
                val translit = s.optString("translit")
                
                val bestTranslit = when {
                    !srcTranslit.isNullOrBlank() -> srcTranslit
                    !translit.isNullOrBlank() -> translit
                    else -> null
                }
                
                if (bestTranslit != null) {
                    if (translitSb.isNotEmpty()) translitSb.append(" ")
                    translitSb.append(bestTranslit)
                }
            }
            
            Pair(translationSb.toString().trim(), translitSb.toString().trim())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Official Fallback: Translate Punjabi text to English using Google Cloud Translation API v2.
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
                connection.connectTimeout = 4000
                connection.readTimeout = 4000

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { reader ->
                            val response = reader.readText()
                            val json = JSONObject(response)
                            json.getJSONObject("data")
                                .getJSONArray("translations")
                                .getJSONObject(0)
                                .getString("translatedText")
                        }
                    } else "Translation error"
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "Translation error"
            }
        }
}
