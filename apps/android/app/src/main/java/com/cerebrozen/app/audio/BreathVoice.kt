package com.cerebrozen.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Lightweight, on-device phase narration for guided breathing. */
class BreathVoice(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                pending = null
                return@TextToSpeech
            }
            val local = tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (local == TextToSpeech.LANG_MISSING_DATA || local == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(0.88f)
            ready = true
            pending?.let { cue -> pending = null; speak(cue) }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "breath-phase")
    }

    fun dispose() {
        pending = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
