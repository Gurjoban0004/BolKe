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

    /**
     * High-frequency Punjabi words whose ICU spelling is technically correct but not how
     * Punjabi speakers usually write them in Latin-script chats. These are deliberately
     * whole-word replacements, so names and neighbouring words are never altered.
     */
    private val conversationalWords = mapOf(
        "jihaadaa" to "jehda", "jihaaraa" to "jehda", "jihara" to "jehda",
        "jihaadii" to "jehdi", "jihaari" to "jehdi", "jihari" to "jehdi",
        "jihaade" to "jehde", "jihare" to "jehde",
        "kihaadaa" to "kehda", "kihaaraa" to "kehda", "kihara" to "kehda",
        "kihaadii" to "kehdi", "kihaari" to "kehdi", "kihari" to "kehdi",
        "kihaade" to "kehde", "kihare" to "kehde",
        "kivem" to "kiven", "kiwem" to "kiven", "tainoom" to "tainu", "tainoo" to "tainu",
        "mainoom" to "mainu", "mainoo" to "mainu", "aapanaa" to "aapa", "aipa" to "aapa", "aapam" to "aapa",
        "banaaiaa" to "banai aa", "banaiaa" to "banai aa",
        "doosare" to "doosre", "phona" to "phone", "vica" to "vich",
        "calaanaa" to "chalana", "calaana" to "chalana", "calana" to "chalana", "toom" to "tu", "too" to "tu",
        "dassaa" to "dass", "dasa" to "dass", "rihaa" to "reha", "rihi" to "rahi", "rahe" to "rahe",
        "taam" to "ta", "likhaa" to "likha", "likha" to "likha", "bhejanaa" to "bhejna", "bhejana" to "bhejna", "ihana" to "ihna",
        "ihanam" to "ihna", "maidama" to "madam", "jobana" to "jo bana",
        "thoda" to "thoda", "thode" to "thode", "thadi" to "thadi",
        "tuhaadaa" to "thada", "tuhada" to "thada", "tuhaade" to "thade", "tuhade" to "thade", "tuhaadii" to "thadi", "tuhadi" to "thadi",
        "kiu" to "kyu", "nahim" to "nahi", "han" to "haan",
        "hai" to "aa", "haim" to "aa", "hama" to "haan",
        "karana" to "karna", "karada" to "karda", "karadi" to "kardi",
        "karade" to "karde", "jaana" to "jana", "aana" to "auna",
        "milana" to "milna", "bolana" to "bolna"
    )

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

        // Apply familiar chat spellings only after all diacritics are removed. Sorting keeps
        // the longest phrases first should a future mapping contain overlapping entries.
        conversationalWords.entries.sortedByDescending { it.key.length }.forEach { (source, target) ->
            result = result.replace(Regex("\\b${Regex.escape(source)}\\b", RegexOption.IGNORE_CASE), target)
        }
        
        // Remove any remaining special Unicode characters that might slip through
        result = result.replace(Regex("[^\\p{ASCII}\\s]"), "")

        return result
    }
}
