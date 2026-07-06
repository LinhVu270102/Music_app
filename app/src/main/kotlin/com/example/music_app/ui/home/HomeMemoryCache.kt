package com.example.music_app.ui.home

import com.example.music_app.data.model.Song

data class HomeData(
    val relatedTracks: List<Song> = emptyList(),
    val moreLike: List<Song> = emptyList(),
    val hotForYou: List<Song> = emptyList(),
    val trendingByGenre: List<Song> = emptyList()
)

object HomeMemoryCache {

    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private var cachedAt: Long = 0L

    var data: HomeData? = null
        private set

    fun save(newData: HomeData) {
        data = newData
        cachedAt = System.currentTimeMillis()
    }

    fun isValid(): Boolean {
        val currentData = data ?: return false
        val songs = currentData.allSongs()

        if (songs.isEmpty()) return false
        if (songs.any { song -> !song.isPlayableHomeSong() }) return false

        return System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
    }

    fun clear() {
        data = null
        cachedAt = 0L
    }

    private fun HomeData.allSongs(): List<Song> {
        return relatedTracks + moreLike + hotForYou + trendingByGenre
    }

    private fun Song.isPlayableHomeSong(): Boolean {
        return id.isNotBlank() && songUrl.isNotBlank() && !isDeleted
    }
}
