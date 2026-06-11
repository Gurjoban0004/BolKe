package com.bolke.keyboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bolke.keyboard.util.PreferencesManager

/**
 * Setup wizard to guide the user through enabling and selecting the BolKe keyboard.
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 100
    }

    private lateinit var btnEnableKeyboard: TextView
    private lateinit var btnSelectKeyboard: TextView
    private lateinit var btnDone: TextView
    private lateinit var prefsManager: PreferencesManager

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
        setContentView(R.layout.activity_setup)

        prefsManager = PreferencesManager(this)

        // Request microphone permission if not granted
        checkMicrophonePermission()

        // If setup is already complete and IME is enabled/selected, go directly to Settings
        if (prefsManager.isSetupComplete && isKeyboardEnabled() && isKeyboardSelected()) {
            startSettingsActivity()
            finish()
            return
        }

        btnEnableKeyboard = findViewById(R.id.btn_enable_keyboard)
        btnSelectKeyboard = findViewById(R.id.btn_select_keyboard)
        btnDone = findViewById(R.id.btn_done)

        btnEnableKeyboard.setOnClickListener {
            // Open Android Keyboard Settings
            try {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "ਕੀਬੋਰਡ ਸੈਟਿੰਗਜ਼ ਨਹੀਂ ਖੁੱਲ੍ਹ ਸਕੀਆਂ", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectKeyboard.setOnClickListener {
            // Show Input Method Picker
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        btnDone.setOnClickListener {
            val enabled = isKeyboardEnabled()
            val selected = isKeyboardSelected()

            if (enabled && selected) {
                prefsManager.isSetupComplete = true
                Toast.makeText(this, "ਸੈਟਅੱਪ ਪੂਰਾ ਹੋ ਗਿਆ! ✓", Toast.LENGTH_SHORT).show()
                startSettingsActivity()
                finish()
            } else {
                val message = when {
                    !enabled -> "ਕਿਰਪਾ ਕਰਕੇ ਪਹਿਲਾਂ BolKe ਕੀਬੋਰਡ ਨੂੰ ਚਾਲੂ (Enable) ਕਰੋ।"
                    else -> "ਕਿਰਪਾ ਕਰਕੇ BolKe ਨੂੰ ਚਾਲੂ ਕੀਬੋਰਡ (Select) ਵਜੋਂ ਚੁਣੋ।"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSetupStates()
    }

    /**
     * Check if BolKe is enabled in the system's enabled input methods list.
     */
    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        return enabledMethods.any { it.packageName == packageName }
    }

    /**
     * Check if BolKe is currently the selected (default) input method.
     */
    private fun isKeyboardSelected(): Boolean {
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return defaultIme != null && defaultIme.startsWith(packageName)
    }

    /**
     * Dynamically update UI text and visual states based on what settings are configured.
     */
    private fun updateSetupStates() {
        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()

        // Bind containers
        val step1Container = findViewById<View>(R.id.step1_container)
        val step2Container = findViewById<View>(R.id.step2_container)
        val step3Container = findViewById<View>(R.id.step3_container)

        // Bind checks
        val step1Check = findViewById<View>(R.id.step1_check)
        val step2Check = findViewById<View>(R.id.step2_check)

        // Step 1: Enable
        if (enabled) {
            step1Check.visibility = View.VISIBLE
            btnEnableKeyboard.visibility = View.GONE
            step1Container.alpha = 0.6f
        } else {
            step1Check.visibility = View.GONE
            btnEnableKeyboard.visibility = View.VISIBLE
            step1Container.alpha = 1.0f
        }

        // Step 2: Select
        if (enabled) {
            step2Container.alpha = if (selected) 0.6f else 1.0f
            btnSelectKeyboard.visibility = if (selected) View.GONE else View.VISIBLE
            step2Check.visibility = if (selected) View.VISIBLE else View.GONE
        } else {
            step2Container.alpha = 0.4f
            btnSelectKeyboard.visibility = View.VISIBLE
            step2Check.visibility = View.GONE
        }

        // Step 3: Done
        if (enabled && selected) {
            step3Container.alpha = 1.0f
            btnDone.isEnabled = true
        } else {
            step3Container.alpha = 0.4f
            btnDone.isEnabled = false
        }
    }

    private fun startSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun checkMicrophonePermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ਮਾਈਕ ਦੀ ਇਜਾਜ਼ਤ ਮਿਲ ਗਈ ਹੈ! ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "ਮਾਈਕ ਦੀ ਇਜਾਜ਼ਤ ਤੋਂ ਬਿਨਾਂ ਵੌਇਸ ਟਾਈਪਿੰਗ ਕੰਮ ਨਹੀਂ ਕਰੇਗੀ।",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
