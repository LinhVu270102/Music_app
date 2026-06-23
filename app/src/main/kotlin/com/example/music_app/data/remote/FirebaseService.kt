package com.example.music_app.data.remote

import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.example.music_app.data.model.SongStatus
import com.google.firebase.firestore.FieldPath
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.ReportStatus

class FirebaseService(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val RECENTLY_PLAYED_LIMIT = 20L
    }

    // =========================
    // IN-APP NOTIFICATIONS
    // =========================

    suspend fun createNotification(notification: AppNotification): AppNotification {
        if (notification.receiverId.isBlank()) return notification

        val reference = firestore.collection("users")
            .document(notification.receiverId)
            .collection("notifications")
            .document()

        val notificationWithId = notification.copy(
            id = reference.id,
            createdAt = notification.createdAt.takeIf { it > 0 }
                ?: System.currentTimeMillis()
        )

        reference.set(notificationWithId).await()
        return notificationWithId
    }

    suspend fun getNotifications(userId: String): List<AppNotification> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(AppNotification::class.java)?.copy(id = document.id)
            }
    }

    suspend fun markNotificationRead(userId: String, notificationId: String) {
        if (userId.isBlank() || notificationId.isBlank()) return

        firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .document(notificationId)
            .update("isRead", true)
            .await()
    }

    suspend fun markAllNotificationsRead(userId: String) {
        if (userId.isBlank()) return

        val unreadNotifications = firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .await()

        if (unreadNotifications.isEmpty) return

        firestore.batch().apply {
            unreadNotifications.documents.forEach { document ->
                update(document.reference, "isRead", true)
            }
        }.commit().await()
    }

    // =========================
    // SONG
    // =========================

    suspend fun getSongById(songId: String): Song? {
        if (songId.isBlank()) return null

        val doc = firestore.collection("songs")
            .document(songId)
            .get()
            .await()

        return doc.toObject(Song::class.java)?.copy(id = doc.id)
    }

    suspend fun getAllSongsWithIds(): List<Song> {
        val snapshot = firestore.collection("songs")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }.filter { song ->
            !song.isDeleted
        }
    }

    suspend fun upsertSong(song: Song) {
        if (song.id.isBlank()) {
            throw AppException(R.string.invalid_song)
        }

        firestore.collection("songs")
            .document(song.id)
            .set(song)
            .await()
    }

    suspend fun getSongsByUploaderId(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .whereEqualTo("uploaderId", userId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getApprovedSongsByUploaderId(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .whereEqualTo("uploaderId", userId)
            .whereEqualTo("status", SongStatus.APPROVED)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun deleteSong(songId: String) {
        if (songId.isBlank()) return

        firestore.collection("songs")
            .document(songId)
            .delete()
            .await()
    }

    suspend fun getApprovedSongs(): List<Song> {
        val snapshot = firestore.collection("songs")
            .whereEqualTo("status", SongStatus.APPROVED)
            .whereEqualTo("isDeleted", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getSongsByStatus(status: String): List<Song> {
        if (status.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .whereEqualTo("status", status)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun updateSongStatus(
        songId: String,
        status: String,
        reviewedBy: String,
        rejectReason: String = ""
    ) {
        if (songId.isBlank()) return

        val data = mapOf(
            "status" to status,
            "reviewedBy" to reviewedBy,
            "reviewedAt" to System.currentTimeMillis(),
            "rejectReason" to rejectReason,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("songs")
            .document(songId)
            .update(data)
            .await()
    }

    suspend fun softDeleteSong(
        songId: String,
        deletedBy: String
    ) {
        if (songId.isBlank() || deletedBy.isBlank()) return

        val now = System.currentTimeMillis()

        firestore.collection("songs")
            .document(songId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to now,
                    "deletedBy" to deletedBy,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateSongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        if (songId.isBlank()) return

        firestore.collection("songs")
            .document(songId)
            .set(
                mapOf(
                    "allowComments" to allowComments,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .await()
    }

    // =========================
    // RECENTLY PLAYED
    // =========================

    suspend fun saveRecentlyPlayed(userId: String, song: Song) {
        if (userId.isBlank() || song.id.isBlank()) return

        val data = mapOf(
            "songId" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "coverUrl" to song.coverUrl,
            "songUrl" to song.songUrl,
            "duration" to song.duration,
            "uploaderId" to song.uploaderId,
            "genre" to song.genre,
            "status" to song.status,
            "source" to song.source,
            "soundCloudId" to song.soundCloudId,
            "permalinkUrl" to song.permalinkUrl,
            "streamable" to song.streamable,
            "access" to song.access,
            "playedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .document(song.id)
            .set(data)
            .await()
    }

    suspend fun getRecentlyPlayedSongs(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val recentlySnapshot = firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .orderBy("playedAt", Query.Direction.DESCENDING)
            .limit(RECENTLY_PLAYED_LIMIT)
            .get()
            .await()

        val songIds = recentlySnapshot.documents
            .mapNotNull { document ->
                document.getString("songId") ?: document.id
            }
            .distinct()
            .take(RECENTLY_PLAYED_LIMIT.toInt())

        if (songIds.isEmpty()) return emptyList()

        val songs = mutableListOf<Song>()

        songIds.chunked(10).forEach { ids ->
            val songSnapshot = runCatching {
                firestore.collection("songs")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .await()
            }.getOrNull() ?: return@forEach

            val batchSongs = songSnapshot.documents.mapNotNull { document ->
                document.toObject(Song::class.java)?.copy(id = document.id)
            }

            songs.addAll(batchSongs)
        }

        val songMap = songs.associateBy { song -> song.id }
        val recentlyPlayedMap = recentlySnapshot.documents.associateBy { document ->
            document.getString("songId") ?: document.id
        }

        return songIds
            .mapNotNull { songId ->
                songMap[songId] ?: recentlyPlayedMap[songId]?.toRecentSong()
            }
            .filter { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toRecentSong(): Song? {
        val songId = getString("songId") ?: id
        val title = getString("title").orEmpty()

        if (songId.isBlank() || title.isBlank()) return null

        return Song(
            id = songId,
            title = title,
            artist = getString("artist").orEmpty(),
            coverUrl = getString("coverUrl").orEmpty(),
            songUrl = getString("songUrl").orEmpty(),
            duration = (getLong("duration") ?: 0L).toInt(),
            uploaderId = getString("uploaderId").orEmpty(),
            genre = getString("genre").orEmpty(),
            status = getString("status") ?: SongStatus.APPROVED,
            source = getString("source").orEmpty(),
            soundCloudId = getLong("soundCloudId") ?: 0L,
            permalinkUrl = getString("permalinkUrl").orEmpty(),
            streamable = getBoolean("streamable") ?: false,
            access = getString("access").orEmpty()
        )
    }

    // =========================
    // USER
    // =========================

    suspend fun getUserById(userId: String): User? {
        if (userId.isBlank()) return null

        val doc = firestore.collection("users")
            .document(userId)
            .get()
            .await()

        return doc.toObject(User::class.java)?.copy(uid = doc.id)
    }

    suspend fun updateUserRole(userId: String, role: String) {
        if (userId.isBlank()) return

        val data = mapOf(
            "role" to role
        )

        firestore.collection("users")
            .document(userId)
            .set(data, SetOptions.merge())
            .await()
    }
    suspend fun resubmitSongForReview(songId: String) {
        if (songId.isBlank()) return

        val now = System.currentTimeMillis()
        firestore.collection("songs")
            .document(songId)
            .set(
                mapOf(
                    "status" to SongStatus.PENDING,
                    "rejectReason" to "",
                    "reviewedBy" to "",
                    "reviewedAt" to 0L,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()
    }

    // =========================
    // LIKE SONG
    // =========================

    suspend fun likeSong(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val likedRef = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .document(songId)

        val songRef = firestore.collection("songs")
            .document(songId)

        val likedDoc = likedRef.get().await()

        if (likedDoc.exists()) return

        val data = mapOf(
            "songId" to songId,
            "likedAt" to System.currentTimeMillis()
        )

        likedRef.set(data).await()
        songRef.update("likes", FieldValue.increment(1)).await()
    }

    suspend fun unlikeSong(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val likedRef = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .document(songId)

        val songRef = firestore.collection("songs")
            .document(songId)

        val likedDoc = likedRef.get().await()

        if (!likedDoc.exists()) return

        likedRef.delete().await()
        songRef.update("likes", FieldValue.increment(-1)).await()
    }

    suspend fun isSongLiked(userId: String, songId: String): Boolean {
        if (userId.isBlank() || songId.isBlank()) return false

        val doc = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .document(songId)
            .get()
            .await()

        return doc.exists()
    }

    suspend fun getLikedSongs(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .orderBy("likedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val songIds = snapshot.documents.mapNotNull { doc ->
            doc.getString("songId")
        }

        return songIds.mapNotNull { songId ->
            getSongById(songId)
        }.filter { song ->
            song.status == SongStatus.APPROVED
        }
    }

    // =========================
    // PLAYLIST
    // =========================

    suspend fun createPlaylist(
        userId: String,
        name: String,
        description: String = ""
    ): Playlist {
        if (userId.isBlank()) {
            throw AppException(R.string.invalid_user)
        }

        if (name.isBlank()) {
            throw AppException(R.string.playlist_name_empty)
        }

        val playlistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document()

        val publicPlaylistRef = firestore.collection("playlists")
            .document(playlistRef.id)

        val now = System.currentTimeMillis()

        val playlist = Playlist(
            id = playlistRef.id,
            name = name,
            description = description,
            coverUrl = "",
            ownerId = userId,
            isPublic = true,
            songsCount = 0L,
            createdAt = now,
            updatedAt = now
        )

        firestore.batch()
            .set(playlistRef, playlist)
            // A public mirror makes playlists searchable without exposing the
            // private user subcollection as the application's public index.
            .set(publicPlaylistRef, playlist)
            .commit()
            .await()

        return playlist
    }

    suspend fun getUserPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Playlist::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getPublicUserPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .whereEqualTo("isPublic", true)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Playlist::class.java)?.copy(id = doc.id)
        }.sortedByDescending { playlist ->
            playlist.updatedAt
        }
    }

    suspend fun isPlaylistLiked(userId: String, playlistId: String): Boolean {
        if (userId.isBlank() || playlistId.isBlank()) return false

        return firestore.collection("users")
            .document(userId)
            .collection("likedPlaylists")
            .document(playlistId)
            .get()
            .await()
            .exists()
    }

    suspend fun togglePlaylistLike(userId: String, playlist: Playlist): Boolean {
        if (userId.isBlank() || playlist.id.isBlank()) return false

        val likedPlaylistRef = firestore.collection("users")
            .document(userId)
            .collection("likedPlaylists")
            .document(playlist.id)

        if (likedPlaylistRef.get().await().exists()) {
            likedPlaylistRef.delete().await()
            return false
        }

        likedPlaylistRef.set(
            playlist.copy(updatedAt = System.currentTimeMillis())
        ).await()

        return true
    }

    suspend fun getLikedPlaylists(userId: String): List<Playlist> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("likedPlaylists")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(Playlist::class.java)?.copy(id = document.id)
            }
    }

    suspend fun deletePlaylist(userId: String, playlistId: String) {
        if (userId.isBlank() || playlistId.isBlank()) return

        firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)
            .delete()
            .await()

        firestore.collection("playlists")
            .document(playlistId)
            .delete()
            .await()
    }

    suspend fun addSongToPlaylist(
        userId: String,
        playlistId: String,
        song: Song
    ) {
        if (userId.isBlank() || playlistId.isBlank() || song.id.isBlank()) return

        val songInPlaylistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)
            .collection("songs")
            .document(song.id)

        val playlistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)

        val existedDoc = songInPlaylistRef.get().await()

        if (existedDoc.exists()) return

        val data = mapOf(
            "songId" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "coverUrl" to song.coverUrl,
            "songUrl" to song.songUrl,
            "duration" to song.duration,
            "uploaderId" to song.uploaderId,
            "genre" to song.genre,
            "tags" to song.tags,
            "status" to song.status,
            "source" to song.source,
            "soundCloudId" to song.soundCloudId,
            "permalinkUrl" to song.permalinkUrl,
            "streamable" to song.streamable,
            "access" to song.access,
            "addedAt" to System.currentTimeMillis()
        )

        songInPlaylistRef.set(data).await()

        playlistRef.update(
            mapOf(
                "songsCount" to FieldValue.increment(1),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()

        firestore.collection("playlists")
            .document(playlistId)
            .set(
                mapOf(
                    "songsCount" to FieldValue.increment(1),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun removeSongFromPlaylist(
        userId: String,
        playlistId: String,
        songId: String
    ) {
        if (userId.isBlank() || playlistId.isBlank() || songId.isBlank()) return

        val songInPlaylistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)
            .collection("songs")
            .document(songId)

        val playlistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)

        val existedDoc = songInPlaylistRef.get().await()

        if (!existedDoc.exists()) return

        songInPlaylistRef.delete().await()

        playlistRef.update(
            mapOf(
                "songsCount" to FieldValue.increment(-1),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()

        firestore.collection("playlists")
            .document(playlistId)
            .set(
                mapOf(
                    "songsCount" to FieldValue.increment(-1),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun getPlaylistSongs(
        userId: String,
        playlistId: String
    ): List<Song> {
        if (userId.isBlank() || playlistId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document(playlistId)
            .collection("songs")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Song::class.java)?.copy(id = doc.id)
        }
    }

    // =========================
    // FOLLOW USER
    // =========================

    suspend fun followUser(
        currentUserId: String,
        targetUserId: String
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        if (currentUserId == targetUserId) return

        val followingData = mapOf(
            "userId" to targetUserId,
            "followedAt" to System.currentTimeMillis()
        )

        val followerData = mapOf(
            "userId" to currentUserId,
            "followedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .set(followingData)
            .await()

        firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
            .set(followerData)
            .await()
    }

    suspend fun unfollowUser(
        currentUserId: String,
        targetUserId: String
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return

        firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .delete()
            .await()

        firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
            .delete()
            .await()
    }

    suspend fun isFollowing(
        currentUserId: String,
        targetUserId: String
    ): Boolean {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return false

        val doc = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .get()
            .await()

        return doc.exists()
    }

    suspend fun getFollowingUsers(userId: String): List<User> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("following")
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val userIds = snapshot.documents.mapNotNull { doc ->
            doc.getString("userId")
        }

        return userIds.mapNotNull { targetUserId ->
            getUserById(targetUserId)
        }
    }

    // =========================
    // COMMENT
    // =========================

    suspend fun addComment(
        songId: String,
        user: User,
        content: String,
        timelinePositionMs: Long = 0L
    ): Comment {
        if (songId.isBlank()) {
            throw AppException(R.string.invalid_song)
        }

        if (user.uid.isBlank()) {
            throw AppException(R.string.invalid_user)
        }

        if (content.isBlank()) {
            throw AppException(R.string.comment_content_empty)
        }

        val song = getSongById(songId)
            ?: throw AppException(R.string.invalid_song)

        if (song.isDeleted) {
            throw AppException(R.string.song_deleted)
        }

        if (!song.allowComments) {
            throw AppException(R.string.comments_locked)
        }

        val commentRef = firestore.collection("songs")
            .document(songId)
            .collection("comments")
            .document()

        val now = System.currentTimeMillis()

        val comment = Comment(
            id = commentRef.id,
            songId = songId,
            userId = user.uid,
            displayName = user.displayName.ifBlank { user.email },
            avatarUrl = user.avatarUrl,
            content = content,
            timelinePositionMs = timelinePositionMs,
            createdAt = now,
            updatedAt = now
        )

        commentRef.set(comment).await()

        firestore.collection("songs")
            .document(songId)
            .update("commentsCount", FieldValue.increment(1))
            .await()

        return comment
    }

    suspend fun getComments(songId: String): List<Comment> {
        if (songId.isBlank()) return emptyList()

        val snapshot = firestore.collection("songs")
            .document(songId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Comment::class.java)?.copy(id = doc.id)
        }.filter { comment ->
            !comment.isDeleted
        }
    }

    suspend fun softDeleteComment(
        songId: String,
        commentId: String,
        deletedBy: String
    ) {
        if (songId.isBlank() || commentId.isBlank() || deletedBy.isBlank()) return

        val now = System.currentTimeMillis()

        firestore.collection("songs")
            .document(songId)
            .collection("comments")
            .document(commentId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to now,
                    "deletedBy" to deletedBy,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()

        firestore.collection("songs")
            .document(songId)
            .update("commentsCount", FieldValue.increment(-1))
            .await()
    }

    suspend fun getReportedComments(): List<Comment> {
        val snapshot = firestore.collectionGroup("comments")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val reportsCount = doc.getLong("reportsCount") ?: 0L
            val isDeleted = doc.getBoolean("isDeleted") ?: false

            if (reportsCount <= 0L || isDeleted) {
                return@mapNotNull null
            }

            val parentSongId = doc.reference.parent.parent?.id.orEmpty()

            doc.toObject(Comment::class.java)?.copy(
                id = doc.id,
                songId = parentSongId
            )
        }.sortedByDescending { comment ->
            comment.createdAt
        }
    }
    // =========================
// REPORT
// =========================

    suspend fun createReport(report: Report): Report {
        if (report.targetId.isBlank()) {
            throw AppException(R.string.invalid_report_target)
        }

        if (report.reporterId.isBlank()) {
            throw AppException(R.string.invalid_user)
        }

        if (report.reason.isBlank()) {
            throw AppException(R.string.report_reason_empty)
        }

        val reportRef = firestore.collection("reports").document()
        val now = System.currentTimeMillis()

        val reportWithId = report.copy(
            id = reportRef.id,
            createdAt = now,
            updatedAt = now
        )

        reportRef.set(reportWithId).await()

        when (report.targetType) {
            "song" -> {
                firestore.collection("songs")
                    .document(report.targetId)
                    .update("reportsCount", FieldValue.increment(1))
                    .await()
            }

            "comment" -> {
                if (report.description.isNotBlank()) {
                    val parts = report.description.split("|")
                    val songId = parts.getOrNull(0).orEmpty()

                    if (songId.isNotBlank()) {
                        firestore.collection("songs")
                            .document(songId)
                            .collection("comments")
                            .document(report.targetId)
                            .update("reportsCount", FieldValue.increment(1))
                            .await()
                    }
                }
            }
        }

        return reportWithId
    }

    suspend fun getPendingReports(): List<Report> {
        val snapshot = firestore.collection("reports")
            .whereEqualTo("status", ReportStatus.PENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Report::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun updateReportStatus(
        reportId: String,
        status: String,
        reviewedBy: String,
        adminNote: String = ""
    ) {
        if (reportId.isBlank()) return

        val now = System.currentTimeMillis()

        firestore.collection("reports")
            .document(reportId)
            .set(
                mapOf(
                    "status" to status,
                    "reviewedBy" to reviewedBy,
                    "reviewedAt" to now,
                    "adminNote" to adminNote,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()
    }
}
