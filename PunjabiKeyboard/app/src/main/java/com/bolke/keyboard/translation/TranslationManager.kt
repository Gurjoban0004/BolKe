package com.bolke.keyboard.translation

import com.bolke.keyboard.util.OutputMode

/** Processes recognized Gurmukhi according to the selected writing mode. */
class TranslationManager {
    private val languageService = LanguageServiceClient()

    suspend fun process(
        gurmukhiText: String,
        mode: OutputMode,
        isOfflineMode: Boolean = false
    ): String = when (mode) {
        OutputMode.PUNJABI -> gurmukhiText
        OutputMode.PUNGLISH -> if (isOfflineMode) {
            TransliterationHelper.transliterate(gurmukhiText)
        } else {
            when (val result = languageService.punglish(gurmukhiText)) {
                is LanguageResponse.Ok -> result.result.text
                is LanguageResponse.Failed -> TransliterationHelper.transliterate(gurmukhiText)
            }
        }
        // Kept for preference migration; English is no longer exposed in the UI.
        OutputMode.ENGLISH -> TransliterationHelper.transliterate(gurmukhiText)
    }
}
