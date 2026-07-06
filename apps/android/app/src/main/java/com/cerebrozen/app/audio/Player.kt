package com.cerebrozen.app.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cerebrozen.app.R

/**
 * A tiny, real audio player. Content records don't carry per-track audio yet, so
 * every title shares one bundled ambient bed (looped) — genuine playback, clearly
 * labelled as an ambient bed until the content pipeline serves narrated stories.
 * Compose-observable so any screen can reflect what's playing.
 */
object Player {
    var nowPlaying by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    private var mp: MediaPlayer? = null

    /** Play [title]; tapping the current title again pauses it. */
    fun toggle(context: Context, title: String) {
        if (nowPlaying == title && isPlaying) { pause(); return }
        if (mp == null) {
            mp = MediaPlayer.create(context.applicationContext, R.raw.ambient_bed)?.apply { isLooping = true }
        }
        mp?.start()
        nowPlaying = title
        isPlaying = true
    }

    fun pause() {
        mp?.pause()
        isPlaying = false
    }

    fun stop() {
        mp?.release()
        mp = null
        nowPlaying = null
        isPlaying = false
    }
}
