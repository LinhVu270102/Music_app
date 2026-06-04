package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.UserRole
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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

    /**
     * Public song list.
     * Home/Search/Library chỉ nên hiển thị bài đã được admin duyệt.
     */
    suspend fun getAllSongs(): List<Song> {
        return firebaseService.getAllSongsWithIds()
            .filter { song -> song.status == SongStatus.APPROVED }
    }

    /**
     * Dùng khi tạo/cập nhật bài hát.
     * Nếu user upload bài mới, Song.kt nên mặc định status = pending.
     */
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
            .filter { song -> song.status == SongStatus.APPROVED }
    }

    // =========================
    // USER PROFILE / UPLOAD
    // =========================

    suspend fun getCurrentUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        return firebaseService.getUserById(userId)
    }

    /**
     * Dùng cho màn Your Upload.
     * Chủ tài khoản nên thấy cả pending / approved / rejected.
     */
    suspend fun getMyUploadedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firebaseService.getSongsByUploaderId(userId)
    }

    /**
     * Dùng khi người khác xem profile user.
     * Chỉ hiển thị bài đã được duyệt.
     */
    suspend fun getSongsByUserId(userId: String): List<Song> {
        return firebaseService.getSongsByUploaderId(userId)
            .filter { song -> song.status == SongStatus.APPROVED }
    }

    suspend fun getUserById(userId: String): User? {
        return firebaseService.getUserById(userId)
    }

    // =========================
    // ADMIN MODERATION
    // =========================

    suspend fun getPendingSongs(): List<Song> {
        requireAdmin()

        val snapshot = db.collection("songs")
            .whereEqualTo("status", SongStatus.PENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            document.toObject(Song::class.java)?.copy(id = document.id)
        }
    }

    suspend fun approveSong(songId: String) {
        val adminId = requireAdmin()

        db.collection("songs")
            .document(songId)
            .update(
                mapOf(
                    "status" to SongStatus.APPROVED,
                    "rejectReason" to "",
                    "reviewedBy" to adminId,
                    "reviewedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun rejectSong(
        songId: String,
        reason: String
    ) {
        val adminId = requireAdmin()

        db.collection("songs")
            .document(songId)
            .update(
                mapOf(
                    "status" to SongStatus.REJECTED,
                    "rejectReason" to reason,
                    "reviewedBy" to adminId,
                    "reviewedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    private suspend fun requireAdmin(): String {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.account_not_found)

        if (user.role != UserRole.ADMIN) {
            throw AppException(R.string.no_admin_permission)
        }

        return userId
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
            .filter { song -> song.status == SongStatus.APPROVED }
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
            .filter { song -> song.status == SongStatus.APPROVED }
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
    // COMMENT
    // =========================

    suspend fun addComment(
        songId: String,
        content: String
    ): Comment {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.user_not_found)

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
}