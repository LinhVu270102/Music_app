package com.example.music_app.data.firebase.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for song likes and user-follow relationships. */
class SocialFirestoreDataSource(
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

    suspend fun getLikedSongIds(userId: String): List<String> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("likedSongs")
            .orderBy("likedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.getString("songId") }
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

    suspend fun getFollowingUserIds(userId: String): List<String> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("following")
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.getString("userId") }
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
