package com.cerebro.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.cerebro.app.MainActivity
import com.cerebro.app.R

/**
 * Plays the looping ambient bed as a foreground service with a MediaStyle
 * notification (play / pause / stop + lock-screen transport), so calm audio
 * keeps going when the app is backgrounded. The [Player] object is the thin
 * controller; this service owns the actual MediaPlayer + MediaSession.
 */
class AmbientService : Service() {
    companion object {
        const val ACTION_PLAY = "com.cerebro.app.PLAY"
        const val ACTION_PAUSE = "com.cerebro.app.PAUSE"
        const val ACTION_STOP = "com.cerebro.app.STOP"
        const val ACTION_TIMER = "com.cerebro.app.TIMER"
        const val ACTION_VOLUME = "com.cerebro.app.VOLUME"
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_VOLUME = "volume"
        private const val CHANNEL = "ambient_playback"
        private const val NOTIF = 77
    }

    private var mp: MediaPlayer? = null
    private var session: MediaSession? = null
    private var title = "Ambient bed"
    private var volume = 1f
    /** Current audio source: "" = bundled ambient bed, else a narration URL. */
    private var currentSrc = ""
    /** Streamed players prepare async; start/pause are only legal once ready. */
    private var prepared = false
    private var pauseRequested = false
    // Sleep auto-stop timer (mirrors the iOS player): fades ~10 s, then stops.
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        session = MediaSession(this, "cerebro-ambient").apply { isActive = true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                intent.getStringExtra(EXTRA_TITLE)?.let { title = it }
                // No extra (notification resume) keeps the current source;
                // an explicit "" means this title has no narration → bed.
                play(intent.getStringExtra(EXTRA_URL) ?: currentSrc)
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopAll()
            ACTION_TIMER -> setTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_VOLUME -> {
                volume = intent.getFloatExtra(EXTRA_VOLUME, 1f).coerceIn(0f, 1f)
                mp?.setVolume(volume, volume)
                Player.setVolumeState(volume)
            }
        }
        return START_STICKY
    }

    private fun setTimer(minutes: Int) {
        timerHandler.removeCallbacksAndMessages(null)
        mp?.setVolume(1f, 1f)
        Player.setTimerState(minutes)
        if (minutes > 0) {
            val untilFade = (minutes * 60_000L - 10_000L).coerceAtLeast(1_000L)
            timerHandler.postDelayed({ fadeOut(10) }, untilFade)
        }
    }

    private fun fadeOut(stepsLeft: Int) {
        val player = mp
        if (stepsLeft <= 0 || player == null) { stopAll(); return }
        player.setVolume(stepsLeft / 10f, stepsLeft / 10f)
        timerHandler.postDelayed({ fadeOut(stepsLeft - 1) }, 1_000L)
    }

    private fun play(url: String = currentSrc) {
        pauseRequested = false
        if (mp != null && url != currentSrc) { mp?.release(); mp = null }
        if (mp == null) {
            currentSrc = url
            mp = if (url.isBlank()) createBed() else createStream(url)
        }
        val player = mp
        if (player == null) {
            stopAll()
            return
        }
        if (prepared) {
            runCatching {
                player.setVolume(volume, volume)
                player.start()
            }.onFailure {
                stopAll()
                return
            }
        }
        Player.setState(title, true)
        updateSession(true)
        ServiceCompat.startForeground(
            this, NOTIF, buildNotification(true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun createBed(): MediaPlayer? {
        prepared = false
        return runCatching {
            resources.openRawResourceFd(R.raw.ambient_bed).use { afd ->
                MediaPlayer().apply {
                    setAudioAttributes(mediaAudioAttributes())
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    isLooping = true
                    prepare()
                    setVolume(volume, volume)
                    prepared = true
                }
            }
        }.getOrNull()
    }

    /** Stream a narrated track over HTTP(S); any failure falls back to the bed. */
    private fun createStream(url: String): MediaPlayer? {
        prepared = false
        val player = MediaPlayer()
        player.setAudioAttributes(mediaAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH))
        player.setOnPreparedListener {
            prepared = true
            it.setVolume(volume, volume)
            if (!pauseRequested) {
                runCatching { it.start() }.onFailure { stopAll() }
            }
        }
        player.setOnErrorListener { _, _, _ -> fallBackToBed(); true }
        player.setOnCompletionListener { stopAll() }   // narration ends; never loops
        return try {
            player.setDataSource(url)
            player.prepareAsync()
            player
        } catch (_: Exception) {
            player.release()
            currentSrc = ""
            createBed()
        }
    }

    private fun fallBackToBed() {
        mp?.release(); mp = null
        currentSrc = ""
        if (!pauseRequested) play("")
    }

    private fun mediaAudioAttributes(contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(contentType)
            .build()

    private fun pause() {
        if (prepared) mp?.pause() else pauseRequested = true
        Player.setState(title, false)
        updateSession(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java).notify(NOTIF, buildNotification(false))
    }

    private fun stopAll() {
        timerHandler.removeCallbacksAndMessages(null)
        Player.setTimerState(0)
        mp?.release(); mp = null
        Player.setState(null, false)
        session?.isActive = false; session?.release(); session = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mp?.release(); mp = null
        session?.release(); session = null
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
            Intent(this, AmbientService::class.java).setAction(action),
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
            .setContentTitle(title)
            .setContentText(if (currentSrc.isBlank()) "CereBro · ambient bed" else "CereBro · narration")
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
                NotificationChannel(CHANNEL, "Ambient playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controls for the calming ambient bed."
                    setShowBadge(false)
                },
            )
        }
    }
}
