package com.example.music_app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.example.music_app.R
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager

@UnstableApi
class MusicService : Service() {

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaSession: MediaSession? = null
    private var playerNotificationManager: PlayerNotificationManager? = null

    override fun onCreate() {
        super.onCreate()

        PlayerManager.init(this)

        val player = PlayerManager.getPlayer() ?: return

        mediaSession = MediaSession.Builder(this, player).build()

        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setChannelNameResourceId(R.string.app_name)
            .setSmallIconResourceId(R.drawable.music_orange)
            .setMediaDescriptionAdapter(createDescriptionAdapter())
            .setNotificationListener(createNotificationListener())
            .build()
            .apply {
                setPlayer(player)
                setUseNextAction(true)
                setUsePreviousAction(true)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createDescriptionAdapter(): PlayerNotificationManager.MediaDescriptionAdapter {
        return object : PlayerNotificationManager.MediaDescriptionAdapter {

            override fun getCurrentContentTitle(player: Player): CharSequence {
                return PlayerManager.currentSong.value?.title ?: getString(R.string.app_name)
            }

            override fun getCurrentContentText(player: Player): CharSequence {
                return PlayerManager.currentSong.value?.artist
                    ?: getString(R.string.now_playing_default_text)
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = Intent(this@MusicService, MainActivity::class.java)

                return PendingIntent.getActivity(
                    this@MusicService,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): Bitmap? {
                return null
            }
        }
    }

    private fun createNotificationListener(): PlayerNotificationManager.NotificationListener {
        return object : PlayerNotificationManager.NotificationListener {

            override fun onNotificationPosted(
                notificationId: Int,
                notification: Notification,
                ongoing: Boolean
            ) {
                startForeground(notificationId, notification)
            }

            override fun onNotificationCancelled(
                notificationId: Int,
                dismissedByUser: Boolean
            ) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        playerNotificationManager?.setPlayer(null)
        mediaSession?.release()

        playerNotificationManager = null
        mediaSession = null

        super.onDestroy()
    }
}