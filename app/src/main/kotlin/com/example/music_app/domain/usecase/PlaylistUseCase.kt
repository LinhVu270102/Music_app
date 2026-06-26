package com.example.music_app.domain.usecase

import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.PlaylistRepository

/** Business rules for reading and changing a playlist. */
class PlaylistUseCase(
    private val playlistRepository: PlaylistRepository = PlaylistRepository()
) {

    fun isCurrentUserOwner(
        ownerId: String
    ): Boolean {
        val currentUserId = playlistRepository.getCurrentUserId()
        return currentUserId.isNotBlank() && currentUserId == ownerId.ifBlank { currentUserId }
    }

    suspend fun getPlaylistSongs(
        playlistId: String,
        ownerId: String
    ): List<Song> {
        val currentUserId = playlistRepository.getCurrentUserId()
        val isCurrentUserPlaylist = ownerId.isBlank() || ownerId == currentUserId

        // The owner's playlist path is the source of truth for edit actions.
        // The root collection is only a public/search mirror and may be stale
        // if legacy data or Firestore rules block mirror sync.
        return if (isCurrentUserPlaylist) {
            playlistRepository.getPlaylistSongs(ownerId, playlistId)
                .ifEmpty { playlistRepository.getRootPlaylistSongs(playlistId) }
        } else {
            playlistRepository.getRootPlaylistSongs(playlistId)
                .ifEmpty { playlistRepository.getPlaylistSongs(ownerId, playlistId) }
        }
    }

    suspend fun isPlaylistLiked(playlistId: String): Boolean {
        return playlistRepository.isPlaylistLiked(playlistId)
    }

    suspend fun togglePlaylistLike(playlist: Playlist): Boolean {
        return playlistRepository.togglePlaylistLike(playlist)
    }

    suspend fun addSong(
        playlistId: String,
        song: Song
    ) {
        playlistRepository.addSongToPlaylist(playlistId, song)
    }

    suspend fun removeSong(
        playlistId: String,
        songId: String,
        ownerId: String
    ) {
        playlistRepository.removeSongFromPlaylist(playlistId, songId)
    }
}
