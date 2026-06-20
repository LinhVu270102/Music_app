package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.ReportStatus
import com.example.music_app.data.model.ReportTargetType
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.UserRole
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class SongRepository {

    private val db = FirebaseFirestore.getInstance()
    private val firebaseService = FirebaseService(db)
    private val auth = FirebaseAuth.getInstance()

    // =========================
    // SONG BASIC
    // =========================

    suspend fun getSong(songId: String): Song? {
        return firebaseService.getSongById(songId)
    }

    suspend fun getAllSongs(): List<Song> {
        return firebaseService.getAllSongsWithIds()
            .filter { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            }
    }


    suspend fun upsertSong(song: Song) {
        firebaseService.upsertSong(song)
    }

    suspend fun saveRecentlyPlayed(song: Song) {
        val userId = auth.currentUser?.uid ?: return

        if (song.id.isBlank()) return
        if (song.isDeleted) return

        firebaseService.saveRecentlyPlayed(userId, song.id)
    }

    suspend fun getRecentlyPlayedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return firebaseService.getRecentlyPlayedSongs(userId)
            .filter { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            }
    }

    // =========================
    // USER PROFILE / UPLOAD
    // =========================

    suspend fun getCurrentUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return firebaseService.getUserById(userId)
    }


    suspend fun getMyUploadedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getSongsByUploaderId(userId)
    }


    suspend fun softDeleteMySong(songId: String) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val song = firebaseService.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val currentUser = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

        val isOwner = song.uploaderId == userId
        val isAdmin = currentUser.role == UserRole.ADMIN

        if (!isOwner && !isAdmin) {
            throw AppException(R.string.no_permission)
        }

        firebaseService.softDeleteSong(
            songId = songId,
            deletedBy = userId
        )
    }

    suspend fun updateMySongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val song = firebaseService.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val currentUser = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

        val isOwner = song.uploaderId == userId
        val isAdmin = currentUser.role == UserRole.ADMIN

        if (!isOwner && !isAdmin) {
            throw AppException(R.string.no_permission)
        }

        firebaseService.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }


    suspend fun getSongsByUserId(userId: String): List<Song> {
        return firebaseService.getSongsByUploaderId(userId)
            .filter { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            }
    }

    suspend fun getUserById(userId: String): User? {
        return firebaseService.getUserById(userId)
    }

    // =========================
    // LIKE SONG
    // =========================

    suspend fun likeSong(song: Song) {
        val userId = auth.currentUser?.uid ?: return

        if (song.id.isBlank()) return
        if (song.isDeleted) return

        firebaseService.likeSong(userId, song.id)
    }

    suspend fun unlikeSong(song: Song) {
        val userId = auth.currentUser?.uid ?: return

        if (song.id.isBlank()) return

        firebaseService.unlikeSong(userId, song.id)
    }

    suspend fun isSongLiked(songId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return firebaseService.isSongLiked(userId, songId)
    }

    suspend fun getLikedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return firebaseService.getLikedSongs(userId)
            .filter { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            }
    }

    suspend fun toggleLikeSong(song: Song): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        if (song.id.isBlank()) return false
        if (song.isDeleted) return false

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
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

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
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
        }

        firebaseService.addSongToPlaylist(userId, playlistId, song)
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        firebaseService.removeSongFromPlaylist(userId, playlistId, songId)
    }

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()

        return firebaseService.getPlaylistSongs(userId, playlistId)
            .filter { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            }
    }

    // =========================
    // FOLLOW USER
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
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        if (targetUserId.isBlank()) {
            throw AppException(R.string.target_user_not_found)
        }

        if (userId == targetUserId) {
            throw AppException(R.string.cannot_follow_yourself)
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

    suspend fun getFollowingUsers(): List<User> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getFollowingUsers(userId)
    }

    // =========================
    // REPORT
    // =========================


    suspend fun reportSong(
        songId: String,
        reason: String,
        description: String = ""
    ): Report {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

        val song = firebaseService.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
        }

        val report = Report(
            targetId = songId,
            targetType = ReportTargetType.SONG,
            reporterId = userId,
            reporterName = user.displayName.ifBlank { user.email },
            reason = reason,
            description = description,
            status = ReportStatus.PENDING
        )

        return firebaseService.createReport(report)
    }


    suspend fun reportComment(
        songId: String,
        commentId: String,
        reason: String,
        description: String = ""
    ): Report {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

        val report = Report(
            targetId = commentId,
            targetType = ReportTargetType.COMMENT,
            reporterId = userId,
            reporterName = user.displayName.ifBlank { user.email },
            reason = reason,
            description = "$songId|$description",
            status = ReportStatus.PENDING
        )

        return firebaseService.createReport(report)
    }

    // =========================
    // COMMENT
    // =========================

    suspend fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long = 0L
    ): Comment {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

        return firebaseService.addComment(
            songId = songId,
            user = user,
            content = content,
            timelinePositionMs = timelinePositionMs
        )
    }

    suspend fun getComments(songId: String): List<Comment> {
        return firebaseService.getComments(songId)
    }

    suspend fun softDeleteComment(
        songId: String,
        commentId: String
    ) {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val currentUser = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

        val comments = firebaseService.getComments(songId)

        val comment = comments.firstOrNull { it.id == commentId }
            ?: throw AppException(R.string.comment_not_found)

        val song = firebaseService.getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        val isCommentOwner = comment.userId == userId
        val isSongOwner = song.uploaderId == userId
        val isAdmin = currentUser.role == UserRole.ADMIN

        if (!isCommentOwner && !isSongOwner && !isAdmin) {
            throw AppException(R.string.no_permission)
        }

        firebaseService.softDeleteComment(
            songId = songId,
            commentId = commentId,
            deletedBy = userId
        )
    }
}