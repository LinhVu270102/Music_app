package com.example.music_app.data.remote

import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for song likes and user-follow relationships. */
class SocialRemoteDataSource(
    private val firestore: FirebaseFirestore
) {

    suspend fun likeSong(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val likedRef = likedSong(userId, songId)
        if (likedRef.get().await().exists()) return

        likedRef.set(
            mapOf(
                "songId" to songId,
                "likedAt" to System.currentTimeMillis()
            )
        ).await()
        firestore.collection("songs")
            .document(songId)
            .update("likes", FieldValue.increment(1))
            .await()
    }

    suspend fun unlikeSong(userId: String, songId: String) {
        if (userId.isBlank() || songId.isBlank()) return

        val likedRef = likedSong(userId, songId)
        if (!likedRef.get().await().exists()) return

        likedRef.delete().await()
        firestore.collection("songs")
            .document(songId)
            .update("likes", FieldValue.increment(-1))
            .await()
    }

    suspend fun isSongLiked(userId: String, songId: String): Boolean {
        return userId.isNotBlank() && songId.isNotBlank() && likedSong(userId, songId).get().await().exists()
    }

    suspend fun getLikedSongs(userId: String): List<Song> {
        if (userId.isBlank()) return emptyList()

        val songIds = firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .orderBy("likedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.getString("songId") }

        // A user can retain an old liked-song reference after its song is
        // deleted, hidden, or no longer readable. One inaccessible document
        // must not make the entire Your Likes screen fail to load.
        return songIds.mapNotNull { songId ->
            runCatching { getSong(songId) }.getOrNull()
        }.filter { song ->
            song.status.equals(SongStatus.APPROVED, ignoreCase = true) &&
                !song.isDeleted
        }
    }

    suspend fun followUser(currentUserId: String, targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) return

        val now = System.currentTimeMillis()
        following(currentUserId, targetUserId)
            .set(mapOf("userId" to targetUserId, "followedAt" to now))
            .await()
        followers(targetUserId, currentUserId)
            .set(mapOf("userId" to currentUserId, "followedAt" to now))
            .await()
    }

    suspend fun unfollowUser(currentUserId: String, targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return

        following(currentUserId, targetUserId).delete().await()
        followers(targetUserId, currentUserId).delete().await()
    }

    suspend fun isFollowing(currentUserId: String, targetUserId: String): Boolean {
        return currentUserId.isNotBlank() &&
            targetUserId.isNotBlank() &&
            following(currentUserId, targetUserId).get().await().exists()
    }

    suspend fun getFollowingUsers(userId: String): List<User> {
        if (userId.isBlank()) return emptyList()

        val userIds = firestore.collection("users")
            .document(userId)
            .collection("following")
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.getString("userId") }

        return userIds.mapNotNull { targetUserId ->
            getUser(targetUserId) ?: getSyntheticUserFromUploadedSongs(targetUserId)
        }
    }

    suspend fun getFollowerCount(userId: String): Long {
        if (userId.isBlank()) return 0L

        return firestore.collection("users")
            .document(userId)
            .collection("followers")
            .get()
            .await()
            .size()
            .toLong()
    }

    suspend fun getUser(userId: String): User? {
        if (userId.isBlank()) return null

        val document = firestore.collection("users").document(userId).get().await()
        return document.toObject(User::class.java)?.copy(uid = document.id)
    }

    private suspend fun getSong(songId: String): Song? {
        if (songId.isBlank()) return null

        val document = firestore.collection("songs").document(songId).get().await()
        return document.toObject(Song::class.java)?.copy(id = document.id)
    }

    private suspend fun getSyntheticUserFromUploadedSongs(userId: String): User? {
        if (userId.isBlank()) return null

        val songs = getApprovedSongsByUploaderId(userId)
        val firstSong = songs.firstOrNull() ?: return null
        val artistName = firstSong.artist.ifBlank { userId }

        return User(
            uid = userId,
            displayName = artistName,
            username = artistName,
            avatarUrl = firstSong.coverUrl,
            fullName = artistName,
            uploadedSongsCount = songs.size.toLong()
        )
    }

    private suspend fun getApprovedSongsByUploaderId(userId: String): List<Song> {
        val normalizedSongs = firestore.collection("songs")
            .whereEqualTo("uploaderId", userId)
            .whereEqualTo("status", SongStatus.APPROVED)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(Song::class.java)?.copy(id = document.id)
            }

        val legacySongs = runCatching {
            firestore.collection("songs")
                .whereEqualTo("uploaderId", userId)
                .whereEqualTo("status", "APPROVED")
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(Song::class.java)?.copy(id = document.id)
                }
        }.getOrDefault(emptyList())

        return (normalizedSongs + legacySongs).distinctBy(Song::id)
    }

    private fun likedSong(userId: String, songId: String) = firestore.collection("users")
        .document(userId)
        .collection("likedSongs")
        .document(songId)

    private fun following(userId: String, targetUserId: String) = firestore.collection("users")
        .document(userId)
        .collection("following")
        .document(targetUserId)

    private fun followers(userId: String, followerUserId: String) = firestore.collection("users")
        .document(userId)
        .collection("followers")
        .document(followerUserId)
}
