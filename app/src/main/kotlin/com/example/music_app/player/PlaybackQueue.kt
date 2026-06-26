package com.example.music_app.player

import com.example.music_app.data.model.Song

/**
 * Mutable playback queue without any Android or ExoPlayer dependency.
 * PlayerManager owns media playback; this class only owns queue ordering and selection.
 */
class PlaybackQueue {

    private val songs = mutableListOf<Song>()

    var currentIndex: Int = -1
        private set

    val size: Int
        get() = songs.size

    val indices: IntRange
        get() = songs.indices

    fun replace(items: List<Song>): List<Song> {
        songs.clear()
        songs.addAll(sanitize(items))
        currentIndex = -1
        return snapshot()
    }

    fun replaceWithSingle(song: Song): List<Song> {
        songs.clear()
        songs.add(song)
        currentIndex = 0
        return snapshot()
    }

    fun select(index: Int): Song? {
        val song = songs.getOrNull(index) ?: return null
        currentIndex = index
        return song
    }

    fun songAt(index: Int): Song? = songs.getOrNull(index)

    fun indexOf(songId: String): Int = songs.indexOfFirst { song -> song.id == songId }

    fun append(items: List<Song>): List<Song> {
        val existingIds = songs.mapTo(mutableSetOf()) { song -> song.id }
        val additions = sanitize(items).filter { song -> existingIds.add(song.id) }

        songs.addAll(additions)
        return additions
    }

    fun snapshot(): List<Song> = songs.toList()

    fun clear() {
        songs.clear()
        currentIndex = -1
    }

    private fun sanitize(items: List<Song>): List<Song> {
        return items
            .filter { song -> song.id.isNotBlank() && song.songUrl.isNotBlank() }
            .distinctBy(Song::id)
    }
}
