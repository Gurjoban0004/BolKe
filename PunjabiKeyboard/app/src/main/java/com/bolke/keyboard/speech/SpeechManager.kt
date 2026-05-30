package com.bolke.keyboard.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps Android's SpeechRecognizer for Punjabi (pa-IN) speech recognition.
 * Provides callbacks for partial results, final results, and errors.
 */
class SpeechManager(private val context: Context) {

    /** Callback interface for speech recognition events */
    interface SpeechCallback {
        fun onReadyForSpeech()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(errorMessage: String)
        fun onEndOfSpeech()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: SpeechCallback? = null

    fun setCallback(cb: SpeechCallback) {
        callback = cb
    }

    /**
     * Start listening for Punjabi speech.
     * Creates a new SpeechRecognizer if needed and begins recognition.
     */
    fun startListening() {
        // Destroy existing recognizer to avoid stale state
        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pa-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pa-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "pa-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    /** Stop listening (waits for current speech to finish processing) */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /** Cancel recognition immediately without waiting for results */
    fun cancel() {
        speechRecognizer?.cancel()
    }

    /** Release all resources. Call when the IME service is destroyed. */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        callback = null
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                callback?.onReadyForSpeech()
            }

            override fun onBeginningOfSpeech() {
                // Speech input has started — could trigger visual feedback
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changes — could drive a waveform visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer — not needed for our use case
            }

            override fun onEndOfSpeech() {
                callback?.onEndOfSpeech()
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Audio error — ਮਾਈਕ ਚੈੱਕ ਕਰੋ"
                    SpeechRecognizer.ERROR_CLIENT ->
                        "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "ਮਾਈਕ ਦੀ ਇਜਾਜ਼ਤ ਦਿਓ"
                    SpeechRecognizer.ERROR_NETWORK ->
                        "ਇੰਟਰਨੈਟ ਚੈੱਕ ਕਰੋ"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network timeout — ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ"
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "ਕੁਝ ਸਮਝ ਨਹੀਂ ਆਇਆ — ਦੁਬਾਰਾ ਬੋਲੋ"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "Recognizer busy — ਥੋੜਾ ਰੁਕੋ"
                    SpeechRecognizer.ERROR_SERVER ->
                        "Server error — ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "ਕੁਝ ਸੁਣਿਆ ਨਹੀਂ — ਦੁਬਾਰਾ ਬੋਲੋ"
                    else ->
                        "Error — ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ"
                }
                callback?.onError(message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    callback?.onFinalResult(matches[0])
                } else {
                    callback?.onError("ਕੁਝ ਸਮਝ ਨਹੀਂ ਆਇਆ")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    callback?.onPartialResult(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Custom events — not used
            }
        }
    }
}
