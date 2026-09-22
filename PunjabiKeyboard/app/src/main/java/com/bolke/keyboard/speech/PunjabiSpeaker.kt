package com.bolke.keyboard.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/** Plays Punjabi with the phone's installed text-to-speech engine. */
class PunjabiSpeaker(
    context: Context,
    private val onStateChanged: (Boolean) -> Unit,
    private val onUnavailable: () -> Unit
) : TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ttsReady = false
    private var active = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = unavailable()
        })
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS &&
            tts.isLanguageAvailable(PUNJABI) >= TextToSpeech.LANG_AVAILABLE
        if (ttsReady) tts.language = PUNJABI
    }

    fun toggle(text: String) {
        if (active) {
            stop()
            return
        }
        if (text.isBlank()) return
        setActive(true)
        speakLocally(text)
    }

    fun stop() {
        tts.stop()
        setActive(false)
    }

    fun close() {
        stop()
        scope.cancel()
        tts.shutdown()
    }

    private fun speakLocally(text: String) {
        if (!ttsReady || tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.ERROR) {
            unavailable()
        }
    }

    private fun finish() = scope.launch {
        setActive(false)
    }.let { Unit }

    private fun unavailable() = scope.launch {
        setActive(false)
        onUnavailable()
    }.let { Unit }

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        onStateChanged(value)
    }

    companion object {
        private val PUNJABI = Locale("pa", "IN")
        private const val UTTERANCE_ID = "bolke-punjabi"
    }
}
