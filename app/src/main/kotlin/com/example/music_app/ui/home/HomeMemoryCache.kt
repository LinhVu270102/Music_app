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

        val hasData =
            currentData.relatedTracks.isNotEmpty() ||
                    currentData.moreLike.isNotEmpty() ||
                    currentData.hotForYou.isNotEmpty() ||
                    currentData.trendingByGenre.isNotEmpty()

        if (!hasData) return false

        return System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
    }

    fun clear() {
        data = null
        cachedAt = 0L
    }
}