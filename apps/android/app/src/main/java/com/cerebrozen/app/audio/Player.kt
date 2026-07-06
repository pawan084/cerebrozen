package com.cerebrozen.app.audio

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Thin controller over [AmbientService]. The service owns the real MediaPlayer +
 * MediaSession and keeps playing in the background with notification controls;
 * it publishes state back here so any Compose screen can reflect what's playing.
 * Content records carry no per-track audio yet, so every title shares one bundled
 * ambient bed — clearly labelled as such.
 */
object Player {
    var nowPlaying by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    /** Published by the service on every state change. */
    fun setState(title: String?, playing: Boolean) {
        nowPlaying = title
        isPlaying = playing
    }

    fun toggle(context: Context, title: String) {
        if (nowPlaying == title && isPlaying) pause(context) else play(context, title)
    }

    fun play(context: Context, title: String) {
        context.startForegroundService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_PLAY)
                .putExtra(AmbientService.EXTRA_TITLE, title),
        )
    }

    fun pause(context: Context) {
        context.startService(Intent(context, AmbientService::class.java).setAction(AmbientService.ACTION_PAUSE))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, AmbientService::class.java).setAction(AmbientService.ACTION_STOP))
    }
}
