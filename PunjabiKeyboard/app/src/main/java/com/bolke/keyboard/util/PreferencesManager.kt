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
        private const val KEY_QUICK_REPLIES = "quick_replies"
        private const val KEY_SLANG_MAPPINGS = "slang_mappings"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_QR_MODE = "qr_mode"
        private const val KEY_APP_SCALE = "app_scale"
        private const val KEY_DOUBLE_TAP_PERIOD = "double_tap_period"
        private const val KEY_AUTO_CAP = "auto_cap"
        private const val KEY_SAVED_PHRASES = "saved_phrases"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SAMJHO_BUBBLE = "samjho_bubble"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_SAMJHO_CACHE = "samjho_cache"

        private const val DEFAULT_QUICK_REPLIES = "ਕਿੱਥੇ ਆਗਿਆ?|kithe aagya?\nਮੈਂ ਚੱਲ ਪਈ!|mai chalpyi!\nਪੁੱਤ ਕਿੱਥੇ ਆਂ?|putt kithe aa?\nਹਾਂਜੀ|hanji\nਨਾਜੀ|naji\nਠੀਕ ਹੈ|thik hai\nਸਤਿ ਸ੍ਰੀ ਅਕਾਲ|sat sri akal\nਕੀ ਹਾਲ ਹੈ?|ki haal hai?"
        private const val DEFAULT_SLANG_MAPPINGS = "karo:kro\nchalo:chlo\nkarda:krda\nkardi:krdi\nkarde:krde\njaldi:jldi"
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

    /** Whether to insert voice text immediately (without a preview screen). */
    var isAutoSendEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SEND, value).apply()
        }

    /** Custom quick replies (newline-separated) */
    var quickReplies: String
        get() = prefs.getString(KEY_QUICK_REPLIES, DEFAULT_QUICK_REPLIES) ?: DEFAULT_QUICK_REPLIES
        set(value) {
            prefs.edit().putString(KEY_QUICK_REPLIES, value).apply()
        }

    /** Custom slang mappings (newline-separated target:replacement) */
    var slangMappings: String
        get() = prefs.getString(KEY_SLANG_MAPPINGS, DEFAULT_SLANG_MAPPINGS) ?: DEFAULT_SLANG_MAPPINGS
        set(value) {
            prefs.edit().putString(KEY_SLANG_MAPPINGS, value).apply()
        }

    /** User-approved Gurmukhi-to-Punglish wording, kept only on this device. */
    var savedPhrases: String
        get() = prefs.getString(KEY_SAVED_PHRASES, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SAVED_PHRASES, value).apply()
        }

    /** Whether to force offline mode, bypassing Translate API calls */
    var isOfflineMode: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()
        }

    /** How Quick Replies are formatted: FOLLOW_KEYBOARD, PUNGLISH, PUNJABI */
    var quickReplyMode: String
        get() = prefs.getString(KEY_QR_MODE, "FOLLOW_KEYBOARD") ?: "FOLLOW_KEYBOARD"
        set(value) {
            prefs.edit().putString(KEY_QR_MODE, value).apply()
        }

    /** Font and layout scale multiplier for the Settings App UI (default: 1.0f) */
    var appScale: Float
        get() = prefs.getFloat(KEY_APP_SCALE, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_APP_SCALE, value).apply()
        }

    /** Whether double-tapping space inserts a period and space */
    var isDoubleTapPeriodEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_TAP_PERIOD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DOUBLE_TAP_PERIOD, value).apply()
        }

    /** Whether to automatically toggle shift after punctuation */
    var isAutoCapitalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAP, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CAP, value).apply()
        }

    /** Gemini API key used by the Samjho translator */
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()
        }

    /**
     * Whether the Samjho bubble is shown. Independent of the accessibility permission
     * so the bubble can be hidden without going back into system settings.
     */
    var isSamjhoBubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAMJHO_BUBBLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SAMJHO_BUBBLE, value).apply()
        }

    /** Where the user parked the bubble. -1 means "not placed yet". */
    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, -1)
        set(value) {
            prefs.edit().putInt(KEY_BUBBLE_X, value).apply()
        }

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, -1)
        set(value) {
            prefs.edit().putInt(KEY_BUBBLE_Y, value).apply()
        }

    /** Past Samjho translations, as a JSON object of source text to Punjabi. */
    var samjhoCache: String
        get() = prefs.getString(KEY_SAMJHO_CACHE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SAMJHO_CACHE, value).apply()
        }
}
