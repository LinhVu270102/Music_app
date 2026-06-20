package com.example.music_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createInitialNotification()
        )

        PlayerManager.init(applicationContext)

        val player = PlayerManager.getPlayer() ?: return

        val contentIntent = Intent(this, MainActivity::class.java)

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val session = MediaSession.Builder(this, player)
            .setSessionActivity(contentPendingIntent)
            .build()

        mediaSession = session

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

                setUsePlayPauseActions(true)

                setUseNextAction(true)
                setUsePreviousAction(true)

                setUseNextActionInCompactView(true)
                setUsePreviousActionInCompactView(true)

                setUseFastForwardAction(false)
                setUseRewindAction(false)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createInitialNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.music_orange)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.now_playing_default_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createDescriptionAdapter(): PlayerNotificationManager.MediaDescriptionAdapter {
        return object : PlayerNotificationManager.MediaDescriptionAdapter {

            override fun getCurrentContentTitle(player: Player): CharSequence {
                return PlayerManager.currentSong.value?.title
                    ?: getString(R.string.app_name)
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
                PlayerManager.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.now_playing_default_text)
            setShowBadge(false)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        playerNotificationManager?.setPlayer(null)
        mediaSession?.release()

        playerNotificationManager = null
        mediaSession = null

        super.onDestroy()
    }
}