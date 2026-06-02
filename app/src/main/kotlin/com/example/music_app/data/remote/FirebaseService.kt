package com.example.music_app.data.remote

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Comment

class FirebaseService(
    private val firestore: FirebaseFirestore
) {

    suspend fun getSongById(songId: String): Song? {
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
        }
    }

    suspend fun upsertSong(song: Song) {
        firestore.collection("songs")
            .document(song.id)
            .set(song)
            .await()
    }

    suspend fun saveRecentlyPlayed(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val data = mapOf(
            "songId" to songId,
            "playedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .document(songId)
            .set(data)
            .await()
    }

    suspend fun getRecentlyPlayedSongs(
        userId: String,
        limit: Long = 4
    ): List<Song> {
        if (userId.isBlank()) return emptyList()

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("recentlyPlayed")
            .orderBy("playedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        val songIds = snapshot.documents.mapNotNull { doc ->
            doc.getString("songId")
        }

        return songIds.mapNotNull { songId ->
            getSongById(songId)
        }
    }

    suspend fun getUserById(userId: String): User? {
        if (userId.isBlank()) return null

        val doc = firestore.collection("users")
            .document(userId)
            .get()
            .await()

        return doc.toObject(User::class.java)?.copy(uid = doc.id)
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
        }
    }

    suspend fun createPlaylist(
        userId: String,
        name: String,
        description: String = ""
    ): Playlist {
        if (userId.isBlank()) {
            throw Exception("User không hợp lệ")
        }

        if (name.isBlank()) {
            throw Exception("Tên playlist không được để trống")
        }

        val playlistRef = firestore.collection("users")
            .document(userId)
            .collection("playlists")
            .document()

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

        playlistRef.set(playlist).await()

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

    suspend fun deletePlaylist(userId: String, playlistId: String) {
        if (userId.isBlank() || playlistId.isBlank()) return

        firestore.collection("users")
            .document(userId)
            .collection("playlists")
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

        // Nếu bài đã có trong playlist thì không thêm trùng
        if (existedDoc.exists()) return

        val data = mapOf(
            "songId" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "coverUrl" to song.coverUrl,
            "songUrl" to song.songUrl,
            "duration" to song.duration,
            "addedAt" to System.currentTimeMillis()
        )

        songInPlaylistRef.set(data).await()

        playlistRef.update(
            mapOf(
                "songsCount" to FieldValue.increment(1),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
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
    }suspend fun addComment(
        songId: String,
        user: User,
        content: String
    ): Comment {
        if (songId.isBlank()) {
            throw Exception("Bài hát không hợp lệ")
        }

        if (user.uid.isBlank()) {
            throw Exception("User không hợp lệ")
        }

        if (content.isBlank()) {
            throw Exception("Nội dung bình luận không được để trống")
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
            createdAt = now
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
        }
    }

    suspend fun deleteComment(
        songId: String,
        commentId: String
    ) {
        if (songId.isBlank() || commentId.isBlank()) return

        firestore.collection("songs")
            .document(songId)
            .collection("comments")
            .document(commentId)
            .delete()
            .await()

        firestore.collection("songs")
            .document(songId)
            .update("commentsCount", FieldValue.increment(-1))
            .await()
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
}