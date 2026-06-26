package com.example.music_app.data.repository

import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.AppNotificationType
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.remote.NotificationRemoteDataSource
import com.example.music_app.data.remote.SocialRemoteDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single entry point for social features: likes, follow state and follower lists.
 * All likes and follows are isolated here so callers do not depend on SongRepository.
 */
class SocialRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val remoteDataSource: SocialRemoteDataSource = SocialRemoteDataSource(firestore),
    private val notificationRemoteDataSource: NotificationRemoteDataSource =
        NotificationRemoteDataSource(firestore)
) {
    suspend fun isSongLiked(songId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return remoteDataSource.isSongLiked(userId, songId)
    }

    suspend fun getLikedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return remoteDataSource.getLikedSongs(userId)
            .filter { song ->
                song.status.equals(SongStatus.APPROVED, ignoreCase = true) &&
                    !song.isDeleted
            }
    }

    suspend fun toggleSongLike(song: Song): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        if (song.id.isBlank() || song.isDeleted) return false

        if (remoteDataSource.isSongLiked(userId, song.id)) {
            remoteDataSource.unlikeSong(userId, song.id)
            return false
        }

        remoteDataSource.likeSong(userId, song.id)
        createSongLikeNotification(userId, song)
        return true
    }

    suspend fun isFollowing(userId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        return remoteDataSource.isFollowing(currentUserId, userId)
    }

    suspend fun toggleFollow(userId: String): Boolean {
        val currentUser = auth.currentUser ?: throw AppException(com.example.music_app.R.string.login_required)
        if (userId.isBlank()) throw AppException(com.example.music_app.R.string.invalid_user)
        if (userId == currentUser.uid) throw AppException(com.example.music_app.R.string.cannot_follow_yourself)

        if (remoteDataSource.isFollowing(currentUser.uid, userId)) {
            remoteDataSource.unfollowUser(currentUser.uid, userId)
            return false
        }

        remoteDataSource.followUser(currentUser.uid, userId)
        createFollowNotification(currentUser.uid, userId)
        return true
    }

    suspend fun getFollowingUsers(): List<User> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return remoteDataSource.getFollowingUsers(userId)
    }

    suspend fun getFollowerCount(userId: String): Long {
        return remoteDataSource.getFollowerCount(userId)
    }

    private suspend fun createSongLikeNotification(actorId: String, song: Song) {
        val receiverId = song.uploaderId
        if (receiverId.isBlank() || receiverId == actorId) return

        val actor = remoteDataSource.getUser(actorId)
        val actorName = actor?.displayName?.takeIf(String::isNotBlank)
            ?: actor?.email
            ?: auth.currentUser?.displayName
            ?: auth.currentUser?.email
            ?: "Orange Music user"

        notificationRemoteDataSource.create(
            AppNotification(
                receiverId = receiverId,
                actorId = actorId,
                actorName = actorName,
                actorAvatarUrl = actor?.avatarUrl.orEmpty(),
                type = AppNotificationType.NEW_LIKE,
                title = "New like",
                message = "$actorName liked ${song.title}",
                targetId = song.id,
                targetType = "song"
            )
        )
    }

    private suspend fun createFollowNotification(actorId: String, receiverId: String) {
        val actor = remoteDataSource.getUser(actorId)
        val actorName = actor?.displayName?.takeIf(String::isNotBlank)
            ?: actor?.email
            ?: auth.currentUser?.email
            ?: "Orange Music user"

        notificationRemoteDataSource.create(
            AppNotification(
                receiverId = receiverId,
                actorId = actorId,
                actorName = actorName,
                actorAvatarUrl = actor?.avatarUrl.orEmpty(),
                type = AppNotificationType.NEW_FOLLOWER,
                title = "New follower",
                message = "$actorName started following you",
                targetId = actorId,
                targetType = "user"
            )
        )
    }
}
