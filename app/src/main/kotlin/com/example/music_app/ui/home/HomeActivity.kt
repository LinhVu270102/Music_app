package com.example.music_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Tìm RecyclerView trong layout
        val recyclerView = findViewById<RecyclerView>(R.id.musicFeed)

        // Thiết lập layout manager
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Tạo dữ liệu giả (mock data)
        val songs = listOf(
            Song("Shape of You", "Ed Sheeran", R.drawable.ic_music_note),
            Song("Blinding Lights", "The Weeknd", R.drawable.ic_music_note),
            Song("Levitating", "Dua Lipa", R.drawable.ic_music_note),
            Song("Stay", "Justin Bieber", R.drawable.ic_music_note),
            Song("Peaches", "Justin Bieber", R.drawable.ic_music_note)
        )

        // Gắn adapter
        recyclerView.adapter = SongAdapter(songs)
    }
}
