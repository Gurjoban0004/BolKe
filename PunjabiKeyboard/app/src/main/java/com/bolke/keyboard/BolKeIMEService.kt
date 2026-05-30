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

        // Bind all the keyboard keys recursively
        alphabeticKeys.clear()
        setupKeys(keyboardView)

        // Bind top bar buttons
        val btnSettings = keyboardView.findViewById<ImageButton>(R.id.btn_settings)
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        modeSelector = keyboardView.findViewById(R.id.mode_selector)
        updateModeSelectorText()
        modeSelector.setOnClickListener {
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
            startVoiceInput()
        }

        val btnStopVoice = keyboardView.findViewById<TextView>(R.id.btn_stop_voice)
        btnStopVoice.setOnClickListener {
            speechManager.stopListening()
        }

        // Set up mic button touch listener for space/voice input
        val micButton = keyboardView.findViewById<ImageView>(R.id.mic_button)
        micButton.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val width = view.width
                val x = event.x
                // Center 50% for mic, outer 25% on each side for spacebar
                val isCenter = x > width * 0.25 && x < width * 0.75
                if (isCenter) {
                    startVoiceInput()
                } else {
                    currentInputConnection?.commitText(" ", 1)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                view.performClick()
            }
            true
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
                        apiKey = prefsManager.apiKey
                    )
                    currentInputText = processed
                    currentState = KeyboardState.PREVIEW
                    updateUIState()
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
                            view.setOnClickListener { toggleShift() }
                        }
                        "key_backspace", "key_backspace_num" -> {
                            view.setOnClickListener { handleBackspace() }
                        }
                        "key_symbols" -> {
                            view.setOnClickListener { toggleSymbols() }
                        }
                        "key_enter" -> {
                            view.setOnClickListener { handleEnter() }
                        }
                        else -> {
                            // Alphabetic and numeric/symbol keys
                            if (keyText.length == 1 && keyText[0].isLetter()) {
                                alphabeticKeys.add(view)
                            }
                            view.setOnClickListener {
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
}
