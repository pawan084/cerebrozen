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
 * Titles with server-generated narration (registered in [MediaUrls]) stream
 * their own audio; everything else plays the bundled ambient bed.
 *
 * Exclusivity: starting one engine stops the other (REDESIGN §3.4) — [play]
 * stops a running [SoundscapeMixer] first, and the mixer's play does the same
 * to this player. [stop] never counter-calls the other engine, so the pair
 * can't loop.
 */
object Player {
    var nowPlaying by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    /** W27 §2: the content kind of what's loaded ("soundscape", "sleep",
     * "meditation"…), as declared by the screen that started it — the honest
     * source, since only the caller knows which list the title came from.
     * Null when nothing is loaded or the caller didn't say. */
    var nowPlayingKind by mutableStateOf<String?>(null)
        private set

    /** Sleep auto-stop timer, minutes (0 = off) — mirrors the iOS player. */
    var timerMinutes by mutableStateOf(0)
        private set

    /** Published by the service on every state change. */
    fun setState(title: String?, playing: Boolean) {
        nowPlaying = title
        isPlaying = playing
        if (title == null) nowPlayingKind = null
    }

    /** W27 §2: the kind that is audibly playing right now — the aurora's tint
     * signal. Reacts to WHAT plays (bed title kind, or the mixer as a
     * soundscape), never to the waveform. Null when everything is silent. */
    fun audibleKind(): String? = when {
        isPlaying -> nowPlayingKind ?: "soundscape"
        SoundscapeMixer.isPlaying -> "soundscape"
        else -> null
    }

    fun setTimerState(minutes: Int) { timerMinutes = minutes }

    /** Ambient volume 0–1 (independent of the system media volume). */
    var volume by mutableStateOf(1f)
        private set

    fun setVolumeState(v: Float) { volume = v }

    fun setVolume(context: Context, v: Float) {
        volume = v
        // Nothing playing → just remember the level; don't start an idle service
        // that has no player to control (it would sit around doing nothing).
        if (nowPlaying == null) return
        context.startService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_VOLUME)
                .putExtra(AmbientService.EXTRA_VOLUME, v),
        )
    }

    /** Off → 15 → 30 → 45 → 60 → off (same steps as the iOS sleep player). */
    fun cycleTimer(context: Context) {
        val next = when (timerMinutes) { 0 -> 15; 15 -> 30; 30 -> 45; 45 -> 60; else -> 0 }
        timerMinutes = next   // optimistic; the service confirms via setTimerState
        if (nowPlaying == null) return   // no session to arm a timer against
        context.startService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_TIMER)
                .putExtra(AmbientService.EXTRA_MINUTES, next),
        )
    }

    fun toggle(context: Context, title: String, kind: String? = null) {
        if (nowPlaying == title && isPlaying) pause(context) else play(context, title, kind)
    }

    fun play(context: Context, title: String, kind: String? = null) {
        // Exactly one audio engine at a time (REDESIGN §3.4): a running mixer
        // yields to the bed. Its stop() has no counter-call, so this can't loop.
        if (SoundscapeMixer.isPlaying) SoundscapeMixer.stop(context)
        // A resume of the same title (kind omitted, e.g. NowPlayingBar/player
        // transport) keeps the kind the original screen declared.
        nowPlayingKind = kind ?: if (title == nowPlaying) nowPlayingKind else null
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
        nowPlaying = null   // optimistic; the service confirms via setState
        isPlaying = false
        nowPlayingKind = null
    }

    /** Duck the bed under the voice companion while it speaks, then restore. */
    fun duck(context: Context, ducked: Boolean) {
        if (nowPlaying == null) return   // no bed playing → nothing to duck
        context.startService(
            Intent(context, AmbientService::class.java)
                .setAction(AmbientService.ACTION_DUCK)
                .putExtra(AmbientService.EXTRA_DUCK, ducked),
        )
    }
}
