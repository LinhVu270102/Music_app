package com.example.music_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.widget.TextView

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Gán dữ liệu giả cho profile
        val avatar = findViewById<ImageView>(R.id.profileAvatar)
        val name = findViewById<TextView>(R.id.profileName)
        val email = findViewById<TextView>(R.id.profileEmail)
        val songCount = findViewById<TextView>(R.id.songCount)
        val playlistCount = findViewById<TextView>(R.id.playlistCount)

        avatar.setImageResource(R.drawable.ic_account_circle)
        name.text = "Nguyễn Văn A"
        email.text = "nguyenvana@example.com"
        songCount.text = "Songs: 12"
        playlistCount.text = "Playlists: 3"

        // RecyclerView danh sách nhạc cá nhân
        val recyclerView = findViewById<RecyclerView>(R.id.profileMusicList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val mySongs = listOf(
            Song("My Song 1", "Artist A", R.drawable.ic_music_note),
            Song("My Song 2", "Artist B", R.drawable.ic_music_note),
            Song("My Song 3", "Artist C", R.drawable.ic_music_note),
            Song("My Song 4", "Artist D", R.drawable.ic_music_note)
        )

        recyclerView.adapter = SongAdapter(mySongs)
    }
}
