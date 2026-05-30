package com.bolke.keyboard

import android.content.Context
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private lateinit var checkboxOfflineMode: CheckBox
    private lateinit var radioGroupQRFormat: RadioGroup
    private lateinit var radioQRFollow: RadioButton
    private lateinit var radioQRPunglish: RadioButton
    private lateinit var radioQRPunjabi: RadioButton

    private lateinit var btnSave: TextView
    private lateinit var saveStatus: TextView

    private lateinit var btnHelpGuide: TextView
    private lateinit var helpGuideContainer: LinearLayout
    private lateinit var btnAppSizeMinus: TextView
    private lateinit var btnAppSizePlus: TextView

    private val quickRepliesList = ArrayList<Pair<String, String>>()
    private val slangMappingsList = ArrayList<Pair<String, String>>()

    private lateinit var quickRepliesListContainer: LinearLayout
    private lateinit var slangListContainer: LinearLayout
    private lateinit var editNewQRPunjabi: EditText
    private lateinit var editNewQRPunglish: EditText
    private lateinit var editNewSlangTarget: EditText
    private lateinit var editNewSlangReplacement: EditText
    private lateinit var btnAddQR: TextView
    private lateinit var btnAddSlang: TextView
    private lateinit var btnResetQR: TextView
    private lateinit var btnResetSlang: TextView

    private lateinit var prefsManager: PreferencesManager
    private var isAdvancedVisible = false
    private var isHelpGuideVisible = false


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

        quickRepliesListContainer = findViewById(R.id.quick_replies_list_container)
        slangListContainer = findViewById(R.id.slang_list_container)
        editNewQRPunjabi = findViewById(R.id.new_qr_punjabi)
        editNewQRPunglish = findViewById(R.id.new_qr_punglish)
        editNewSlangTarget = findViewById(R.id.new_slang_target)
        editNewSlangReplacement = findViewById(R.id.new_slang_replacement)
        btnAddQR = findViewById(R.id.btn_add_qr)
        btnAddSlang = findViewById(R.id.btn_add_slang)
        btnResetQR = findViewById(R.id.btn_reset_qr_defaults)
        btnResetSlang = findViewById(R.id.btn_reset_slang_defaults)

        btnAddQR.setOnClickListener { addQuickReply() }
        btnAddSlang.setOnClickListener { addSlangMapping() }
        btnResetQR.setOnClickListener { resetQuickReplyDefaults() }
        btnResetSlang.setOnClickListener { resetSlangDefaults() }

        checkboxOfflineMode = findViewById(R.id.offline_mode_checkbox)
        radioGroupQRFormat = findViewById(R.id.qr_format_radio_group)
        radioQRFollow = findViewById(R.id.qr_follow_keyboard)
        radioQRPunglish = findViewById(R.id.qr_always_punglish)
        radioQRPunjabi = findViewById(R.id.qr_always_punjabi)

        btnSave = findViewById(R.id.btn_save)
        saveStatus = findViewById(R.id.save_status)

        // Bind Help Guide
        btnHelpGuide = findViewById(R.id.btn_help_guide)
        helpGuideContainer = findViewById(R.id.help_guide_container)
        btnHelpGuide.setOnClickListener {
            toggleHelpGuide()
        }

        // Bind App Size buttons
        btnAppSizeMinus = findViewById(R.id.btn_app_size_minus)
        btnAppSizePlus = findViewById(R.id.btn_app_size_plus)
        btnAppSizeMinus.setOnClickListener {
            adjustAppScale(-0.1f)
        }
        btnAppSizePlus.setOnClickListener {
            adjustAppScale(0.1f)
        }

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

        // Custom Quick replies and slang lists parsing
        quickRepliesList.clear()
        val savedReplies = prefsManager.quickReplies
        savedReplies.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            val parts = it.split("|")
            if (parts.size == 2) {
                quickRepliesList.add(Pair(parts[0], parts[1]))
            }
        }
        refreshQuickRepliesUI()

        slangMappingsList.clear()
        val savedSlang = prefsManager.slangMappings
        savedSlang.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                slangMappingsList.add(Pair(parts[0], parts[1]))
            }
        }
        refreshSlangUI()

        // Offline mode
        checkboxOfflineMode.isChecked = prefsManager.isOfflineMode

        // Quick replies format
        when (prefsManager.quickReplyMode) {
            "FOLLOW_KEYBOARD" -> radioQRFollow.isChecked = true
            "PUNGLISH" -> radioQRPunglish.isChecked = true
            "PUNJABI" -> radioQRPunjabi.isChecked = true
        }
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
        val isOfflineMode = checkboxOfflineMode.isChecked
        val qrMode = when (radioGroupQRFormat.checkedRadioButtonId) {
            R.id.qr_follow_keyboard -> "FOLLOW_KEYBOARD"
            R.id.qr_always_punglish -> "PUNGLISH"
            R.id.qr_always_punjabi -> "PUNJABI"
            else -> "FOLLOW_KEYBOARD"
        }

        // Serialize quick replies list
        val quickRepliesSerialized = quickRepliesList.joinToString("\n") { "${it.first}|${it.second}" }
        // Serialize slang mappings list
        val slangMappingsSerialized = slangMappingsList.joinToString("\n") { "${it.first}:${it.second}" }

        // Save to preferences
        prefsManager.outputMode = selectedMode
        prefsManager.keyboardSize = selectedSize
        prefsManager.isAutoSendEnabled = isAutoSend
        prefsManager.apiKey = apiKey
        prefsManager.quickReplies = quickRepliesSerialized
        prefsManager.slangMappings = slangMappingsSerialized
        prefsManager.isOfflineMode = isOfflineMode
        prefsManager.quickReplyMode = qrMode

        // Show status message
        saveStatus.text = getString(R.string.settings_saved)
        saveStatus.visibility = View.VISIBLE

        // Auto-hide status message after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            saveStatus.visibility = View.GONE
        }, 2000)
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun addQuickReply() {
        val punjabi = editNewQRPunjabi.text.toString().trim()
        val punglish = editNewQRPunglish.text.toString().trim()
        if (punjabi.isNotEmpty() && punglish.isNotEmpty()) {
            quickRepliesList.add(Pair(punjabi, punglish))
            editNewQRPunjabi.text.clear()
            editNewQRPunglish.text.clear()
            refreshQuickRepliesUI()
            saveSettings()
        } else {
            Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਦੋਵੇਂ ਖਾਨੇ ਭਰੋ (Fill both fields)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeQuickReply(punjabi: String, punglish: String) {
        quickRepliesList.removeAll { it.first == punjabi && it.second == punglish }
        refreshQuickRepliesUI()
        saveSettings()
    }

    private fun resetQuickReplyDefaults() {
        quickRepliesList.clear()
        val defaultReplies = "ਕਿੱਥੇ ਆਗਿਆ?|kithe aagya?\nਮੈਂ ਚੱਲ ਪਈ!|mai chalpyi!\nਪੁੱਤ ਕਿੱਥੇ ਆਂ?|putt kithe aa?\nਹਾਂਜੀ|hanji\nਨਾਜੀ|naji\nਠੀਕ ਹੈ|thik hai\nਸਤਿ ਸ੍ਰੀ ਅਕਾਲ|sat sri akal\nਕੀ ਹਾਲ ਹੈ?|ki haal hai?"
        defaultReplies.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            val parts = it.split("|")
            if (parts.size == 2) {
                quickRepliesList.add(Pair(parts[0], parts[1]))
            }
        }
        refreshQuickRepliesUI()
        saveSettings()
    }

    private fun refreshQuickRepliesUI() {
        quickRepliesListContainer.removeAllViews()
        for (pair in quickRepliesList) {
            val view = createQuickReplyRow(pair.first, pair.second)
            quickRepliesListContainer.addView(view)
        }
    }

    private fun createQuickReplyRow(punjabi: String, punglish: String): View {
        val context = this
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.mode_pill_bg)
            setPadding(12.toPx(), 10.toPx(), 12.toPx(), 10.toPx())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4.toPx(), 0, 4.toPx())
            }
            layoutParams = params
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val punjabiTxt = TextView(context).apply {
            text = punjabi
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val punglishTxt = TextView(context).apply {
            text = punglish
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
            setPadding(0, 2.toPx(), 0, 0)
        }

        textLayout.addView(punjabiTxt)
        textLayout.addView(punglishTxt)

        val deleteBtn = TextView(context).apply {
            text = "🗑️"
            textSize = 16f
            setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                removeQuickReply(punjabi, punglish)
            }
        }

        contentLayout.addView(textLayout)
        contentLayout.addView(deleteBtn)
        rowLayout.addView(contentLayout)

        return rowLayout
    }

    private fun addSlangMapping() {
        val target = editNewSlangTarget.text.toString().trim()
        val replacement = editNewSlangReplacement.text.toString().trim()
        if (target.isNotEmpty() && replacement.isNotEmpty()) {
            slangMappingsList.removeAll { it.first.lowercase() == target.lowercase() }
            slangMappingsList.add(Pair(target, replacement))
            editNewSlangTarget.text.clear()
            editNewSlangReplacement.text.clear()
            refreshSlangUI()
            saveSettings()
        } else {
            Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਦੋਵੇਂ ਖਾਨੇ ਭਰੋ (Fill both fields)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeSlangMapping(target: String) {
        slangMappingsList.removeAll { it.first == target }
        refreshSlangUI()
        saveSettings()
    }

    private fun resetSlangDefaults() {
        slangMappingsList.clear()
        val defaultSlangs = "karo:kro\nchalo:chlo\nkarda:krda\nkardi:krdi\nkarde:krde\njaldi:jldi"
        defaultSlangs.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                slangMappingsList.add(Pair(parts[0], parts[1]))
            }
        }
        refreshSlangUI()
        saveSettings()
    }

    private fun refreshSlangUI() {
        slangListContainer.removeAllViews()
        for (pair in slangMappingsList) {
            val view = createSlangRow(pair.first, pair.second)
            slangListContainer.addView(view)
        }
    }

    private fun createSlangRow(target: String, replacement: String): View {
        val context = this
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 6.toPx(), 0, 6.toPx())
        }

        val targetTxt = TextView(context).apply {
            text = target
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val arrowTxt = TextView(context).apply {
            text = " ➔ "
            setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val replacementTxt = TextView(context).apply {
            text = replacement
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val deleteBtn = TextView(context).apply {
            text = "🗑️"
            textSize = 16f
            setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                removeSlangMapping(target)
            }
        }

        rowLayout.addView(targetTxt)
        rowLayout.addView(arrowTxt)
        rowLayout.addView(replacementTxt)
        rowLayout.addView(deleteBtn)

        return rowLayout
    }

    private fun toggleHelpGuide() {
        isHelpGuideVisible = !isHelpGuideVisible
        if (isHelpGuideVisible) {
            helpGuideContainer.visibility = View.VISIBLE
            btnHelpGuide.text = "💡 View Voice Setup Guide (ਵੌਇਸ ਗਾਈਡ) ▴"
        } else {
            helpGuideContainer.visibility = View.GONE
            btnHelpGuide.text = "💡 View Voice Setup Guide (ਵੌਇਸ ਗਾਈਡ) ▾"
        }
    }

    private fun adjustAppScale(delta: Float) {
        val newScale = (prefsManager.appScale + delta).coerceIn(0.8f, 1.6f)
        prefsManager.appScale = newScale
        recreate()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
