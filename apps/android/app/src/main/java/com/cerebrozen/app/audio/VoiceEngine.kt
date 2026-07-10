package com.cerebro.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * A real, keyless voice loop for the Talk companion: Android's on-device
 * [SpeechRecognizer] transcribes speech, the transcript goes to the chat
 * backend, and [TextToSpeech] speaks the reply. Everything is Compose-observable
 * so the orb can reflect listening / speaking. Degrades cleanly: if no
 * recognition service is present, [available] is false and callers fall back to
 * typing. Must be driven from the main thread (SpeechRecognizer requirement).
 */
class VoiceEngine(context: Context) {
    var listening by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set

    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var onFinal: ((String) -> Unit)? = null
    private var onSpeechDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speaking = true }
            override fun onDone(id: String?) {
                speaking = false
                onSpeechDone?.invoke()
                onSpeechDone = null
            }
            @Deprecated("deprecated in API 21") override fun onError(id: String?) {
                speaking = false
                onSpeechDone?.invoke()
                onSpeechDone = null
            }
        })
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!available || listening) return
        onFinal = onResult
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(listener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        listening = true
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        listening = false
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (text.isBlank()) return
        onSpeechDone = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun dispose() {
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rms: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { listening = false }
        override fun onError(error: Int) { listening = false }
        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) onFinal?.invoke(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
