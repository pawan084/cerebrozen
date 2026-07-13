package com.cerebrozen.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cerebrozen.app.MainActivity
import com.cerebrozen.app.R

/**
 * Foreground service that owns the layered soundscape mix so it keeps playing with
 * the screen locked / overnight, with a MediaStyle notification (play / pause /
 * stop). Four looping [ExoPlayer] voices (rain/ocean/wind/drone) each at their own
 * volume, scaled by a master and a sleep-timer fade. [SoundscapeMixer] is the thin
 * controller: it sends intents here and reflects the state we publish back.
 */
class SoundscapeService : Service() {
    companion object {
        const val ACTION_PLAY = "com.cerebrozen.app.SS_PLAY"
        const val ACTION_PAUSE = "com.cerebrozen.app.SS_PAUSE"
        const val ACTION_STOP = "com.cerebrozen.app.SS_STOP"
        const val ACTION_LAYER = "com.cerebrozen.app.SS_LAYER"
        const val ACTION_MASTER = "com.cerebrozen.app.SS_MASTER"
        const val ACTION_TIMER = "com.cerebrozen.app.SS_TIMER"
        const val EXTRA_VOLUMES = "volumes"
        const val EXTRA_MASTER = "master"
        const val EXTRA_INDEX = "index"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_MINUTES = "minutes"
        private const val CHANNEL = "soundscape_mix"
        private const val NOTIF = 78
        // Parallel to SoundscapeMixer.layers (rain, ocean, wind, drone).
        private val LAYER_RES = intArrayOf(R.raw.rain, R.raw.ocean, R.raw.wind, R.raw.drone)
        private const val FADE_LEAD = 12
    }

    private var players: List<ExoPlayer>? = null
    private var session: MediaSession? = null
    private val volumes = floatArrayOf(0.7f, 0f, 0f, 0f)
    private var master = 0.7f
    private var fade = 1f
    // W27 §1: crossfade factor (0 silent → 1 full) — play ramps it up over
    // ~600ms, pause/stop ramp it down before pausing/releasing, so the mix
    // never hard-cuts (including the Player↔Mixer exclusivity handoff).
    private var rampFactor = 1f
    private val ramp = VolumeRamp()
    private var timerMinutes = 0
    private var remaining = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        session = MediaSession(this, "cerebro-soundscape").apply { isActive = true }
    }

    private fun effective(i: Int): Float = (volumes[i] * master * fade * rampFactor).coerceIn(0f, 1f)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                intent.getFloatArrayExtra(EXTRA_VOLUMES)?.let { src ->
                    for (i in volumes.indices) if (i < src.size) volumes[i] = src[i]
                }
                master = intent.getFloatExtra(EXTRA_MASTER, master)
                play()
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopWithRamp()
            ACTION_LAYER -> {
                val i = intent.getIntExtra(EXTRA_INDEX, -1)
                if (i in volumes.indices) {
                    volumes[i] = intent.getFloatExtra(EXTRA_VOLUME, 0f).coerceIn(0f, 1f)
                    players?.getOrNull(i)?.volume = effective(i)
                }
            }
            ACTION_MASTER -> {
                master = intent.getFloatExtra(EXTRA_VOLUME, master).coerceIn(0f, 1f)
                applyVolumes()
            }
            ACTION_TIMER -> setTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
        }
        return START_STICKY
    }

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()

    private fun play() {
        // Satisfy the foreground-start contract FIRST (see AmbientService.play),
        // before constructing four ExoPlayers that could throw.
        SoundscapeMixer.publishPlaying(true)
        updateSession(true)
        ServiceCompat.startForeground(
            this, NOTIF, buildNotification(true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (players == null) {
            // Guard construction so a headless/low-memory environment can't crash
            // the foreground service (parity with AmbientService's runCatching).
            rampFactor = 0f   // W27 §1: fresh voices are born silent
            players = runCatching {
                LAYER_RES.map { res ->
                    ExoPlayer.Builder(this).build().apply {
                        setAudioAttributes(audioAttrs, /* handleAudioFocus = */ true)
                        setHandleAudioBecomingNoisy(true)
                        setWakeMode(C.WAKE_MODE_LOCAL)
                        setMediaItem(MediaItem.fromUri("android.resource://$packageName/$res"))
                        repeatMode = Player.REPEAT_MODE_ONE
                        volume = 0f
                        prepare()
                        playWhenReady = true
                    }
                }
            }.getOrNull()
        } else {
            players?.forEach { it.playWhenReady = true }
        }
        if (players == null) { stopAll(); return }
        // W27 §1: ramp the whole mix in from wherever it sits (0 when fresh or
        // after a pause tail-out) to full — never a hard start.
        applyVolumes()
        ramp.ramp(from = rampFactor, to = 1f, onStep = { rampFactor = it; applyVolumes() })
    }

    private fun applyVolumes() {
        players?.forEachIndexed { i, p -> p.volume = effective(i) }
    }

    private fun pause() {
        // W27 §1: publish the paused state immediately (UI + notification stay
        // honest), then tail the mix out ~600ms before actually pausing.
        SoundscapeMixer.publishPlaying(false)
        updateSession(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java).notify(NOTIF, buildNotification(false))
        if (players == null) return
        ramp.ramp(from = rampFactor, to = 0f, onStep = { rampFactor = it; applyVolumes() }) {
            players?.forEach { it.playWhenReady = false }
        }
    }

    /** W27 §1: stop = ramp the mix out (~600ms), then tear down. State publishes
     * immediately, so the Player↔Mixer exclusivity handoff overlaps this
     * tail-out with the other engine's ramp-in — a true crossfade. The
     * cross-stop contract is unchanged: controllers stay synchronous. */
    private fun stopWithRamp() {
        handler.removeCallbacksAndMessages(null)
        val active = players
        if (active == null || active.none { it.playWhenReady }) { stopAll(); return }
        SoundscapeMixer.publishPlaying(false)
        SoundscapeMixer.publishTimer(0, null)
        ramp.ramp(from = rampFactor, to = 0f, onStep = { rampFactor = it; applyVolumes() }) { stopAll() }
    }

    private fun stopAll() {
        ramp.cancel()
        handler.removeCallbacksAndMessages(null)
        players?.forEach { it.release() }
        players = null
        fade = 1f
        timerMinutes = 0
        remaining = 0
        SoundscapeMixer.publishPlaying(false)
        SoundscapeMixer.publishTimer(0, null)
        session?.isActive = false
        session?.release()
        session = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setTimer(minutes: Int) {
        handler.removeCallbacksAndMessages(null)
        fade = 1f
        applyVolumes()
        timerMinutes = minutes
        if (minutes <= 0) {
            remaining = 0
            SoundscapeMixer.publishTimer(0, null)
            return
        }
        remaining = minutes * 60
        SoundscapeMixer.publishTimer(minutes, remaining)
        val tick = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining == FADE_LEAD) startFade(FADE_LEAD)
                SoundscapeMixer.publishTimer(timerMinutes, remaining.coerceAtLeast(0))
                if (remaining <= 0) {
                    // W27 §5: session-end ritual — after the 12s fade, one soft
                    // bell (user-toggleable, default on), then silence.
                    Chime.playTimerBell()
                    stopAll()
                    return
                }
                handler.postDelayed(this, 1_000)
            }
        }
        handler.postDelayed(tick, 1_000)
    }

    private fun startFade(seconds: Int) {
        val steps = 24
        val interval = seconds * 1_000L / steps
        var step = 0
        val fadeStep = object : Runnable {
            override fun run() {
                step++
                fade = ((steps - step).toFloat() / steps).coerceIn(0f, 1f)
                applyVolumes()
                if (step < steps) handler.postDelayed(this, interval)
            }
        }
        handler.postDelayed(fadeStep, interval)
    }

    override fun onDestroy() {
        ramp.cancel()
        handler.removeCallbacksAndMessages(null)
        players?.forEach { it.release() }
        players = null
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun updateSession(playing: Boolean) {
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun action(label: String, action: String, icon: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, SoundscapeService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun buildNotification(playing: Boolean): Notification {
        val toggle =
            if (playing) action("Pause", ACTION_PAUSE, android.R.drawable.ic_media_pause)
            else action("Play", ACTION_PLAY, android.R.drawable.ic_media_play)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_orb)
            .setContentTitle("Soundscape")
            .setContentText("CereBro · your own calm mix")
            .setContentIntent(open)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(toggle)
            .addAction(action("Stop", ACTION_STOP, android.R.drawable.ic_menu_close_clear_cancel))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Soundscape mix", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controls for your layered ambient soundscape."
                    setShowBadge(false)
                },
            )
        }
    }
}
