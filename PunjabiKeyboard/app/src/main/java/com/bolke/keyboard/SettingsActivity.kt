package com.bolke.keyboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bolke.keyboard.util.OutputMode
import com.bolke.keyboard.util.PreferencesManager

/**
 * Settings activity to configure the keyboard's output mode and Google Translate API key.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioPunglish: RadioButton
    private lateinit var radioPunjabi: RadioButton
    private lateinit var radioEnglish: RadioButton
    private lateinit var apiKeyInput: EditText
    private lateinit var btnSave: TextView
    private lateinit var saveStatus: TextView

    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsManager = PreferencesManager(this)

        radioGroup = findViewById(R.id.mode_radio_group)
        radioPunglish = findViewById(R.id.radio_punglish)
        radioPunjabi = findViewById(R.id.radio_punjabi)
        radioEnglish = findViewById(R.id.radio_english)
        apiKeyInput = findViewById(R.id.api_key_input)
        btnSave = findViewById(R.id.btn_save)
        saveStatus = findViewById(R.id.save_status)

        // Load current values
        loadSettings()

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val currentMode = prefsManager.outputMode
        when (currentMode) {
            OutputMode.PUNGLISH -> radioPunglish.isChecked = true
            OutputMode.PUNJABI -> radioPunjabi.isChecked = true
            OutputMode.ENGLISH -> radioEnglish.isChecked = true
        }

        apiKeyInput.setText(prefsManager.apiKey)
    }

    private fun saveSettings() {
        // Determine selected output mode
        val selectedMode = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_punglish -> OutputMode.PUNGLISH
            R.id.radio_punjabi -> OutputMode.PUNJABI
            R.id.radio_english -> OutputMode.ENGLISH
            else -> OutputMode.PUNGLISH
        }

        val apiKey = apiKeyInput.text.toString().trim()

        // Save to SharedPreferences
        prefsManager.outputMode = selectedMode
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
