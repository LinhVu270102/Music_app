package com.example.music_app.player

import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.MusicInteractionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Persists the most recently started song without blocking playback. */
class PlaybackHistoryRecorder(
    private val repository: MusicInteractionRepository,
    private val scope: CoroutineScope
) {

    private var lastRecordedKey = ""

    fun record(
        song: Song,
        context: PlaybackContext? = null
    ) {
        if (song.id.isBlank()) return

        val key = "${song.id}:${context?.playlistId.orEmpty()}"
        if (key == lastRecordedKey) return

        lastRecordedKey = key
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { repository.saveRecentlyPlayed(song) }
                runCatching {
                    repository.saveRecentlyPlayedPlaylist(
                        context.toPlaylist(song)
                    )
                }
            }
        }
    }

    fun clear() {
        lastRecordedKey = ""
    }

    private fun PlaybackContext?.toPlaylist(song: Song): Playlist? {
        val playbackContext = this?.takeIf { context -> context.isPlaylist }
            ?: return null

        return Playlist(
            id = playbackContext.playlistId,
            name = playbackContext.playlistName,
            coverUrl = playbackContext.playlistCoverUrl.ifBlank { song.coverUrl },
            ownerId = playbackContext.playlistOwnerId,
            isPublic = true,
            updatedAt = System.currentTimeMillis()
        )
    }
}
