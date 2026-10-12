package com.omni.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Wraps Android's SpeechRecognizer (voice input) and TextToSpeech (spoken replies).
 * All methods must be called on the main thread. Which languages work depends on the speech
 * services installed on the device (for Hindi, the Google speech service and Hindi voice data).
 */
class VoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var queued: (() -> Unit)? = null

    val recognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    // ---- Voice input ----------------------------------------------------------------------

    fun startListening(
        languageTag: String,
        heardPartial: (String) -> Unit,
        heardFinal: (String) -> Unit,
        failed: (String) -> Unit
    ) {
        stopListening()
        if (!recognitionAvailable) {
            failed("Speech recognition isn't available on this device.")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val text = firstResult(partialResults)
                if (text != null) heardPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                release()
                if (text.isNullOrBlank()) failed("Didn't catch that. Try again.") else heardFinal(text)
            }

            override fun onError(error: Int) {
                release()
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Didn't catch that. Try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is needed for voice input."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "The speech service needs an internet connection."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "The speech service is busy. Try again in a moment."
                    SpeechRecognizer.ERROR_CLIENT -> null // we cancelled it ourselves
                    else -> "Voice input failed (code $error)."
                }
                if (message != null) failed(message)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (languageTag != "auto") putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.let {
            try {
                it.cancel()
            } catch (_: Exception) {
            }
        }
        release()
    }

    private fun release() {
        recognizer?.let {
            try {
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()
    }

    // ---- Spoken output --------------------------------------------------------------------

    fun speak(text: String, languageTag: String, finished: () -> Unit, failed: (String) -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            finished()
            return
        }
        val action: () -> Unit = {
            val engine = tts
            if (engine == null || ttsFailed) {
                failed("Text-to-speech isn't available on this device.")
            } else {
                val locale = if (languageTag == "auto") Locale.getDefault() else Locale.forLanguageTag(languageTag)
                val langResult = engine.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    failed("No speech voice installed for ${locale.displayName}. Install it in Android settings.")
                } else {
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            main.post { finished() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            main.post { failed("Text-to-speech failed.") }
                        }
                    })
                    val limited = clean.take(TextToSpeech.getMaxSpeechInputLength() - 1)
                    engine.speak(limited, TextToSpeech.QUEUE_FLUSH, null, "omni-utterance")
                }
            }
        }
        if (ttsReady) {
            action()
        } else {
            queued = action
            if (tts == null) {
                tts = TextToSpeech(appContext) { status ->
                    main.post {
                        if (status == TextToSpeech.SUCCESS) {
                            ttsReady = true
                        } else {
                            ttsFailed = true
                            ttsReady = true
                        }
                        val pending = queued
                        queued = null
                        pending?.invoke()
                    }
                }
            }
        }
    }

    fun stopSpeaking() {
        queued = null
        tts?.stop()
    }

    fun shutdown() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
