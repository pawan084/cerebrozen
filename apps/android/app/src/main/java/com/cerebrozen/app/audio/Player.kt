package com.cerebro.app.audio

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Thin controller over [AmbientService]. The service owns the real MediaPlayer +
 * MediaSession and keeps playing in the background with notification controls;
 * it publishes state back here so any Compose screen can reflect what's playing.
 * Titles with server-generated narration (registered in [MediaUrls]) stream
 * their own audio; everything else plays the bundled ambient bed.
 */
object Player {
    var nowPlaying by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    /** Sleep auto-stop timer, minutes (0 = off) — mirrors the iOS player. */
    var timerMinutes by mutableStateOf(0)
        private set

    /** Published by the service on every state change. */
    fun setState(title: String?, playing: Boolean) {
        nowPlaying = title
        isPlaying = playing
    }

    fun setTimerState(minutes: Int) { timerMinutes = minutes }

    /** Ambient volume 0–1 (independent of the system media volume). */
    var volume by mutableStateOf(1f)
        private set

    fun setVolumeState(v: Float) { volume = v }

    fun setVolume(context: Context, v: Float) {
        volume = v
        context.startService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_VOLUME)
                .putExtra(AmbientService.EXTRA_VOLUME, v),
        )
    }

    /** Off → 15 → 30 → 45 → 60 → off (same steps as the iOS sleep player). */
    fun cycleTimer(context: Context) {
        val next = when (timerMinutes) { 0 -> 15; 15 -> 30; 30 -> 45; 45 -> 60; else -> 0 }
        context.startService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_TIMER)
                .putExtra(AmbientService.EXTRA_MINUTES, next),
        )
        timerMinutes = next   // optimistic; the service confirms via setTimerState
    }

    fun toggle(context: Context, title: String) {
        if (nowPlaying == title && isPlaying) pause(context) else play(context, title)
    }

    fun play(context: Context, title: String) {
        context.startForegroundService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_PLAY)
                .putExtra(AmbientService.EXTRA_TITLE, title)
                .putExtra(AmbientService.EXTRA_URL, MediaUrls.urlFor(title)),
        )
    }

    fun pause(context: Context) {
        context.startService(Intent(context, AmbientService::class.java).setAction(AmbientService.ACTION_PAUSE))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, AmbientService::class.java).setAction(AmbientService.ACTION_STOP))
    }
}
