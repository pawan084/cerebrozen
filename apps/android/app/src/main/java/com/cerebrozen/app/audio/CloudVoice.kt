package com.cerebrozen.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Recording + playback for the CLOUD voice loop (iOS-parity quality):
 *   mic → AAC clip → POST /voice/stt (Deepgram) → /chat|/oracle
 *       → POST /voice/tts (ElevenLabs MP3) → playback.
 * The keyless on-device [VoiceEngine] stays as the fallback when the server
 * reports voice disabled. Tap-to-stop recording; tap-to-interrupt playback.
 */
class CloudVoice(private val context: Context) {
    var recording by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private val clip = File(context.cacheDir, "voice-turn.m4a")
    private val reply = File(context.cacheDir, "voice-reply.mp3")

    fun startRecording(): Boolean {
        if (recording) return true
        return runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(clip.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recording = true
            true
        }.getOrElse {
            recorder?.release(); recorder = null
            false
        }
    }

    /** Peak mic amplitude since the last call (0 when not recording) — drives the
     * reactive orb and the trailing-silence auto-endpoint. */
    fun maxAmplitude(): Int = recorder?.let { runCatching { it.maxAmplitude }.getOrDefault(0) } ?: 0

    /** Stop and return the recorded AAC clip (null if nothing usable). The raw
     * mic recording is deleted from disk as soon as we've read it into memory. */
    fun stopRecording(): ByteArray? {
        val r = recorder ?: return null
        recording = false
        recorder = null
        return runCatching {
            r.stop(); r.release()
            val bytes = clip.readBytes()
            clip.delete()   // don't leave the user's voice on disk
            bytes.takeIf { it.size > 1_000 }   // ignore accidental blips
        }.getOrNull()
    }

    /** Play an MP3 reply; [onDone] fires on natural end or interruption. The disk
     * write and prepare run off the main thread (this is called from a coroutine),
     * but the MediaPlayer is created on the caller's Looper so completion callbacks
     * still fire. The reply file is deleted once played. */
    suspend fun play(mp3: ByteArray, onDone: () -> Unit = {}) {
        stopPlayback()
        runCatching {
            withContext(Dispatchers.IO) { reply.writeBytes(mp3) }
            val p = MediaPlayer()
            p.setDataSource(reply.absolutePath)
            p.setOnCompletionListener { speaking = false; reply.delete(); onDone() }
            withContext(Dispatchers.IO) { p.prepare() }   // the blocking step
            player = p
            speaking = true
            p.start()
        }.onFailure { speaking = false; reply.delete(); onDone() }
    }

    /** Tap-to-interrupt (mirrors the iOS barge-in affordance). */
    fun stopPlayback() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        speaking = false
    }

    fun dispose() {
        recorder?.let { runCatching { it.stop(); it.release() } }
        recorder = null
        stopPlayback()
        // Leave no voice audio behind on disk.
        runCatching { clip.delete() }
        runCatching { reply.delete() }
    }
}
