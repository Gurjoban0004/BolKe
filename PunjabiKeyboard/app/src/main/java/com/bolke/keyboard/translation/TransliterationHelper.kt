package com.bolke.keyboard.translation

import android.icu.text.Transliterator
import java.text.Normalizer

/**
 * Transliterates Gurmukhi script to natural romanized Punjabi ("Punglish").
 *
 * Pipeline:
 *   ਕੀ ਹਾਲ ਆ ਤੇਰਾ
 *   → ICU Gurmukhi-Latin: "kī hāl ā tērā"
 *   → Vowel-aware diacritics removal: "kee haal aa teraa"
 *   → Cleanup: "kee haal aa teraa"
 *
 * Uses Android's built-in ICU library (no internet needed).
 */
object TransliterationHelper {

    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Gurmukhi-Latin")
    }

    fun transliterate(gurmukhiText: String): String {
        if (gurmukhiText.isBlank()) return gurmukhiText

        return try {
            // Step 1: ICU transliteration → produces academic romanization with diacritics
            val withDiacritics = transliterator.transliterate(gurmukhiText)

            // Step 2: Convert diacritics to natural spelling before stripping
            val natural = convertToNaturalSpelling(withDiacritics)

            // Step 3: Clean up whitespace
            natural.replace(Regex("\\s+"), " ").trim()
        } catch (e: Exception) {
            // Fallback to returning the original Punjabi Gurmukhi text if transliteration fails
            gurmukhiText
        }
    }

    /**
     * Convert ICU academic romanization to natural Punjabi texting style.
     * Handles long vowels, retroflex consonants, and other Punjabi-specific sounds.
     */
    private fun convertToNaturalSpelling(text: String): String {
        var result = text

        // === Colloquial Conversions (before diacritic stripping) ===
        // Common words that sound better in texting style
        result = result.replace("tuhāḍā", "thada")
        result = result.replace("tuhāḍe", "thade")
        result = result.replace("tuhāḍī", "thadi")
        result = result.replace("thōḍā", "thoda")
        result = result.replace("thōḍe", "thode")
        result = result.replace("thōḍī", "thodi")
        result = result.replace("kiuṃ", "kyu")
        result = result.replace("rihā", "rha")
        result = result.replace("rihī", "rhi")
        result = result.replace("rahē", "rhe")

        // === Long vowels → doubled letters (before stripping diacritics) ===
        // ā → aa (long a)
        result = result.replace("ā", "aa")
        result = result.replace("Ā", "Aa")

        // ī → ee (long i)
        result = result.replace("ī", "ee")
        result = result.replace("Ī", "Ee")

        // ū → oo (long u)
        result = result.replace("ū", "oo")
        result = result.replace("Ū", "Oo")

        // ē → e
        result = result.replace("ē", "e")
        result = result.replace("Ē", "E")

        // ō → o
        result = result.replace("ō", "o")
        result = result.replace("Ō", "O")

        // === Retroflex and special consonants ===
        result = result.replace("ṭ", "t")
        result = result.replace("Ṭ", "T")
        result = result.replace("ḍ", "d")
        result = result.replace("Ḍ", "D")
        result = result.replace("ṛ", "r")
        result = result.replace("Ṛ", "R")
        result = result.replace("ṇ", "n")
        result = result.replace("Ṇ", "N")
        result = result.replace("ṅ", "n")
        result = result.replace("Ṅ", "N")
        result = result.replace("ñ", "n")

        // === Sibilants ===
        result = result.replace("ś", "sh")
        result = result.replace("Ś", "Sh")
        result = result.replace("ṣ", "sh")
        result = result.replace("Ṣ", "Sh")

        // === Strip any remaining diacritics ===
        val normalized = Normalizer.normalize(result, Normalizer.Form.NFD)
        result = normalized.replace(Regex("[\\u0300-\\u036F]"), "")

        // === Final texting-style cleanups ===
        result = result.replace("eea", "iya") // kariya instead of kareea
        result = result.replace("vicha", "ch") // "ch" is common for "vich"
        
        // Remove any remaining special Unicode characters that might slip through
        result = result.replace(Regex("[^\\p{ASCII}\\s]"), "")

        return result
    }
}
