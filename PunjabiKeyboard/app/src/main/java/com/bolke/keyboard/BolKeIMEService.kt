package com.bolke.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
import com.bolke.keyboard.ui.VoiceRippleView
import com.bolke.keyboard.translation.TranslationManager
import com.bolke.keyboard.translation.TransliterationHelper
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
    private var isEmojiActive = false
    private var isNumericInput = false
    private var currentState = KeyboardState.KEYBOARD
    private var currentInputText = ""
    private var lastSpaceTime: Long = 0
    private var isToolbarExpanded = false
    private var lastVoiceText = ""
    private var lastVoiceSource = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideVoiceAction = Runnable { hideVoiceAction() }
    private var backspaceRepeat: Runnable? = null

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

        val toolbarToggle = keyboardView.findViewById<ImageView>(R.id.toolbar_toggle)
        val utilityButtons = keyboardView.findViewById<View>(R.id.utility_buttons)
        
        toolbarToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            isToolbarExpanded = !isToolbarExpanded
            utilityButtons.visibility = if (isToolbarExpanded) View.VISIBLE else View.GONE
            toolbarToggle.setImageResource(
                if (isToolbarExpanded) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right
            )
        }

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
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        modeSelector = keyboardView.findViewById(R.id.mode_selector)
        updateModeSelectorText()
        modeSelector.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val nextMode = when (prefsManager.outputMode) {
                OutputMode.PUNGLISH -> OutputMode.PUNJABI
                OutputMode.PUNJABI, OutputMode.ENGLISH -> OutputMode.PUNGLISH
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

        keyboardView.findViewById<TextView>(R.id.btn_undo_voice).setOnClickListener {
            undoLastVoiceInput()
        }
        keyboardView.findViewById<TextView>(R.id.btn_save_voice_phrase).setOnClickListener {
            saveLastVoicePhrase()
        }

        val btnStopVoice = keyboardView.findViewById<TextView>(R.id.btn_stop_voice)
        btnStopVoice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            speechManager.stopListening()
        }

        // Emoji drawer
        populateEmojiGrid()
        keyboardView.findViewById<TextView>(R.id.btn_emoji).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            isEmojiActive = !isEmojiActive
            updateUIState()
        }
        keyboardView.findViewById<TextView>(R.id.btn_emoji_abc).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            isEmojiActive = false
            updateUIState()
        }
        keyboardView.findViewById<TextView>(R.id.btn_emoji_space).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            currentInputConnection?.commitText(" ", 1)
        }
        bindBackspace(keyboardView.findViewById(R.id.btn_emoji_backspace))

        // Numeric keypad: 1-tap return to letters
        keyboardView.findViewById<TextView>(R.id.btn_numpad_abc).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            isNumericInput = false
            updateUIState()
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
        mainHandler.removeCallbacksAndMessages(null)
        speechManager.destroy()
    }

    /**
     * Start the Punjabi voice input process.
     */
    private fun startVoiceInput() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            hideVoiceAction()
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
                // Preview in the script the user will actually get
                partialTextView.text = if (prefsManager.outputMode == OutputMode.PUNJABI) {
                    text
                } else {
                    TransliterationHelper.transliterate(text)
                }
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
                    
                    val capitalized = if (isShifted && processed.isNotEmpty()) {
                        processed.substring(0, 1).uppercase() + processed.substring(1)
                    } else {
                        processed
                    }

                    val slangApplied = if (prefsManager.outputMode == OutputMode.PUNGLISH) {
                        applySlangMappings(applySavedPhrase(text, capitalized), prefsManager.slangMappings)
                    } else {
                        capitalized
                    }
                    val finalOutput = slangApplied

                    val connection = currentInputConnection
                    if (prefsManager.isAutoSendEnabled && connection != null && finalOutput.isNotEmpty()) {
                        // Voice typing inserts immediately but never sends a message on the
                        // user's behalf. This makes a short undo window safe and predictable.
                        connection.commitText(finalOutput, 1)
                        connection.commitText(" ", 1)
                        lastVoiceText = finalOutput
                        lastVoiceSource = text
                        showVoiceAction()
                        
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
                // rmsdB ranges typically from -2 to 10. Normalize to 0.0 - 1.0
                val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
                val indicator = keyboardView.findViewById<VoiceRippleView>(R.id.voice_recording_indicator)
                indicator?.setAmplitude(normalized)
            }
        }
    }

    /**
     * Update the label showing the current mode in the top bar.
     */
    private fun updateModeSelectorText() {
        if (!::modeSelector.isInitialized) return
        val modeText = when (prefsManager.outputMode) {
            OutputMode.PUNGLISH -> "Punglish"
            OutputMode.PUNJABI -> "ਪੰਜਾਬੀ"
            OutputMode.ENGLISH -> "Punglish"
        }
        modeSelector.text = modeText
    }

    private fun applySavedPhrase(source: String, generated: String): String {
        val saved = prefsManager.savedPhrases.lineSequence()
            .map { it.split("\t", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == source }
        return saved?.get(1) ?: generated
    }

    private fun saveLastVoicePhrase() {
        if (lastVoiceSource.isBlank() || lastVoiceText.isBlank()) return
        val retained = prefsManager.savedPhrases.lineSequence()
            .filter { it.substringBefore("\t") != lastVoiceSource }
            .toList()
        prefsManager.savedPhrases = (retained + "$lastVoiceSource\t$lastVoiceText").joinToString("\n")
        hideVoiceAction()
        Toast.makeText(this, "Saved on this device", Toast.LENGTH_SHORT).show()
    }

    private fun undoLastVoiceInput() {
        val connection = currentInputConnection ?: return
        val inserted = "$lastVoiceText "
        val beforeCursor = connection.getTextBeforeCursor(inserted.length, 0)?.toString()
        if (lastVoiceText.isNotBlank() && beforeCursor == inserted) {
            connection.deleteSurroundingText(inserted.length, 0)
        }
        hideVoiceAction()
    }

    private fun showVoiceAction() {
        val actionBar = keyboardView.findViewById<View>(R.id.voice_action_bar)
        actionBar.visibility = View.VISIBLE
        // One shared handler: removeCallbacks only cancels posts made by the same instance
        mainHandler.removeCallbacks(hideVoiceAction)
        mainHandler.postDelayed(hideVoiceAction, 6_000)
    }

    private fun hideVoiceAction() {
        if (::keyboardView.isInitialized) {
            keyboardView.findViewById<View>(R.id.voice_action_bar).visibility = View.GONE
        }
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
            if (view.id == View.NO_ID) return
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
                        "key_backspace", "key_backspace_num", "key_backspace_pad" -> {
                            bindBackspace(view)
                        }
                        "key_symbols" -> {
                            view.setOnClickListener {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                toggleSymbols()
                            }
                        }
                        "key_space" -> {
                            bindSpacebar(view)
                        }
                        "key_enter", "key_enter_pad" -> {
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

    /**
     * Multi-stage backspace: tap deletes a character, holding repeats characters,
     * and holding past 1.5s accelerates into whole-word deletion.
     */
    private fun bindBackspace(key: View) {
        key.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    handleBackspace()
                    val start = System.currentTimeMillis()
                    val repeat = object : Runnable {
                        override fun run() {
                            val heldFor = System.currentTimeMillis() - start
                            val wordMode = heldFor > 1500
                            if (wordMode) deleteWord() else handleBackspace()
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            mainHandler.postDelayed(this, if (wordMode) 60 else 50)
                        }
                    }
                    backspaceRepeat = repeat
                    mainHandler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    backspaceRepeat?.let { mainHandler.removeCallbacks(it) }
                    backspaceRepeat = null
                }
            }
            true
        }
    }

    /**
     * Spacebar doubles as a cursor trackpad: sliding horizontally steps the caret
     * one character per ~14dp travelled, while a plain tap still inserts a space.
     */
    private fun bindSpacebar(key: View) {
        val step = 14 * resources.displayMetrics.density
        var anchorX = 0f
        var scrubbed = false

        key.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    anchorX = event.rawX
                    scrubbed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val steps = ((event.rawX - anchorX) / step).toInt()
                    if (steps != 0) {
                        scrubbed = true
                        anchorX += steps * step
                        moveCursor(steps)
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (!scrubbed) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handleSpaceTap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            true
        }
    }

    private fun handleSpaceTap() {
        val connection = currentInputConnection ?: return
        val now = System.currentTimeMillis()

        if (prefsManager.isDoubleTapPeriodEnabled && now - lastSpaceTime < 400) {
            val textBefore = connection.getTextBeforeCursor(1, 0)
            if (textBefore != null && textBefore.isNotEmpty() && textBefore[0] == ' ') {
                connection.deleteSurroundingText(1, 0)
                connection.commitText(". ", 1)
                if (prefsManager.isAutoCapitalizationEnabled && !isShifted) {
                    toggleShift()
                }
                lastSpaceTime = 0 // Reset to prevent triple-tap period
                return
            }
        }

        connection.commitText(" ", 1)

        // Auto-cap after punctuation + space
        if (prefsManager.isAutoCapitalizationEnabled && !isShifted) {
            val textBefore = connection.getTextBeforeCursor(2, 0)
            if (textBefore != null && textBefore.length >= 2) {
                val lastChar = textBefore[0]
                if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
                    toggleShift()
                }
            }
        }

        lastSpaceTime = now
    }

    private fun moveCursor(steps: Int) {
        val connection = currentInputConnection ?: return
        val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun handleBackspace() {
        val connection = currentInputConnection ?: return
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    private fun deleteWord() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(64, 0)?.toString() ?: return
        if (before.isEmpty()) return
        connection.deleteSurroundingText(wordDeleteLength(before), 0)
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

        val numpadRows = numpadRowIds.map { keyboardView.findViewById<View>(it) }
        val emojiDrawer = keyboardView.findViewById<View>(R.id.emoji_drawer)

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
        numpadRows.forEach { it.visibility = View.GONE }
        emojiDrawer.visibility = View.GONE

        // The emoji toggle is meaningless in a number/phone field
        keyboardView.findViewById<View>(R.id.btn_emoji).visibility =
            if (isNumericInput) View.GONE else View.VISIBLE

        // Update the symbols toggle key text
        val btnSymbols = keyboardView.findViewById<TextView>(R.id.key_symbols)
        if (btnSymbols != null) {
            btnSymbols.text = if (isSymbolsActive) "abc" else "?123"
        }

        when (currentState) {
            KeyboardState.KEYBOARD -> when {
                // Number/phone/date fields get a bare keypad — no voice or symbol clutter
                isNumericInput -> numpadRows.forEach { it.visibility = View.VISIBLE }
                isEmojiActive -> emojiDrawer.visibility = View.VISIBLE
                isSymbolsActive -> {
                    row4.visibility = View.VISIBLE
                    numbersRow1.visibility = View.VISIBLE
                    numbersRow2.visibility = View.VISIBLE
                    numbersRow3.visibility = View.VISIBLE
                }
                else -> {
                    row4.visibility = View.VISIBLE
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

        // Clipboard paste chip first — most useful right when an OTP or address was copied
        clipboardSuggestion()?.let { clip ->
            val label = if (clip.length > 24) clip.take(24) + "…" else clip
            quickRepliesContainer.addView(makeChip("📋 $label") {
                currentInputConnection?.commitText(clip, 1)
            })
        }

        // A number field wants a clean keypad, not phrase chips
        if (isNumericInput) return

        val repliesStr = prefsManager.quickReplies
        val list = repliesStr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

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

                quickRepliesContainer.addView(makeChip(displayText) {
                    handleQuickReplyClick(punjabiText, punglishText)
                })
            }
        }
    }

    /** Build a tappable pill for the top shelf. */
    private fun makeChip(label: String, onTap: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        val paddingH = (12 * density).toInt()
        val paddingV = (6 * density).toInt()
        val marginStartEnd = (4 * density).toInt()

        return TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            setBackgroundResource(R.drawable.mode_pill_bg)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(marginStartEnd, 0, marginStartEnd, 0) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onTap()
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
            val connection = currentInputConnection
            if (connection != null && finalOutput.isNotEmpty()) {
                connection.commitText(finalOutput, 1)
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Number, phone and date fields open straight into the dedicated keypad
        isNumericInput = when (info?.inputType?.and(InputType.TYPE_MASK_CLASS)) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
        if (isNumericInput) {
            isEmojiActive = false
            isSymbolsActive = false
        }

        // Refresh quick replies and size dynamically when keyboard shows up
        populateQuickReplies()
        scaleKeyboardRows(prefsManager.keyboardSize)
        updateUIState()
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
        val rowHeight = (48 * density * multiplier).toInt()
        val specialRowHeight = (54 * density * multiplier).toInt()

        keyboardView.findViewById<View>(R.id.row1)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.row2)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.row3)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.row4)?.layoutParams?.height = specialRowHeight
        keyboardView.findViewById<View>(R.id.numbers_row1)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.numbers_row2)?.layoutParams?.height = rowHeight
        keyboardView.findViewById<View>(R.id.numbers_row3)?.layoutParams?.height = rowHeight
        numpadRowIds.forEach {
            keyboardView.findViewById<View>(it)?.layoutParams?.height = specialRowHeight
        }
        keyboardView.requestLayout()
    }

    /**
     * Fill the emoji drawer: the ten emojis Punjabi families actually text with come
     * first, then the usual categories.
     */
    private fun populateEmojiGrid() {
        val grid = keyboardView.findViewById<LinearLayout>(R.id.emoji_grid)
        if (grid.childCount > 0) return

        val density = resources.displayMetrics.density
        val rowHeight = (46 * density).toInt()

        EMOJIS.chunked(8).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowHeight
                )
            }
            for (emoji in chunk) {
                val cell = TextView(this).apply {
                    text = emoji
                    textSize = 26f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, rowHeight, 1f)
                    setBackgroundResource(android.R.color.transparent)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        currentInputConnection?.commitText(emoji, 1)
                    }
                }
                row.addView(cell)
            }
            grid.addView(row)
        }
    }

    /** Recent clipboard text, unless this is a password field. */
    private fun clipboardSuggestion(): String? {
        val inputType = currentInputEditorInfo?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        if (isPassword) return null

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this).toString().trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        /** Characters to drop so a word-delete removes the trailing gap plus the word before it. */
        fun wordDeleteLength(before: String): Int {
            val withoutTrailingSpace = before.trimEnd()
            val trailingSpace = before.length - withoutTrailingSpace.length
            val wordLength = withoutTrailingSpace.takeLastWhile { !it.isWhitespace() }.length
            return (trailingSpace + wordLength).coerceAtLeast(1)
        }

        private val numpadRowIds = listOf(
            R.id.numpad_row1, R.id.numpad_row2, R.id.numpad_row3, R.id.numpad_row4
        )

        private val EMOJIS = listOf(
            // Top 10 Punjabi texting reactions
            "🙏", "❤️", "😂", "👍", "🎉", "🎂", "☕", "🚜", "👳", "🔥",
            // Smileys
            "😀", "😃", "😄", "😅", "😊", "🙂", "😉", "😍", "🥰", "😘",
            "😎", "🤩", "🥳", "🤗", "🤔", "🙄", "😴", "😭", "😢", "😡",
            "😳", "🥺", "😱", "🤣", "😜", "😇",
            // Hearts and sparkles
            "🧡", "💛", "💚", "💙", "💜", "🖤", "💔", "💯", "✨", "🌟",
            // Hands
            "👎", "👏", "🙌", "🤝", "✌️", "🤞", "💪", "👌", "🫶", "🤲",
            // Life, food, travel
            "🌹", "💐", "🪔", "🎊", "🎁", "🍵", "🍛", "🫓", "🥘", "🍽️",
            "🚗", "✈️", "🏠", "🌙", "☀️", "🌧️", "⚽", "🏏", "🇮🇳", "🇨🇦"
        )
    }
}
