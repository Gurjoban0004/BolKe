package com.bolke.keyboard.util

/**
 * Output modes for the voice keyboard.
 *
 * PUNJABI   → ਕੀ ਹਾਲ ਆ ਤੇਰਾ  (Gurmukhi script, as-is from speech recognition)
 * ENGLISH   → How are you       (Translated to English via Google Translate API)
 * PUNGLISH  → ki haal aa tera   (Romanized Punjabi — Gurmukhi transliterated to Latin)
 */
enum class OutputMode {
    PUNJABI,
    ENGLISH,
    PUNGLISH
}
