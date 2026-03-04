package com.example.ungdungphatnhac_buoi8

import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ungdungphatnhac_buoi8.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var musicService: MusicService? = null
    private var isBound = false
    
    private val handler = Handler(Looper.getMainLooper())
    
    // 1. ServiceConnection for HYBRID model (Bound part)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.LocalBinder
            musicService = binder.getService()
            isBound = true
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    // 2. Receiver for status updates from Service (Notification actions)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        startSeekBarUpdate()
    }

    override fun onStart() {
        super.onStart()
        // HYBRID: Start the service first so it stays alive
        val intent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Then bind to it for direct interaction
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MusicService.ACTION_PLAY)
            addAction(MusicService.ACTION_PAUSE)
            addAction(MusicService.ACTION_STOP)
        }
        // Register receiver for Android 14+
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun setupListeners() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.let {
                if (it.isPlaying()) it.pause() else it.play()
                updateUI()
            }
        }

        binding.btnStop.setOnClickListener {
            musicService?.stopMusic()
            finish()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateUI() {
        musicService?.let {
            val isPlaying = it.isPlaying()
            binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            
            val duration = it.getDuration()
            binding.seekBar.max = duration
            binding.tvTotalTime.text = formatTime(duration)
        }
    }

    private fun startSeekBarUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                musicService?.let {
                    if (it.isPlaying()) {
                        val currentPos = it.getCurrentPosition()
                        binding.seekBar.progress = currentPos
                        binding.tvCurrentTime.text = formatTime(currentPos)
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = (milliseconds / 1000) / 60
        val seconds = (milliseconds / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
