package com.example.music_app

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.music_app.model.Comment

class PlayerActivity : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var handler: Handler
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val cover = findViewById<ImageView>(R.id.playerCover)
        val title = findViewById<TextView>(R.id.playerSongTitle)
        val artist = findViewById<TextView>(R.id.playerArtist)
        val seekBar = findViewById<SeekBar>(R.id.playerSeekBar)
        val currentTime = findViewById<TextView>(R.id.playerCurrentTime)
        val totalTime = findViewById<TextView>(R.id.playerTotalTime)
        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val recyclerView = findViewById<RecyclerView>(R.id.commentList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Gán dữ liệu giả
        cover.setImageResource(R.drawable.ic_music_note)
        title.text = "Demo Song"
        artist.text = "Demo Artist"

        // Demo dữ liệu comment
        val demoComments = mutableListOf(
            Comment("c1", "userA", "song1", "Bài này hay quá!", System.currentTimeMillis(), 2),
            Comment("c2", "userB", "song1", "Beat nghe cuốn thật", System.currentTimeMillis(), 0),
            Comment("c3", "userC", "song1", "Giọng ca sĩ nghe rất ấm", System.currentTimeMillis(), 1)
        // Gắn adapter
         recyclerView.adapter = CommentAdapter(demoComments)
        // MediaPlayer demo (nhạc giả từ raw resource)
        mediaPlayer = MediaPlayer.create(this, R.raw.demo_song) // bạn cần thêm file demo_song.mp3 vào res/raw
        seekBar.max = mediaPlayer.duration

        totalTime.text = formatTime(mediaPlayer.duration)

        handler = Handler(Looper.getMainLooper())

        // Cập nhật SeekBar theo thời gian
        val updateSeekBar = object : Runnable {
            override fun run() {
                if (mediaPlayer.isPlaying) {
                    seekBar.progress = mediaPlayer.currentPosition
                    currentTime.text = formatTime(mediaPlayer.currentPosition)
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateSeekBar)

        // Nút Play/Pause
        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                mediaPlayer.pause()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                mediaPlayer.start()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            isPlaying = !isPlaying
        }

        // Nút Next/Prev (demo: chỉ reset bài hát)
        btnNext.setOnClickListener {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
            isPlaying = true
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }

        btnPrev.setOnClickListener {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
            isPlaying = true
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }

        // SeekBar thay đổi thủ công
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
        handler.removeCallbacksAndMessages(null)
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
