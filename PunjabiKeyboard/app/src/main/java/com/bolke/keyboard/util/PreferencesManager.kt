package com.bolke.keyboard.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app preferences stored in SharedPreferences.
 * Stores output mode, API key, and setup completion status.
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "bolke_prefs"
        private const val KEY_OUTPUT_MODE = "output_mode"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_KEYBOARD_SIZE = "keyboard_size"
        private const val KEY_AUTO_SEND = "auto_send"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Current output mode (defaults to PUNGLISH — romanized Punjabi) */
    var outputMode: OutputMode
        get() {
            val name = prefs.getString(KEY_OUTPUT_MODE, OutputMode.PUNGLISH.name)
            return try {
                OutputMode.valueOf(name ?: OutputMode.PUNGLISH.name)
            } catch (e: IllegalArgumentException) {
                OutputMode.PUNGLISH
            }
        }
        set(value) {
            prefs.edit().putString(KEY_OUTPUT_MODE, value.name).apply()
        }

    /** Google Cloud Translation API key (needed for English mode only) */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    /** Whether the first-launch setup wizard has been completed */
    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()
        }

    /** Keyboard size scale multiplier (default: 1.15f for Large) */
    var keyboardSize: Float
        get() = prefs.getFloat(KEY_KEYBOARD_SIZE, 1.15f)
        set(value) {
            prefs.edit().putFloat(KEY_KEYBOARD_SIZE, value).apply()
        }

    /** Whether to automatically send text after speaking (skips preview) */
    var isAutoSendEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SEND, value).apply()
        }
}
