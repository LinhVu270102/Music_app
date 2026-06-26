package com.example.music_app.player

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import kotlin.random.Random

/** Pure rules for selecting eligible songs for fallback and random queue extensions. */
object PlaybackSongSelector {

    fun sanitizeFallbackSongs(songs: List<Song>): List<Song> {
        return songs
            .filter(::isEligibleFallbackSong)
            .distinctBy(Song::id)
    }

    fun selectRandomFallback(
        songs: List<Song>,
        currentSongId: String,
        excludedSongIds: Set<String> = emptySet(),
        random: Random = Random.Default
    ): Song? {
        val eligibleSongs = songs.filter(::isEligibleFallbackSong)
        val preferredSongs = eligibleSongs.filter { song ->
            song.id != currentSongId && song.id !in excludedSongIds
        }
        val alternatives = preferredSongs.ifEmpty {
            eligibleSongs.filter { song -> song.id != currentSongId }
        }

        return alternatives.randomOrNull(random)
    }

    fun selectRandomTail(
        songs: List<Song>,
        queuedSongIds: Set<String>,
        limit: Int,
        random: Random = Random.Default
    ): List<Song> {
        return songs
            .filter { song -> song.id.isNotBlank() }
            .filter { song -> song.id !in queuedSongIds }
            .filter(::isEligibleFallbackSong)
            .shuffled(random)
            .take(limit)
    }

    private fun isEligibleFallbackSong(song: Song): Boolean {
        return !song.isDeleted &&
                song.status == SongStatus.APPROVED &&
                song.songUrl.isNotBlank()
    }
}
