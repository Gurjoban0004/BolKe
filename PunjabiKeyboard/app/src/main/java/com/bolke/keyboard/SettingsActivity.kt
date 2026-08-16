package com.bolke.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bolke.keyboard.samjho.PunjabiTranslator
import com.bolke.keyboard.samjho.SamjhoService
import com.bolke.keyboard.samjho.TranslationError
import com.bolke.keyboard.samjho.TranslationResult
import com.bolke.keyboard.util.OutputMode
import com.bolke.keyboard.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Simplified Settings Activity for BolKe.
 */
class SettingsActivity : AppCompatActivity() {

    private companion object {
        const val TEST_SENTENCE = "Hi, can you send me the electricity bill photo before Friday?"
    }

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
    private lateinit var checkboxSamjhoBubble: CheckBox
    private lateinit var geminiKeyInput: EditText
    private lateinit var samjhoStatus: TextView
    private lateinit var samjhoTestResult: TextView

    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(Dispatchers.Main + activityJob)

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
        checkboxSamjhoBubble = findViewById(R.id.samjho_bubble_checkbox)
        geminiKeyInput = findViewById(R.id.gemini_key_input)
        samjhoStatus = findViewById(R.id.samjho_status)
        samjhoTestResult = findViewById(R.id.samjho_test_result)

        findViewById<View>(R.id.btn_samjho_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.btn_samjho_test).setOnClickListener { testTranslation() }

        findViewById<View>(R.id.btn_samjho_clear_cache).setOnClickListener {
            prefsManager.samjhoCache = ""
            samjhoTestResult.text = getString(R.string.samjho_cache_cleared)
            samjhoTestResult.visibility = View.VISIBLE
        }

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
            OutputMode.ENGLISH -> radioGroupMode.check(R.id.radio_punglish)
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

        checkboxSamjhoBubble.isChecked = prefsManager.isSamjhoBubbleEnabled
        geminiKeyInput.setText(prefsManager.geminiApiKey)
    }

    override fun onResume() {
        super.onResume()
        // She may have just come back from the system accessibility screen
        refreshSamjhoStatus()
    }

    private fun refreshSamjhoStatus() {
        val enabled = isSamjhoServiceEnabled()
        samjhoStatus.text = getString(
            if (enabled) R.string.samjho_status_on else R.string.samjho_status_off
        )
        samjhoStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.accent_green else R.color.accent_orange
            )
        )
    }

    /**
     * A service cannot enable itself, so read the system list to report the real state.
     * Same approach SetupActivity uses for the keyboard.
     */
    private fun isSamjhoServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = "$packageName/${SamjhoService::class.java.name}"
        return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
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

        prefsManager.isSamjhoBubbleEnabled = checkboxSamjhoBubble.isChecked
        prefsManager.geminiApiKey = geminiKeyInput.text.toString().trim()
        SamjhoService.onSettingsChanged(this)

        saveStatus.text = getString(R.string.settings_saved)
        saveStatus.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ saveStatus.visibility = View.GONE }, 2000)
    }

    /**
     * Translates a fixed sentence so the key can be checked here rather than by tapping
     * a real message and wondering which half is broken.
     */
    private fun testTranslation() {
        prefsManager.geminiApiKey = geminiKeyInput.text.toString().trim()
        samjhoTestResult.visibility = View.VISIBLE
        samjhoTestResult.text = getString(R.string.samjho_translating)

        activityScope.launch {
            val result = PunjabiTranslator(prefsManager).translate(TEST_SENTENCE, emptyList())
            samjhoTestResult.text = when (result) {
                is TranslationResult.Ok -> result.text
                is TranslationResult.Failed -> getString(
                    when (result.error) {
                        TranslationError.NO_KEY -> R.string.samjho_error_no_key
                        TranslationError.NO_NETWORK -> R.string.samjho_error_network
                        TranslationError.API_ERROR -> R.string.samjho_error_api
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityJob.cancel()
    }

    private fun adjustAppScale(delta: Float) {
        prefsManager.appScale = (prefsManager.appScale + delta).coerceIn(0.8f, 1.4f)
        recreate()
    }
}
