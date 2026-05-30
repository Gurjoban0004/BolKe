package com.bolke.keyboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bolke.keyboard.util.OutputMode
import com.bolke.keyboard.util.PreferencesManager

/**
 * Settings activity to configure the keyboard's output mode, height size,
 * auto-send flow, and Google Translate API key.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioPunglish: RadioButton
    private lateinit var radioPunjabi: RadioButton
    private lateinit var radioEnglish: RadioButton

    private lateinit var radioGroupSize: RadioGroup
    private lateinit var radioSizeSmall: RadioButton
    private lateinit var radioSizeMedium: RadioButton
    private lateinit var radioSizeLarge: RadioButton
    private lateinit var radioSizeXLarge: RadioButton

    private lateinit var checkboxAutoSend: CheckBox
    private lateinit var btnAdvancedToggle: TextView
    private lateinit var advancedContainer: LinearLayout
    private lateinit var apiKeyInput: EditText

    private lateinit var btnSave: TextView
    private lateinit var saveStatus: TextView

    private lateinit var prefsManager: PreferencesManager
    private var isAdvancedVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsManager = PreferencesManager(this)

        // Bind output mode views
        radioGroupMode = findViewById(R.id.mode_radio_group)
        radioPunglish = findViewById(R.id.radio_punglish)
        radioPunjabi = findViewById(R.id.radio_punjabi)
        radioEnglish = findViewById(R.id.radio_english)

        // Bind keyboard size views
        radioGroupSize = findViewById(R.id.size_radio_group)
        radioSizeSmall = findViewById(R.id.size_small)
        radioSizeMedium = findViewById(R.id.size_medium)
        radioSizeLarge = findViewById(R.id.size_large)
        radioSizeXLarge = findViewById(R.id.size_xlarge)

        // Bind other settings
        checkboxAutoSend = findViewById(R.id.auto_send_checkbox)
        btnAdvancedToggle = findViewById(R.id.btn_advanced_settings)
        advancedContainer = findViewById(R.id.advanced_settings_container)
        apiKeyInput = findViewById(R.id.api_key_input)

        btnSave = findViewById(R.id.btn_save)
        saveStatus = findViewById(R.id.save_status)

        // Bind Advanced Toggle click listener
        btnAdvancedToggle.setOnClickListener {
            toggleAdvancedSettings()
        }

        // Load current values
        loadSettings()

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun toggleAdvancedSettings() {
        isAdvancedVisible = !isAdvancedVisible
        if (isAdvancedVisible) {
            advancedContainer.visibility = View.VISIBLE
            btnAdvancedToggle.text = "⚙️ Advanced Settings (Google Cloud API) ▴"
        } else {
            advancedContainer.visibility = View.GONE
            btnAdvancedToggle.text = "⚙️ Advanced Settings (Google Cloud API) ▾"
        }
    }

    private fun loadSettings() {
        // Output mode
        when (prefsManager.outputMode) {
            OutputMode.PUNGLISH -> radioPunglish.isChecked = true
            OutputMode.PUNJABI -> radioPunjabi.isChecked = true
            OutputMode.ENGLISH -> radioEnglish.isChecked = true
        }

        // Keyboard height scale size
        val currentSize = prefsManager.keyboardSize
        when {
            currentSize <= 0.86f -> radioSizeSmall.isChecked = true
            currentSize <= 1.01f -> radioSizeMedium.isChecked = true
            currentSize <= 1.16f -> radioSizeLarge.isChecked = true
            else -> radioSizeXLarge.isChecked = true
        }

        // Auto-send
        checkboxAutoSend.isChecked = prefsManager.isAutoSendEnabled

        // API Key
        apiKeyInput.setText(prefsManager.apiKey)
    }

    private fun saveSettings() {
        // 1. Output mode
        val selectedMode = when (radioGroupMode.checkedRadioButtonId) {
            R.id.radio_punglish -> OutputMode.PUNGLISH
            R.id.radio_punjabi -> OutputMode.PUNJABI
            R.id.radio_english -> OutputMode.ENGLISH
            else -> OutputMode.PUNGLISH
        }

        // 2. Keyboard height scale size
        val selectedSize = when (radioGroupSize.checkedRadioButtonId) {
            R.id.size_small -> 0.85f
            R.id.size_medium -> 1.00f
            R.id.size_large -> 1.15f
            R.id.size_xlarge -> 1.30f
            else -> 1.15f
        }

        // 3. Auto-send and API Key
        val isAutoSend = checkboxAutoSend.isChecked
        val apiKey = apiKeyInput.text.toString().trim()

        // Save to preferences
        prefsManager.outputMode = selectedMode
        prefsManager.keyboardSize = selectedSize
        prefsManager.isAutoSendEnabled = isAutoSend
        prefsManager.apiKey = apiKey

        // Show status message
        saveStatus.text = getString(R.string.settings_saved)
        saveStatus.visibility = View.VISIBLE

        // Auto-hide status message after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            saveStatus.visibility = View.GONE
        }, 2000)
    }
}
