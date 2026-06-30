package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.AppNotificationTargetType
import com.example.music_app.data.model.enums.AppNotificationType
import com.example.music_app.data.model.enums.SongStatus
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
            .filter { song -> song.isVisibleLikedSong() }
    }

    suspend fun toggleSongLike(song: Song): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        if (!song.canBeLiked()) return false

        if (remoteDataSource.isSongLiked(userId, song.id)) {
            remoteDataSource.unlikeSong(userId, song.id)
            return false
        }

        remoteDataSource.likeSong(userId, song.id)
        createSongLikeNotification(userId, song)
        return true
    }

    suspend fun isFollowing(targetUserId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        return remoteDataSource.isFollowing(currentUserId, targetUserId)
    }

    suspend fun toggleFollow(targetUserId: String): Boolean {
        val currentUser = auth.currentUser ?: throw AppException(R.string.login_required)
        validateFollowTarget(
            currentUserId = currentUser.uid,
            targetUserId = targetUserId
        )

        if (remoteDataSource.isFollowing(currentUser.uid, targetUserId)) {
            remoteDataSource.unfollowUser(currentUser.uid, targetUserId)
            return false
        }

        remoteDataSource.followUser(currentUser.uid, targetUserId)
        createFollowNotification(currentUser.uid, targetUserId)
        return true
    }

    suspend fun getFollowingUsers(): List<User> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return remoteDataSource.getFollowingUsers(userId)
    }

    suspend fun getFollowerCount(userId: String): Long {
        return remoteDataSource.getFollowerCount(userId)
    }

    private fun validateFollowTarget(currentUserId: String, targetUserId: String) {
        if (targetUserId.isBlank()) {
            throw AppException(R.string.invalid_user)
        }

        if (targetUserId == currentUserId) {
            throw AppException(R.string.cannot_follow_yourself)
        }
    }

    private fun Song.canBeLiked(): Boolean {
        return id.isNotBlank() && isVisibleLikedSong()
    }

    private fun Song.isVisibleLikedSong(): Boolean {
        return statusType == SongStatus.APPROVED && !isDeleted
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
                type = AppNotificationType.NEW_LIKE.value,
                title = "New like",
                message = "$actorName liked ${song.title}",
                targetId = song.id,
                targetType = AppNotificationTargetType.SONG.value
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
                type = AppNotificationType.NEW_FOLLOWER.value,
                title = "New follower",
                message = "$actorName started following you",
                targetId = actorId,
                targetType = AppNotificationTargetType.USER.value
            )
        )
    }
}
