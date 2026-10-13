package com.omni.assistant.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

    init {
        // Eagerly pre-warm TTS so there's zero lag on first speech response
        initTts()
    }

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    configureEngine()
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

    private fun configureEngine() {
        val engine = tts ?: return
        try {
            // Fluent, crisp, responsive conversational rate and pitch
            engine.setSpeechRate(1.15f)
            engine.setPitch(1.02f)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
        }
    }

    private fun applyBestVoice(locale: Locale) {
        val engine = tts ?: return
        try {
            val voices = engine.voices ?: return
            val bestVoice = voices.asSequence()
                .filter { it.locale.language == locale.language }
                .sortedWith(
                    compareByDescending<Voice> { it.quality }
                        .thenByDescending { !it.isNetworkConnectionRequired }
                        .thenByDescending { it.latency <= Voice.LATENCY_NORMAL }
                )
                .firstOrNull()
            if (bestVoice != null) {
                engine.voice = bestVoice
            }
        } catch (e: Exception) {
        }
    }

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
            // Respond quicker when speaker pauses
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            if (languageTag != "auto") putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.let {
            try {
                it.cancel()
            } catch (e: Exception) {
            }
        }
        release()
    }

    private fun release() {
        recognizer?.let {
            try {
                it.destroy()
            } catch (e: Exception) {
            }
        }
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()
    }

    // ---- Spoken output --------------------------------------------------------------------

    private fun cleanMarkdownForSpeech(input: String): String {
        val safe = if (input.length > 5000) input.take(5000) else input
        return safe
            .replace(Regex("```[\\s\\S]*?```"), " code snippet ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            .replace(Regex("[*_~#]"), "")
            .replace(Regex("^[\\s]*[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun speak(text: String, languageTag: String, finished: () -> Unit, failed: (String) -> Unit) {
        val clean = cleanMarkdownForSpeech(text)
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
                    applyBestVoice(locale)
                    engine.setSpeechRate(1.08f)
                    engine.setPitch(1.00f)

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
                    val params = Bundle().apply {
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    }
                    engine.speak(limited, TextToSpeech.QUEUE_FLUSH, params, "omni-utterance")
                }
            }
        }
        if (ttsReady) {
            action()
        } else {
            queued = action
            initTts()
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
