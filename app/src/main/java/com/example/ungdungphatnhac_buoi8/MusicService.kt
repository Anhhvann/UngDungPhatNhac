package com.example.ungdungphatnhac_buoi8

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val CHANNEL_ID = "MusicServiceChannel"
    private val NOTIFICATION_ID = 1
    private lateinit var mediaSession: MediaSessionCompat
    
    // Binder for Hybrid Service
    private val binder = LocalBinder()

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_NEXT = "ACTION_NEXT"
        private const val TAG = "MusicService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MusicServiceSession")
        
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        try {
            val resId = resources.getIdentifier("music_sample", "raw", packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId)
                mediaPlayer?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    isLooping = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer init error", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopMusic()
            ACTION_PREVIOUS -> Log.d(TAG, "Previous clicked")
            ACTION_NEXT -> Log.d(TAG, "Next clicked")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    // --- Public Methods for Bound Activity ---

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startForeground(NOTIFICATION_ID, createNotification(true))
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                // Update notification but keep it in foreground (or allow swiping away)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, createNotification(false))
                
                // Allow notification to be swiped if paused
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            }
        }
    }

    fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(pos: Int) { mediaPlayer?.seekTo(pos) }

    // --- Private Helpers ---

    private fun createNotification(isPlaying: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Actions for Notification
        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.ic_pause, "Pause", pausePendingIntent)
        } else {
            val playIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY }
            val playPendingIntent = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.ic_play, "Play", playPendingIntent)
        }

        val stopIntent = Intent(this, MusicService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Người Mẫu")
            .setContentText("Lý Vinh Hạo")
            .setSmallIcon(R.drawable.ic_play)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_lyvinhhao))
            .setOngoing(isPlaying)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Previous", nextPendingIntent) // Reuse icon for demo
            .addAction(playPauseAction)
            .addAction(R.drawable.ic_stop, "Next", nextPendingIntent)
            .setStyle(MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.sessionToken))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Music Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music Control Notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }
}
