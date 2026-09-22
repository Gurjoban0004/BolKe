package com.bolke.keyboard

import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
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
import com.bolke.keyboard.speech.PunjabiSpeaker
import com.bolke.keyboard.ui.VoiceRippleView
import com.bolke.keyboard.translation.TranslationManager
import com.bolke.keyboard.translation.TransliterationHelper
import com.bolke.keyboard.translation.LanguageError
import com.bolke.keyboard.translation.LanguageResponse
import com.bolke.keyboard.translation.LanguageServiceClient
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
        PREVIEW,
        TRANSLATION
    }

    private lateinit var keyboardView: View
    private lateinit var modeSelector: TextView
    private lateinit var quickRepliesContainer: LinearLayout
    
    private lateinit var prefsManager: PreferencesManager
    private lateinit var speechManager: SpeechManager
    private lateinit var translationManager: TranslationManager
    private var punjabiSpeaker: PunjabiSpeaker? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val alphabeticKeys = ArrayList<TextView>()
    private var isShifted = false
    private var isSymbolsActive = false
    private var isNumericInput = false
    private var isSecureField = false
    private var currentState = KeyboardState.KEYBOARD
    private var currentInputText = ""
    private var lastSpaceTime: Long = 0
    private var isToolbarExpanded = false
    private var lastVoiceText = ""
    private var lastVoiceSource = ""
    private var lastVoiceAlternatives = emptyList<String>()
    private var lastVoiceAlternativeIndex = 0
    private var lastVoiceNeedsReview = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideVoiceAction = Runnable { hideVoiceAction() }
    private var backspaceRepeat: Runnable? = null
    private val languageService = LanguageServiceClient()
    private var translationSource = ""
    private var translationText = ""
    private var translationAlternatives = emptyList<String>()
    private var translationAlternativeIndex = 0
    private var lastClipboardHash: Int? = null
    private var lastClipboardAt = 0L
    private var pendingClipboardText: String? = null
    private var clipboardListenerRegistered = false
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureWhatsAppClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(this)
        speechManager = SpeechManager(this).apply {
            setCallback(createSpeechCallback())
        }
        translationManager = TranslationManager()
    }

    override fun onCreateInputView(): View {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_BolKe)
        keyboardView = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_layout, null)

        // Adjust keyboard row heights programmatically based on user setting
        try {
            scaleKeyboardRows(prefsManager.keyboardSize)
        } catch (e: Exception) {
            android.util.Log.e("BolKeIMEService", "Error scaling keyboard rows", e)
        }

        // Bind all the keyboard keys recursively
        try {
            alphabeticKeys.clear()
            setupKeys(keyboardView)
        } catch (e: Exception) {
            android.util.Log.e("BolKeIMEService", "Error setting up keys", e)
        }

        // Bind top bar buttons
        quickRepliesContainer = keyboardView.findViewById(R.id.quick_replies_container)
        try {
            populateQuickReplies()
        } catch (e: Exception) {
            android.util.Log.e("BolKeIMEService", "Error populating quick replies", e)
        }

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
            if (lastVoiceNeedsReview) editLastVoiceInput() else saveLastVoicePhrase()
        }
        keyboardView.findViewById<TextView>(R.id.btn_voice_alternative).setOnClickListener {
            replaceLastVoiceWithAlternative()
        }

        val btnStopVoice = keyboardView.findViewById<TextView>(R.id.btn_stop_voice)
        btnStopVoice.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            speechManager.stopListening()
        }

        keyboardView.findViewById<View>(R.id.btn_translation_back).setOnClickListener { clearTranslation() }
        keyboardView.findViewById<View>(R.id.btn_translation_copy).setOnClickListener {
            if (translationText.isNotBlank()) {
                lastClipboardHash = translationText.hashCode()
                lastClipboardAt = System.currentTimeMillis()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.punjabi_translation), translationText)
                )
                keyboardView.findViewById<TextView>(R.id.btn_translation_copy)
                    .setText(R.string.copied_punjabi)
            }
        }
        keyboardView.findViewById<View>(R.id.btn_translation_another).setOnClickListener {
            if (translationAlternatives.isNotEmpty()) {
                translationText = translationAlternatives[
                    translationAlternativeIndex++ % translationAlternatives.size
                ]
                keyboardView.findViewById<TextView>(R.id.translation_text).text = translationText
            }
        }
        keyboardView.findViewById<View>(R.id.btn_translation_original).setOnClickListener {
            keyboardView.findViewById<TextView>(R.id.translation_original).apply {
                text = translationSource
                visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        keyboardView.findViewById<View>(R.id.btn_translation_retry).setOnClickListener {
            translateClipboardText(translationSource)
        }
        keyboardView.findViewById<View>(R.id.btn_translation_speak).setOnClickListener {
            if (!isSecureField && translationText.isNotBlank()) speaker().toggle(translationText)
        }

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
        punjabiSpeaker?.stop()
        if (currentState == KeyboardState.RECORDING) {
            speechManager.cancel()
        }
        translationSource = ""
        translationText = ""
        translationAlternatives = emptyList()
        currentState = KeyboardState.KEYBOARD
        updateUIState()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        unregisterClipboardListener()
        if (currentState == KeyboardState.RECORDING) {
            speechManager.cancel()
        }
        currentState = KeyboardState.KEYBOARD
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        registerClipboardListener()
    }

    override fun onDestroy() {
        unregisterClipboardListener()
        serviceJob.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        if (::speechManager.isInitialized) speechManager.destroy()
        punjabiSpeaker?.close()
        punjabiSpeaker = null
        super.onDestroy()
    }

    /**
     * Start the Punjabi voice input process.
     */
    private fun startVoiceInput() {
        if (isSecureField) return
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

            override fun onFinalResult(text: String, alternatives: List<String>, confidence: Float?) {
                val voiceStatus = keyboardView.findViewById<TextView>(R.id.voice_status)
                voiceStatus.text = getString(R.string.voice_processing)

                serviceScope.launch {
                    val processed = translationManager.process(
                        gurmukhiText = text,
                        mode = prefsManager.outputMode,
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
                        lastVoiceNeedsReview = confidence != null && confidence < 0.65f
                        lastVoiceAlternatives = alternatives.map {
                            if (prefsManager.outputMode == OutputMode.PUNJABI) it
                            else TransliterationHelper.transliterate(it)
                        }
                        lastVoiceAlternativeIndex = 0
                        showVoiceAction(lastVoiceNeedsReview)
                        
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

    private fun showVoiceAction(needsReview: Boolean) {
        val actionBar = keyboardView.findViewById<View>(R.id.voice_action_bar)
        keyboardView.findViewById<TextView>(R.id.voice_action_status).setText(
            if (needsReview) R.string.voice_low_confidence else R.string.voice_inserted
        )
        keyboardView.findViewById<TextView>(R.id.btn_save_voice_phrase).setText(
            if (needsReview) R.string.edit_last_voice else R.string.save_phrase
        )
        keyboardView.findViewById<View>(R.id.btn_voice_alternative).visibility =
            if (needsReview && lastVoiceAlternatives.isNotEmpty()) View.VISIBLE else View.GONE
        actionBar.visibility = View.VISIBLE
        // One shared handler: removeCallbacks only cancels posts made by the same instance
        mainHandler.removeCallbacks(hideVoiceAction)
        mainHandler.postDelayed(hideVoiceAction, 6_000)
    }

    private fun editLastVoiceInput() {
        undoLastVoiceInput()
        currentInputText = lastVoiceText
        currentState = KeyboardState.PREVIEW
        updateUIState()
    }

    private fun replaceLastVoiceWithAlternative() {
        if (lastVoiceAlternatives.isEmpty()) return
        val connection = currentInputConnection ?: return
        val inserted = "$lastVoiceText "
        if (connection.getTextBeforeCursor(inserted.length, 0)?.toString() != inserted) return
        val replacement = lastVoiceAlternatives[lastVoiceAlternativeIndex++ % lastVoiceAlternatives.size]
        connection.deleteSurroundingText(inserted.length, 0)
        connection.commitText("$replacement ", 1)
        lastVoiceText = replacement
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
        val translationPanel = keyboardView.findViewById<View>(R.id.translation_panel)

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
        translationPanel.visibility = View.GONE
        keyboardView.findViewById<View>(R.id.top_bar).visibility =
            if (isSecureField || currentState == KeyboardState.TRANSLATION) View.GONE else View.VISIBLE
        keyboardView.findViewById<View>(R.id.mic_button).visibility =
            if (isSecureField || isNumericInput) View.GONE else View.VISIBLE
        if (isSecureField) hideVoiceAction()

        // Update the symbols toggle key text
        val btnSymbols = keyboardView.findViewById<TextView>(R.id.key_symbols)
        if (btnSymbols != null) {
            btnSymbols.text = if (isSymbolsActive) "abc" else "?123"
        }

        when (currentState) {
            KeyboardState.KEYBOARD -> when {
                // Number/phone/date fields get a bare keypad — no voice or symbol clutter
                isNumericInput -> numpadRows.forEach { it.visibility = View.VISIBLE }
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
            KeyboardState.TRANSLATION -> translationPanel.visibility = View.VISIBLE
        }
    }

    private fun populateQuickReplies() {
        if (!::quickRepliesContainer.isInitialized) return
        quickRepliesContainer.removeAllViews()

        if (isNumericInput || isSecureField) return

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
                OutputMode.ENGLISH -> applySlangMappings(punglishText, prefsManager.slangMappings)
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

        isSecureField = isSecureInput(info?.inputType ?: 0, info?.imeOptions ?: 0)
        if (isSecureField) currentState = KeyboardState.KEYBOARD

        // Number, phone and date fields open straight into the dedicated keypad
        isNumericInput = when (info?.inputType?.and(InputType.TYPE_MASK_CLASS)) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
        if (isNumericInput || isSecureField) isSymbolsActive = false

        if (::keyboardView.isInitialized) {
            try {
                populateQuickReplies()
                scaleKeyboardRows(prefsManager.keyboardSize)
                updateUIState()
            } catch (e: Exception) {
                android.util.Log.e("BolKeIMEService", "Error in onStartInputView UI refresh", e)
            }
        }
        pendingClipboardText?.let {
            pendingClipboardText = null
            translateClipboardText(it)
        }
    }

    private fun registerClipboardListener() {
        if (clipboardListenerRegistered) return
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.addPrimaryClipChangedListener(clipboardListener)
            clipboardListenerRegistered = true
        } catch (e: Exception) {
            android.util.Log.w("BolKeIMEService", "Could not register clipboard listener", e)
        }
    }

    private fun unregisterClipboardListener() {
        if (!clipboardListenerRegistered) return
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            android.util.Log.w("BolKeIMEService", "Could not unregister clipboard listener", e)
        }
        clipboardListenerRegistered = false
    }

    private fun captureWhatsAppClipboard() {
        try {
            if (!::prefsManager.isInitialized) prefsManager = PreferencesManager(this)
            val editor = currentInputEditorInfo
            if (!prefsManager.translateCopiedMessages ||
                isSecureInput(editor?.inputType ?: 0, editor?.imeOptions ?: 0) ||
                !isWhatsAppEditor()
            ) return
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = clipboard.primaryClip ?: return
            if (!clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || clip.itemCount != 1) return
            val text = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
            val now = System.currentTimeMillis()
            if (!isSafeClipboardText(text) ||
                text.hashCode() == lastClipboardHash && now - lastClipboardAt < 1_500
            ) return
            lastClipboardHash = text.hashCode()
            lastClipboardAt = now
            if (isInputViewShown) translateClipboardText(text) else pendingClipboardText = text
        } catch (e: Exception) {
            android.util.Log.w("BolKeIMEService", "Error capturing clipboard", e)
        }
    }

    private fun translateClipboardText(text: String) {
        if (!::keyboardView.isInitialized) return
        hideVoiceAction()
        translationSource = text
        translationText = ""
        translationAlternatives = emptyList()
        translationAlternativeIndex = 0
        currentState = KeyboardState.TRANSLATION
        keyboardView.findViewById<TextView>(R.id.translation_status)?.setText(R.string.translate_loading)
        keyboardView.findViewById<TextView>(R.id.translation_text)?.text = ""
        keyboardView.findViewById<View>(R.id.translation_original).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.btn_translation_copy).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.btn_translation_speak).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.btn_translation_another).visibility = View.GONE
        keyboardView.findViewById<View>(R.id.btn_translation_retry).visibility = View.GONE
        updateUIState()

        serviceScope.launch {
            when (val response = languageService.translate(text)) {
                is LanguageResponse.Ok -> {
                    translationText = response.result.text
                    translationAlternatives = response.result.alternatives
                    keyboardView.findViewById<TextView>(R.id.translation_status).text = ""
                    keyboardView.findViewById<TextView>(R.id.translation_text).text = translationText
                    keyboardView.findViewById<TextView>(R.id.btn_translation_copy).apply {
                        setText(R.string.copy_punjabi)
                        visibility = View.VISIBLE
                    }
                    keyboardView.findViewById<TextView>(R.id.btn_translation_speak).apply {
                        setText(R.string.speak_punjabi)
                        visibility = View.VISIBLE
                    }
                    keyboardView.findViewById<View>(R.id.btn_translation_another).visibility =
                        if (translationAlternatives.isEmpty()) View.GONE else View.VISIBLE
                }
                is LanguageResponse.Failed -> {
                    keyboardView.findViewById<TextView>(R.id.translation_status).setText(
                        when (response.error) {
                            LanguageError.NOT_CONFIGURED -> R.string.translate_unavailable
                            LanguageError.OFFLINE -> R.string.translate_offline
                            LanguageError.TIMEOUT -> R.string.translate_timeout
                            LanguageError.UNSAFE, LanguageError.UNSUPPORTED -> R.string.translate_unsupported
                            LanguageError.SERVICE -> R.string.translate_failed
                        }
                    )
                    keyboardView.findViewById<View>(R.id.btn_translation_retry).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun clearTranslation() {
        punjabiSpeaker?.stop()
        translationSource = ""
        translationText = ""
        translationAlternatives = emptyList()
        currentState = KeyboardState.KEYBOARD
        updateUIState()
    }

    private fun speaker(): PunjabiSpeaker = punjabiSpeaker ?: PunjabiSpeaker(
        this,
        onStateChanged = { speaking ->
            if (::keyboardView.isInitialized) {
                keyboardView.findViewById<TextView>(R.id.btn_translation_speak)?.setText(
                    if (speaking) R.string.stop_speaking else R.string.speak_punjabi
                )
            }
        },
        onUnavailable = {
            Toast.makeText(this, R.string.punjabi_voice_unavailable, Toast.LENGTH_LONG).show()
        }
    ).also { punjabiSpeaker = it }

    private fun isWhatsAppEditor(): Boolean = currentInputEditorInfo?.packageName in WHATSAPP_PACKAGES

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

    companion object {
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        internal fun isSecureInput(inputType: Int, imeOptions: Int = 0): Boolean {
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isPassword = inputClass == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                ) || inputClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            return isPassword || (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        }

        internal fun isSafeClipboardText(text: String): Boolean {
            if (text.isBlank() || text.length > 4_000 || text.any { it == '\u0000' }) return false
            // Standalone short digit strings are commonly OTPs or PINs. A surrounding
            // message can still be translated while preserving its numbers exactly.
            if (text.matches(Regex("[0-9\\s-]{4,12}"))) return false
            return true
        }

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

    }
}
