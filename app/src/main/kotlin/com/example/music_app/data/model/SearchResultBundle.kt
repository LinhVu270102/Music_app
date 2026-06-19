package com.example.music_app.data.model

data class SearchResultBundle(
    val tracks: List<Song> = emptyList(),
    val profiles: List<User> = emptyList(),
    val playlists: List<Playlist> = emptyList()
) {
    fun isEmpty(): Boolean {
        return tracks.isEmpty() && profiles.isEmpty() && playlists.isEmpty()
    }
}