package com.example.music_app.model
import com.example.music_app.data.model.Song

data class Playlist(
    val id: String,
    val name: String,
    val coverResId: Int,
    val songs: List<Song>,
    var likes: Int = 0         // Số lượt thích playlist
)


