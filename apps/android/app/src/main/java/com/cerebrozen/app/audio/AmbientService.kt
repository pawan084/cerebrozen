package com.cerebrozen.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.cerebrozen.app.MainActivity
import com.cerebrozen.app.R

/**
 * Plays the looping ambient bed as a foreground service with a MediaStyle
 * notification (play / pause / stop + lock-screen transport), so calm audio
 * keeps going when the app is backgrounded. The [Player] object is the thin
 * controller; this service owns the actual MediaPlayer + MediaSession.
 */
class AmbientService : Service() {
    companion object {
        const val ACTION_PLAY = "com.cerebrozen.app.PLAY"
        const val ACTION_PAUSE = "com.cerebrozen.app.PAUSE"
        const val ACTION_STOP = "com.cerebrozen.app.STOP"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "ambient_playback"
        private const val NOTIF = 77
    }

    private var mp: MediaPlayer? = null
    private var session: MediaSession? = null
    private var title = "Ambient bed"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        session = MediaSession(this, "cerebro-ambient").apply { isActive = true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> { intent.getStringExtra(EXTRA_TITLE)?.let { title = it }; play() }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopAll()
        }
        return START_STICKY
    }

    private fun play() {
        if (mp == null) mp = MediaPlayer.create(this, R.raw.ambient_bed)?.apply { isLooping = true }
        mp?.start()
        Player.setState(title, true)
        updateSession(true)
        ServiceCompat.startForeground(
            this, NOTIF, buildNotification(true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun pause() {
        mp?.pause()
        Player.setState(title, false)
        updateSession(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java).notify(NOTIF, buildNotification(false))
    }

    private fun stopAll() {
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
            .setContentText("CereBro · ambient bed")
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
