package com.bolke.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bolke.keyboard.util.OutputMode
import com.bolke.keyboard.util.PreferencesManager

/**
 * Simplified Settings Activity for BolKe.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager

    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioGroupSize: RadioGroup
    private lateinit var checkboxAutoSend: CheckBox
    private lateinit var checkboxOfflineMode: CheckBox
    private lateinit var checkboxDoubleTap: CheckBox
    private lateinit var checkboxAutoCap: CheckBox
    private lateinit var apiKeyInput: EditText
    private lateinit var advancedContainer: LinearLayout
    private lateinit var saveStatus: TextView

    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferencesManager(newBase)
        val scale = prefs.appScale
        val config = newBase.resources.configuration
        config.fontScale = scale
        val metrics = newBase.resources.displayMetrics
        config.densityDpi = (metrics.densityDpi * scale).toInt()
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsManager = PreferencesManager(this)

        radioGroupMode = findViewById(R.id.mode_radio_group)
        radioGroupSize = findViewById(R.id.size_radio_group)
        checkboxAutoSend = findViewById(R.id.auto_send_checkbox)
        checkboxOfflineMode = findViewById(R.id.offline_mode_checkbox)
        checkboxDoubleTap = findViewById(R.id.double_tap_period_checkbox)
        checkboxAutoCap = findViewById(R.id.auto_cap_checkbox)
        apiKeyInput = findViewById(R.id.api_key_input)
        advancedContainer = findViewById(R.id.advanced_container)
        saveStatus = findViewById(R.id.save_status)

        findViewById<View>(R.id.btn_open_personalization).setOnClickListener {
            startActivity(Intent(this, PersonalizationActivity::class.java))
        }

        findViewById<View>(R.id.btn_advanced_toggle).setOnClickListener {
            advancedContainer.visibility = if (advancedContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.btn_app_size_minus).setOnClickListener { adjustAppScale(-0.1f) }
        findViewById<View>(R.id.btn_app_size_plus).setOnClickListener { adjustAppScale(0.1f) }

        findViewById<View>(R.id.btn_save).setOnClickListener { saveSettings() }

        loadSettings()
    }

    private fun loadSettings() {
        when (prefsManager.outputMode) {
            OutputMode.PUNGLISH -> radioGroupMode.check(R.id.radio_punglish)
            OutputMode.PUNJABI -> radioGroupMode.check(R.id.radio_punjabi)
            OutputMode.ENGLISH -> radioGroupMode.check(R.id.radio_english)
        }

        val size = prefsManager.keyboardSize
        when {
            size <= 0.86f -> radioGroupSize.check(R.id.size_small)
            size <= 1.01f -> radioGroupSize.check(R.id.size_medium)
            else -> radioGroupSize.check(R.id.size_large)
        }

        checkboxAutoSend.isChecked = prefsManager.isAutoSendEnabled
        checkboxOfflineMode.isChecked = prefsManager.isOfflineMode
        checkboxDoubleTap.isChecked = prefsManager.isDoubleTapPeriodEnabled
        checkboxAutoCap.isChecked = prefsManager.isAutoCapitalizationEnabled
        apiKeyInput.setText(prefsManager.apiKey)
    }

    private fun saveSettings() {
        prefsManager.outputMode = when (radioGroupMode.checkedRadioButtonId) {
            R.id.radio_punjabi -> OutputMode.PUNJABI
            R.id.radio_english -> OutputMode.ENGLISH
            else -> OutputMode.PUNGLISH
        }

        prefsManager.keyboardSize = when (radioGroupSize.checkedRadioButtonId) {
            R.id.size_small -> 0.85f
            R.id.size_medium -> 1.00f
            else -> 1.15f
        }

        prefsManager.isAutoSendEnabled = checkboxAutoSend.isChecked
        prefsManager.isOfflineMode = checkboxOfflineMode.isChecked
        prefsManager.isDoubleTapPeriodEnabled = checkboxDoubleTap.isChecked
        prefsManager.isAutoCapitalizationEnabled = checkboxAutoCap.isChecked
        prefsManager.apiKey = apiKeyInput.text.toString().trim()

        saveStatus.text = getString(R.string.settings_saved)
        saveStatus.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ saveStatus.visibility = View.GONE }, 2000)
    }

    private fun adjustAppScale(delta: Float) {
        prefsManager.appScale = (prefsManager.appScale + delta).coerceIn(0.8f, 1.4f)
        recreate()
    }
}
