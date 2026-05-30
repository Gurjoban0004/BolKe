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

        if (enabled) {
            btnEnableKeyboard.text = "BolKe Enabled ✓"
            btnEnableKeyboard.setBackgroundResource(R.drawable.btn_rerecord_bg)
            btnEnableKeyboard.setTextColor(resources.getColor(R.color.text_secondary, theme))
            btnEnableKeyboard.isEnabled = false
        } else {
            btnEnableKeyboard.text = getString(R.string.setup_step1_btn)
            btnEnableKeyboard.setBackgroundResource(R.drawable.btn_send_bg)
            btnEnableKeyboard.setTextColor(resources.getColor(R.color.key_text, theme))
            btnEnableKeyboard.isEnabled = true
        }

        if (selected) {
            btnSelectKeyboard.text = "BolKe Selected ✓"
            btnSelectKeyboard.setBackgroundResource(R.drawable.btn_rerecord_bg)
            btnSelectKeyboard.setTextColor(resources.getColor(R.color.text_secondary, theme))
            btnSelectKeyboard.isEnabled = false
        } else {
            btnSelectKeyboard.text = getString(R.string.setup_step2_btn)
            btnSelectKeyboard.setBackgroundResource(R.drawable.btn_send_bg)
            btnSelectKeyboard.setTextColor(resources.getColor(R.color.key_text, theme))
            btnSelectKeyboard.isEnabled = true
        }

        // Highlight done button if both are configured
        if (enabled && selected) {
            btnDone.alpha = 1.0f
        } else {
            btnDone.alpha = 0.6f
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
