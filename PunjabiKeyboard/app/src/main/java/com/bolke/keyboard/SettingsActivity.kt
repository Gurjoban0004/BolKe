package com.bolke.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bolke.keyboard.util.OutputMode
import com.bolke.keyboard.util.PreferencesManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: PreferencesManager

    override fun attachBaseContext(newBase: Context) {
        val scale = PreferencesManager(newBase).appScale
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = scale
            densityDpi = (newBase.resources.displayMetrics.densityDpi * scale).toInt()
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = PreferencesManager(this)

        findViewById<View>(R.id.btn_open_personalization).setOnClickListener {
            startActivity(Intent(this, PersonalizationActivity::class.java))
        }
        findViewById<View>(R.id.btn_open_privacy).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        findViewById<View>(R.id.btn_try_keyboard).setOnClickListener { openKeyboardTest() }
        findViewById<View>(R.id.btn_app_size_minus).setOnClickListener { adjustAppScale(-0.1f) }
        findViewById<View>(R.id.btn_app_size_plus).setOnClickListener { adjustAppScale(0.1f) }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveSettings() }
        loadSettings()
    }

    private fun loadSettings() {
        findViewById<RadioGroup>(R.id.mode_radio_group).check(
            if (prefs.outputMode == OutputMode.PUNJABI) R.id.radio_punjabi else R.id.radio_punglish
        )
        findViewById<RadioGroup>(R.id.size_radio_group).check(
            when {
                prefs.keyboardSize <= 0.86f -> R.id.size_small
                prefs.keyboardSize <= 1.01f -> R.id.size_medium
                else -> R.id.size_large
            }
        )
        findViewById<CheckBox>(R.id.auto_send_checkbox).isChecked = prefs.isAutoSendEnabled
        findViewById<CheckBox>(R.id.offline_mode_checkbox).isChecked = prefs.isOfflineMode
        findViewById<CheckBox>(R.id.double_tap_period_checkbox).isChecked = prefs.isDoubleTapPeriodEnabled
        findViewById<CheckBox>(R.id.auto_cap_checkbox).isChecked = prefs.isAutoCapitalizationEnabled
        findViewById<CheckBox>(R.id.translate_copied_checkbox).isChecked = prefs.translateCopiedMessages
        findViewById<TextView>(R.id.language_service_status).setText(
            if (BuildConfig.LANGUAGE_SERVICE_URL.isBlank()) R.string.service_not_configured else R.string.service_ready
        )
    }

    private fun saveSettings() {
        prefs.outputMode = if (findViewById<RadioGroup>(R.id.mode_radio_group).checkedRadioButtonId == R.id.radio_punjabi) {
            OutputMode.PUNJABI
        } else OutputMode.PUNGLISH
        prefs.keyboardSize = when (findViewById<RadioGroup>(R.id.size_radio_group).checkedRadioButtonId) {
            R.id.size_small -> 0.85f
            R.id.size_medium -> 1f
            else -> 1.15f
        }
        prefs.isAutoSendEnabled = findViewById<CheckBox>(R.id.auto_send_checkbox).isChecked
        prefs.isOfflineMode = findViewById<CheckBox>(R.id.offline_mode_checkbox).isChecked
        prefs.isDoubleTapPeriodEnabled = findViewById<CheckBox>(R.id.double_tap_period_checkbox).isChecked
        prefs.isAutoCapitalizationEnabled = findViewById<CheckBox>(R.id.auto_cap_checkbox).isChecked
        prefs.translateCopiedMessages = findViewById<CheckBox>(R.id.translate_copied_checkbox).isChecked

        findViewById<TextView>(R.id.save_status).apply {
            setText(R.string.settings_saved)
            visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({ visibility = View.GONE }, 2_000)
        }
    }

    private fun adjustAppScale(delta: Float) {
        prefs.appScale = (prefs.appScale + delta).coerceIn(0.8f, 1.4f)
        recreate()
    }

    private fun openKeyboardTest() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val selectedIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (!selectedIme.orEmpty().startsWith(packageName)) {
            Toast.makeText(this, R.string.select_bolke_first, Toast.LENGTH_LONG).show()
            imm.showInputMethodPicker()
            return
        }
        findViewById<EditText>(R.id.keyboard_test_input).apply {
            requestFocus()
            post { imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
        }
    }
}
