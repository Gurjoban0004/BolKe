package com.bolke.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bolke.keyboard.translation.LanguageError
import com.bolke.keyboard.translation.LanguageResponse
import com.bolke.keyboard.translation.LanguageServiceClient
import com.bolke.keyboard.speech.PunjabiSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Standard Android Share and selected-text entry point. It never persists source text. */
class TranslateActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = LanguageServiceClient()
    private var original = ""
    private var translation = ""
    private var alternatives = emptyList<String>()
    private var alternativeIndex = 0
    private var speaker: PunjabiSpeaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        original = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }?.trim().orEmpty()

        findViewById<View>(R.id.btn_translate_copy).setOnClickListener { copyPunjabi() }
        findViewById<View>(R.id.btn_translate_retry).setOnClickListener { translate() }
        findViewById<View>(R.id.btn_translate_another).setOnClickListener { showAlternative() }
        findViewById<View>(R.id.btn_translate_speak).setOnClickListener {
            if (translation.isNotBlank()) speaker().toggle(translation)
        }
        findViewById<View>(R.id.btn_translate_original).setOnClickListener {
            findViewById<TextView>(R.id.translate_original).apply {
                text = original
                visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        findViewById<View>(R.id.btn_translate_back).setOnClickListener { finish() }
        translate()
    }

    private fun translate() {
        speaker?.stop()
        if (!isSupportedPlainText(original)) {
            showError(getString(R.string.translate_unsupported))
            return
        }
        showLoading()
        scope.launch {
            when (val response = client.translate(original)) {
                is LanguageResponse.Ok -> {
                    translation = response.result.text
                    alternatives = response.result.alternatives
                    alternativeIndex = 0
                    findViewById<TextView>(R.id.translate_result).text = translation
                    findViewById<View>(R.id.translate_loading).visibility = View.GONE
                    findViewById<View>(R.id.translate_actions).visibility = View.VISIBLE
                    findViewById<View>(R.id.btn_translate_another).visibility =
                        if (alternatives.isEmpty()) View.GONE else View.VISIBLE
                }
                is LanguageResponse.Failed -> showError(errorText(response.error))
            }
        }
    }

    private fun showLoading() {
        findViewById<TextView>(R.id.translate_result).text = ""
        findViewById<View>(R.id.translate_actions).visibility = View.GONE
        findViewById<TextView>(R.id.translate_loading).apply {
            setText(R.string.translate_loading)
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.btn_translate_retry).visibility = View.GONE
    }

    private fun showError(message: String) {
        findViewById<TextView>(R.id.translate_loading).apply {
            text = message
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.translate_actions).visibility = View.GONE
        findViewById<View>(R.id.btn_translate_retry).visibility = View.VISIBLE
    }

    private fun errorText(error: LanguageError): String = getString(
        when (error) {
            LanguageError.NOT_CONFIGURED -> R.string.translate_unavailable
            LanguageError.OFFLINE -> R.string.translate_offline
            LanguageError.TIMEOUT -> R.string.translate_timeout
            LanguageError.UNSAFE, LanguageError.UNSUPPORTED -> R.string.translate_unsupported
            LanguageError.SERVICE -> R.string.translate_failed
        }
    )

    private fun copyPunjabi() {
        if (translation.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.punjabi_translation), translation))
        findViewById<TextView>(R.id.btn_translate_copy).setText(R.string.copied_punjabi)
    }

    private fun showAlternative() {
        if (alternatives.isEmpty()) return
        speaker?.stop()
        translation = alternatives[alternativeIndex++ % alternatives.size]
        findViewById<TextView>(R.id.translate_result).text = translation
    }

    override fun onDestroy() {
        speaker?.close()
        speaker = null
        scope.cancel()
        original = ""
        translation = ""
        alternatives = emptyList()
        super.onDestroy()
    }

    private fun speaker(): PunjabiSpeaker = speaker ?: PunjabiSpeaker(
        this,
        onStateChanged = { speaking ->
            findViewById<TextView>(R.id.btn_translate_speak).setText(
                if (speaking) R.string.stop_speaking else R.string.speak_punjabi
            )
        },
        onUnavailable = {
            Toast.makeText(this, R.string.punjabi_voice_unavailable, Toast.LENGTH_LONG).show()
        }
    ).also { speaker = it }

    companion object {
        internal fun isSupportedPlainText(text: String): Boolean =
            text.isNotBlank() && text.length <= 4_000 && text.none { it == '\u0000' }
    }
}
