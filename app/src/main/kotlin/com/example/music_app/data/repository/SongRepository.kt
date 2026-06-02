package com.example.music_app.data.repository

import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.remote.FirebaseService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.music_app.data.model.Comment

class SongRepository {

    private val firebaseService = FirebaseService(FirebaseFirestore.getInstance())
    private val auth = FirebaseAuth.getInstance()

    suspend fun getSong(songId: String): Song? {
        return firebaseService.getSongById(songId)
    }

    suspend fun getAllSongs(): List<Song> {
        return firebaseService.getAllSongsWithIds()
    }

    suspend fun upsertSong(song: Song) {
        firebaseService.upsertSong(song)
    }

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = auth.currentUser?.uid ?: return
        firebaseService.saveRecentlyPlayed(userId, song.id)
    }

    suspend fun getRecentlyPlayedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getRecentlyPlayedSongs(userId)
    }

    suspend fun getCurrentUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return firebaseService.getUserById(userId)
    }

    suspend fun getMyUploadedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getSongsByUploaderId(userId)
    }

    // =========================
    // LIKE SONG
    // =========================

    suspend fun likeSong(song: Song) {
        val userId = auth.currentUser?.uid ?: return
        firebaseService.likeSong(userId, song.id)
    }

    suspend fun unlikeSong(song: Song) {
        val userId = auth.currentUser?.uid ?: return
        firebaseService.unlikeSong(userId, song.id)
    }

    suspend fun isSongLiked(songId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return firebaseService.isSongLiked(userId, songId)
    }

    suspend fun getLikedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getLikedSongs(userId)
    }

    suspend fun toggleLikeSong(song: Song): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        val isLiked = firebaseService.isSongLiked(userId, song.id)

        return if (isLiked) {
            firebaseService.unlikeSong(userId, song.id)
            false
        } else {
            firebaseService.likeSong(userId, song.id)
            true
        }
    }

    // =========================
    // PLAYLIST
    // =========================

    suspend fun createPlaylist(
        name: String,
        description: String = ""
    ): Playlist {
        val userId = auth.currentUser?.uid ?: throw Exception("Bạn chưa đăng nhập")
        return firebaseService.createPlaylist(userId, name, description)
    }

    suspend fun getMyPlaylists(): List<Playlist> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getUserPlaylists(userId)
    }

    suspend fun deletePlaylist(playlistId: String) {
        val userId = auth.currentUser?.uid ?: return
        firebaseService.deletePlaylist(userId, playlistId)
    }

    suspend fun addSongToPlaylist(
        playlistId: String,
        song: Song
    ) {
        val userId = auth.currentUser?.uid ?: throw Exception("Bạn chưa đăng nhập")
        firebaseService.addSongToPlaylist(userId, playlistId, song)
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ) {
        val userId = auth.currentUser?.uid ?: throw Exception("Bạn chưa đăng nhập")
        firebaseService.removeSongFromPlaylist(userId, playlistId, songId)
    }

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getPlaylistSongs(userId, playlistId)
    }

    // =========================
    // FOLLOW USER / ARTIST
    // =========================

    suspend fun isFollowing(targetUserId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        if (targetUserId.isBlank()) {
            return false
        }

        return firebaseService.isFollowing(
            currentUserId = userId,
            targetUserId = targetUserId
        )
    }

    suspend fun toggleFollowUser(targetUserId: String): Boolean {
        val userId = auth.currentUser?.uid ?: throw Exception("Bạn chưa đăng nhập")

        if (targetUserId.isBlank()) {
            throw Exception("Không tìm thấy người dùng để follow")
        }

        if (userId == targetUserId) {
            throw Exception("Không thể follow chính mình")
        }

        val isFollowing = firebaseService.isFollowing(
            currentUserId = userId,
            targetUserId = targetUserId
        )

        return if (isFollowing) {
            firebaseService.unfollowUser(
                currentUserId = userId,
                targetUserId = targetUserId
            )
            false
        } else {
            firebaseService.followUser(
                currentUserId = userId,
                targetUserId = targetUserId
            )
            true
        }
    }
    suspend fun addComment(
        songId: String,
        content: String
    ): Comment {
        val userId = auth.currentUser?.uid ?: throw Exception("Bạn chưa đăng nhập")

        val user = firebaseService.getUserById(userId)
            ?: throw Exception("Không tìm thấy thông tin user")

        return firebaseService.addComment(
            songId = songId,
            user = user,
            content = content
        )
    }

    suspend fun getComments(songId: String): List<Comment> {
        return firebaseService.getComments(songId)
    }

    suspend fun deleteComment(
        songId: String,
        commentId: String
    ) {
        firebaseService.deleteComment(songId, commentId)
    }
    suspend fun getFollowingUsers(): List<User> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getFollowingUsers(userId)
    }
}