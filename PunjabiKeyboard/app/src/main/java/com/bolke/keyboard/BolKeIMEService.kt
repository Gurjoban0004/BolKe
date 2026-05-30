package com.bolke.keyboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bolke.keyboard.speech.SpeechManager
import com.bolke.keyboard.translation.TranslationManager
import com.bolke.keyboard.util.OutputMode
import com.bolke.keyboard.util.PreferencesManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Core IME (Input Method Editor) service for BolKe Keyboard.
 * Handles the keyboard UI, character input, shift states, symbols, and coordinates voice typing.
 */
class BolKeIMEService : InputMethodService() {

    private enum class KeyboardState {
        KEYBOARD,
        RECORDING,
        PREVIEW
    }

    private lateinit var keyboardView: View
    private lateinit var modeSelector: TextView
    private lateinit var quickRepliesContainer: LinearLayout
    
    private lateinit var prefsManager: PreferencesManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val alphabeticKeys = ArrayList<TextView>()
    private var isShifted = false
    private var isSymbolsActive = false
    private var currentState = KeyboardState.KEYBOARD
    private var currentInputText = ""

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)

        prefsManager = PreferencesManager(this)
        speechManager = SpeechManager(this).apply {
            setCallback(createSpeechCallback())
        }
        translationManager = TranslationManager()

        // Adjust keyboard row heights programmatically based on user setting
        scaleKeyboardRows(prefsManager.keyboardSize)

        // Bind all the keyboard keys recursively
        alphabeticKeys.clear()
        setupKeys(keyboardView)

        // Bind top bar buttons
        quickRepliesContainer = keyboardView.findViewById(R.id.quick_replies_container)
        populateQuickReplies()

        /*
        val btnSizeMinus = keyboardView.findViewById<View>(R.id.btn_size_minus)
        val btnSizePlus = keyboardView.findViewById<View>(R.id.btn_size_plus)

        btnSizeMinus?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            adjustKeyboardSize(-0.05f)
        }

        btnSizePlus?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            adjustKeyboardSize(0.05f)
        }

        val btnSettings = keyboardView.findViewById<View>(R.id.btn_settings)
        btnSettings?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        */

        modeSelector = keyboardView.findViewById(R.id.mode_selector)
        updateModeSelectorText()
        modeSelector.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val nextMode = when (prefsManager.outputMode) {
                OutputMode.PUNGLISH -> OutputMode.PUNJABI
                OutputMode.PUNJABI -> OutputMode.ENGLISH
                OutputMode.ENGLISH -> OutputMode.PUNGLISH
            }
            prefsManager.outputMode = nextMode
            updateModeSelectorText()
        }

        // Bind voice preview actions
        val btnSend = keyboardView.findViewById<TextView>(R.id.btn_send)
        btnSend.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val connection = currentInputConnection
            if (connection != null && currentInputText.isNotEmpty()) {
                connection.commitText(currentInputText, 1)
                // Add a space after voice input for convenience
                connection.commitText(" ", 1)

                // Try performing the editor's default send/search/go/done action
                val editorInfo = currentInputEditorInfo
                val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH) {
                    connection.performEditorAction(actionId)
                }
            }
            currentState = KeyboardState.KEYBOARD
            updateUIState()
        }

        val btnRerecord = keyboardView.findViewById<TextView>(R.id.btn_rerecord)
        btnRerecord.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startVoiceInput()
        }

        val btnStopVoice = keyboardView.findViewById<TextView>(R.id.btn_stop_voice)
        btnStopVoice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            speechManager.stopListening()
        }

        // Set up mic button listener
        val micButton = keyboardView.findViewById<View>(R.id.mic_button)
        micButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startVoiceInput()
        }

        currentState = KeyboardState.KEYBOARD
        updateUIState()

        return keyboardView
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (currentState == KeyboardState.RECORDING) {
            speechManager.cancel()
        }
        currentState = KeyboardState.KEYBOARD
        updateUIState()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (currentState == KeyboardState.RECORDING) {
            speechManager.cancel()
        }
        currentState = KeyboardState.KEYBOARD
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        speechManager.destroy()
    }

    /**
     * Start the Punjabi voice input process.
     */
    private fun startVoiceInput() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            currentState = KeyboardState.RECORDING
            updateUIState()
            speechManager.startListening()
        } else {
            Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਮਾਈਕ ਦੀ ਇਜਾਜ਼ਤ ਦਿਓ। Setup ਐਪ ਖੋਲ੍ਹੋ।", Toast.LENGTH_LONG).show()
            // Launch SetupActivity to request permission
            val intent = Intent(this, SetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    /**
     * Create the listener callbacks for speech events.
     */
    private fun createSpeechCallback(): SpeechManager.SpeechCallback {
        return object : SpeechManager.SpeechCallback {
            override fun onReadyForSpeech() {
                val voiceStatus = keyboardView.findViewById<TextView>(R.id.voice_status)
                voiceStatus.text = getString(R.string.voice_listening)
            }

            override fun onPartialResult(text: String) {
                val partialTextView = keyboardView.findViewById<TextView>(R.id.partial_text)
                partialTextView.text = text
            }

            override fun onFinalResult(text: String) {
                val voiceStatus = keyboardView.findViewById<TextView>(R.id.voice_status)
                voiceStatus.text = getString(R.string.voice_processing)

                serviceScope.launch {
                    val processed = translationManager.process(
                        gurmukhiText = text,
                        mode = prefsManager.outputMode,
                        apiKey = prefsManager.apiKey,
                        isOfflineMode = prefsManager.isOfflineMode
                    )
                    
                    val slangApplied = if (prefsManager.outputMode == OutputMode.PUNGLISH) {
                        applySlangMappings(processed, prefsManager.slangMappings)
                    } else {
                        processed
                    }
                    val finalOutput = appendVoiceEmojis(slangApplied)

                    val connection = currentInputConnection
                    if (prefsManager.isAutoSendEnabled && connection != null && finalOutput.isNotEmpty()) {
                        // Direct Flow: Commit text, add space, and send
                        connection.commitText(finalOutput, 1)
                        connection.commitText(" ", 1)

                        val editorInfo = currentInputEditorInfo
                        val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                        if (actionId == EditorInfo.IME_ACTION_SEND ||
                            actionId == EditorInfo.IME_ACTION_GO ||
                            actionId == EditorInfo.IME_ACTION_DONE ||
                            actionId == EditorInfo.IME_ACTION_SEARCH) {
                            connection.performEditorAction(actionId)
                        } else {
                            // Fallback to sending Enter key event (works in WhatsApp)
                            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        }
                        
                        currentState = KeyboardState.KEYBOARD
                        updateUIState()
                    } else {
                        // Manual Flow: Show in preview bar
                        currentInputText = finalOutput
                        currentState = KeyboardState.PREVIEW
                        updateUIState()
                    }
                }
            }

            override fun onError(errorMessage: String) {
                val voiceStatus = keyboardView.findViewById<TextView>(R.id.voice_status)
                voiceStatus.text = errorMessage

                // Go back to keyboard view after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentState == KeyboardState.RECORDING) {
                        currentState = KeyboardState.KEYBOARD
                        updateUIState()
                    }
                }, 2000)
            }

            override fun onEndOfSpeech() {
                val voiceStatus = keyboardView.findViewById<TextView>(R.id.voice_status)
                voiceStatus.text = getString(R.string.voice_processing)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB ranges typically from -2 to 10. Let's normalize it to a scale factor between 1.0 and 1.5
                val scale = 1.0f + (rmsdB.coerceIn(0f, 10f) / 10f) * 0.5f
                val indicator = keyboardView.findViewById<View>(R.id.voice_recording_indicator)
                indicator?.animate()?.scaleX(scale)?.scaleY(scale)?.setDuration(50)?.start()
            }
        }
    }

    /**
     * Update the label showing the current mode in the top bar.
     */
    private fun updateModeSelectorText() {
        if (!::modeSelector.isInitialized) return
        val modeText = when (prefsManager.outputMode) {
            OutputMode.PUNGLISH -> "Punglish ▾"
            OutputMode.PUNJABI -> "ਪੰਜਾਬੀ ▾"
            OutputMode.ENGLISH -> "English ▾"
        }
        modeSelector.text = modeText
    }

    /**
     * Recursively traverses views to find keys starting with `key_` and bind their listeners.
     */
    private fun setupKeys(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupKeys(view.getChildAt(i))
            }
        } else if (view is TextView) {
            try {
                val idName = resources.getResourceEntryName(view.id)
                if (idName.startsWith("key_")) {
                    val keyText = view.text.toString()
                    when (idName) {
                        "key_shift" -> {
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                toggleShift()
                            }
                        }
                        "key_backspace", "key_backspace_num" -> {
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                handleBackspace()
                            }
                        }
                        "key_symbols" -> {
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                toggleSymbols()
                            }
                        }
                        "key_space" -> {
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                currentInputConnection?.commitText(" ", 1)
                            }
                        }
                        "key_enter" -> {
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                handleEnter()
                            }
                        }
                        else -> {
                            // Alphabetic and numeric/symbol keys
                            if (keyText.length == 1 && keyText[0].isLetter()) {
                                alphabeticKeys.add(view)
                            }
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val charToCommit = view.text.toString()
                                currentInputConnection?.commitText(charToCommit, 1)
                                if (isShifted) {
                                    toggleShift() // Auto-lowercase
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore resource ID errors for unnamed components
            }
        }
    }

    private fun handleBackspace() {
        val connection = currentInputConnection ?: return
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    private fun handleEnter() {
        val connection = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (actionId == EditorInfo.IME_ACTION_SEND ||
            actionId == EditorInfo.IME_ACTION_GO ||
            actionId == EditorInfo.IME_ACTION_DONE ||
            actionId == EditorInfo.IME_ACTION_SEARCH) {
            connection.performEditorAction(actionId)
        } else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun toggleShift() {
        isShifted = !isShifted
        for (keyView in alphabeticKeys) {
            val text = keyView.text.toString()
            keyView.text = if (isShifted) text.uppercase() else text.lowercase()
        }
        val btnShift = keyboardView.findViewById<TextView>(R.id.key_shift)
        btnShift.setTextColor(
            if (isShifted) ContextCompat.getColor(this, R.color.accent_blue)
            else ContextCompat.getColor(this, R.color.key_text)
        )
    }

    private fun toggleSymbols() {
        isSymbolsActive = !isSymbolsActive
        updateUIState()
    }

    /**
     * Set visibility of layouts based on current keyboard state.
     */
    private fun updateUIState() {
        if (!::keyboardView.isInitialized) return

        val previewArea = keyboardView.findViewById<View>(R.id.preview_area)
        val voiceOverlay = keyboardView.findViewById<View>(R.id.voice_overlay)

        val row1 = keyboardView.findViewById<View>(R.id.row1)
        val row2 = keyboardView.findViewById<View>(R.id.row2)
        val row3 = keyboardView.findViewById<View>(R.id.row3)
        val row4 = keyboardView.findViewById<View>(R.id.row4)

        val numbersRow1 = keyboardView.findViewById<View>(R.id.numbers_row1)
        val numbersRow2 = keyboardView.findViewById<View>(R.id.numbers_row2)
        val numbersRow3 = keyboardView.findViewById<View>(R.id.numbers_row3)

        // Hide all blocks initially
        previewArea.visibility = View.GONE
        voiceOverlay.visibility = View.GONE
        row1.visibility = View.GONE
        row2.visibility = View.GONE
        row3.visibility = View.GONE
        row4.visibility = View.GONE
        numbersRow1.visibility = View.GONE
        numbersRow2.visibility = View.GONE
        numbersRow3.visibility = View.GONE

        // Update the symbols toggle key text
        val btnSymbols = keyboardView.findViewById<TextView>(R.id.key_symbols)
        if (btnSymbols != null) {
            btnSymbols.text = if (isSymbolsActive) "abc" else "?123"
        }

        when (currentState) {
            KeyboardState.KEYBOARD -> {
                row4.visibility = View.VISIBLE
                if (isSymbolsActive) {
                    numbersRow1.visibility = View.VISIBLE
                    numbersRow2.visibility = View.VISIBLE
                    numbersRow3.visibility = View.VISIBLE
                } else {
                    row1.visibility = View.VISIBLE
                    row2.visibility = View.VISIBLE
                    row3.visibility = View.VISIBLE
                }
            }
            KeyboardState.RECORDING -> {
                voiceOverlay.visibility = View.VISIBLE
                val voiceStatus = keyboardView.findViewById<TextView>(R.id.voice_status)
                val partialTextView = keyboardView.findViewById<TextView>(R.id.partial_text)
                voiceStatus.text = getString(R.string.voice_prompt)
                partialTextView.text = ""
            }
            KeyboardState.PREVIEW -> {
                previewArea.visibility = View.VISIBLE
                val previewText = keyboardView.findViewById<TextView>(R.id.preview_text)
                previewText.text = currentInputText
                // In preview mode we only show the preview bar (Option A) to keep UI clean and simple.
            }
        }
    }

    private fun populateQuickReplies() {
        if (!::quickRepliesContainer.isInitialized) return
        quickRepliesContainer.removeAllViews()

        val repliesStr = prefsManager.quickReplies
        val list = repliesStr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        val density = resources.displayMetrics.density
        val paddingH = (12 * density).toInt()
        val paddingV = (6 * density).toInt()
        val marginStartEnd = (4 * density).toInt()

        for (replyLine in list) {
            val parts = replyLine.split("|")
            if (parts.size == 2) {
                val punjabiText = parts[0].trim()
                val punglishText = parts[1].trim()

                val displayFormat = prefsManager.quickReplyMode
                val displayText = when (displayFormat) {
                    "PUNJABI" -> punjabiText
                    "PUNGLISH" -> punglishText
                    else -> { // FOLLOW_KEYBOARD
                        if (prefsManager.outputMode == OutputMode.PUNGLISH) punglishText else punjabiText
                    }
                }

                val textView = TextView(this).apply {
                    text = displayText
                    setTextColor(ContextCompat.getColor(context, R.color.key_text))
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.mode_pill_bg)
                    setPadding(paddingH, paddingV, paddingH, paddingV)
                    
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(marginStartEnd, 0, marginStartEnd, 0)
                    }
                    layoutParams = params
                    isClickable = true
                    isFocusable = true
                    
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handleQuickReplyClick(punjabiText, punglishText)
                    }
                }
                quickRepliesContainer.addView(textView)
            }
        }
    }

    private fun handleQuickReplyClick(punjabiText: String, punglishText: String) {
        serviceScope.launch {
            val targetMode = when (prefsManager.quickReplyMode) {
                "PUNGLISH" -> OutputMode.PUNGLISH
                "PUNJABI" -> OutputMode.PUNJABI
                else -> prefsManager.outputMode
            }

            val finalOutput = when (targetMode) {
                OutputMode.PUNJABI -> punjabiText
                OutputMode.PUNGLISH -> applySlangMappings(punglishText, prefsManager.slangMappings)
                OutputMode.ENGLISH -> {
                    if (prefsManager.isOfflineMode || prefsManager.apiKey.isBlank()) {
                        applySlangMappings(punglishText, prefsManager.slangMappings)
                    } else {
                        translationManager.process(
                            gurmukhiText = punjabiText,
                            mode = OutputMode.ENGLISH,
                            apiKey = prefsManager.apiKey,
                            isOfflineMode = prefsManager.isOfflineMode
                        )
                    }
                }
            }
            val finalWithEmojis = appendVoiceEmojis(finalOutput)

            val connection = currentInputConnection
            if (connection != null && finalWithEmojis.isNotEmpty()) {
                connection.commitText(finalWithEmojis, 1)
                connection.commitText(" ", 1)

                if (prefsManager.isAutoSendEnabled) {
                    val editorInfo = currentInputEditorInfo
                    val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                    if (actionId == EditorInfo.IME_ACTION_SEND ||
                        actionId == EditorInfo.IME_ACTION_GO ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_SEARCH) {
                        connection.performEditorAction(actionId)
                    } else {
                        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                }
            }
        }
    }

    private fun applySlangMappings(text: String, mappingsStr: String): String {
        if (mappingsStr.isBlank()) return text
        var result = text
        val lines = mappingsStr.split("\n")
        for (line in lines) {
            val parts = line.split(":", "=")
            if (parts.size == 2) {
                val target = parts[0].trim()
                val replacement = parts[1].trim()
                if (target.isNotEmpty()) {
                    val regex = Regex("\\b${Regex.escape(target)}\\b", RegexOption.IGNORE_CASE)
                    result = result.replace(regex, replacement)
                }
            }
        }
        return result
    }

    private val emojiMappings = mapOf(
        // Gurmukhi keywords
        "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ" to "🙏",
        "ਵਾਹਿਗੁਰੂ" to "🙏",
        "ਪ੍ਰਣਾਮ" to "🙏",
        "namaste" to "🙏",
        "ਨਮਸਤੇ" to "🙏",
        "ਧੰਨਵਾਦ" to "🙏",
        "ਸ਼ੁਕਰੀਆ" to "🙏",
        "ਵਧਾਈ" to "🎉",
        "ਮੁਬਾਰਕ" to "🎉",
        "ਠੀਕ" to "👍",
        "ਵਧੀਆ" to "👍",
        "ਗੁੱਸਾ" to "😡",
        "ਪਿਆਰ" to "❤️",
        "ਹਾਹਾ" to "😂",
        "ਹੱਸ" to "😊",
        "ਖੁਸ਼" to "😊",
        "ਜਨਮਦਿਨ" to "🎂",

        // Punglish/English keywords
        "sat sri akal" to "🙏",
        "sat sri akaal" to "🙏",
        "waheguru" to "🙏",
        "pranam" to "🙏",
        "namaste" to "🙏",
        "dhanyavad" to "🙏",
        "thanks" to "🙏",
        "thank you" to "🙏",
        "shukriya" to "🙏",
        "vadhai" to "🎉",
        "mubarak" to "🎉",
        "congrats" to "🎉",
        "congratulations" to "🎉",
        "thik" to "👍",
        "theek" to "👍",
        "vadiya" to "👍",
        "gussa" to "😡",
        "angry" to "😡",
        "pyar" to "❤️",
        "love" to "❤️",
        "haha" to "😂",
        "happy" to "😊",
        "khush" to "😊",
        "birthday" to "🎂",
        "janamdin" to "🎂"
    )

    private fun appendVoiceEmojis(text: String): String {
        var emojiToAppend = ""
        val lowerText = text.lowercase()
        for ((keyword, emoji) in emojiMappings) {
            if (lowerText.contains(keyword)) {
                if (!emojiToAppend.contains(emoji)) {
                    emojiToAppend += " $emoji"
                }
            }
        }
        return if (emojiToAppend.isNotEmpty()) text + emojiToAppend else text
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Refresh quick replies and size dynamically when keyboard shows up
        populateQuickReplies()
        scaleKeyboardRows(prefsManager.keyboardSize)
    }

    private fun adjustKeyboardSize(delta: Float) {
        val currentSize = prefsManager.keyboardSize
        val newSize = (currentSize + delta).coerceIn(0.70f, 1.45f)
        prefsManager.keyboardSize = newSize
        scaleKeyboardRows(newSize)
    }

    private fun scaleKeyboardRows(multiplier: Float) {
        if (!::keyboardView.isInitialized) return
        val density = resources.displayMetrics.density
        val rowHeight = (46 * density * multiplier).toInt()
        val specialRowHeight = (52 * density * multiplier).toInt()

        keyboardView.findViewById<View>(R.id.row1)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.row2)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.row3)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.row4)?.layoutParams?.height = specialRowHeight
        keyboardView.findViewById<View>(R.id.numbers_row1)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.numbers_row2)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.numbers_row3)?.layoutParams?.height = rowHeight
        keyboardView.requestLayout()
    }
}
