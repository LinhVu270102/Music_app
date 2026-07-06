package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.AppNotificationTargetType
import com.example.music_app.data.model.enums.AppNotificationType
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.firebase.firestore.NotificationFirestoreDataSource
import com.example.music_app.data.firebase.firestore.SongFirestoreDataSource
import com.example.music_app.data.firebase.firestore.SocialFirestoreDataSource
import com.example.music_app.data.firebase.firestore.UserFirestoreDataSource
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
    private val firestoreDataSource: SocialFirestoreDataSource = SocialFirestoreDataSource(firestore),
    private val songFirestoreDataSource: SongFirestoreDataSource = SongFirestoreDataSource(firestore),
    private val userFirestoreDataSource: UserFirestoreDataSource = UserFirestoreDataSource(firestore),
    private val notificationFirestoreDataSource: NotificationFirestoreDataSource =
        NotificationFirestoreDataSource(firestore)
) {
    suspend fun isSongLiked(songId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return firestoreDataSource.isSongLiked(userId, songId)
    }

    suspend fun getLikedSongs(): List<Song> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firestoreDataSource.getLikedSongIds(userId)
            .mapNotNull { songId ->
                runCatching { songFirestoreDataSource.getSongById(songId) }.getOrNull()
            }
            .filter { song -> song.isVisibleLikedSong() }
    }

    suspend fun toggleSongLike(song: Song): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        if (!song.canBeLiked()) return false

        if (firestoreDataSource.isSongLiked(userId, song.id)) {
            firestoreDataSource.unlikeSong(userId, song.id)
            return false
        }

        firestoreDataSource.likeSong(userId, song.id)
        createSongLikeNotification(userId, song)
        return true
    }

    suspend fun isFollowing(targetUserId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false
        return firestoreDataSource.isFollowing(currentUserId, targetUserId)
    }

    suspend fun toggleFollow(targetUserId: String): Boolean {
        val currentUser = auth.currentUser ?: throw AppException(R.string.login_required)
        validateFollowTarget(
            currentUserId = currentUser.uid,
            targetUserId = targetUserId
        )

        if (firestoreDataSource.isFollowing(currentUser.uid, targetUserId)) {
            firestoreDataSource.unfollowUser(currentUser.uid, targetUserId)
            return false
        }

        firestoreDataSource.followUser(currentUser.uid, targetUserId)
        createFollowNotification(currentUser.uid, targetUserId)
        return true
    }

    suspend fun getFollowingUsers(): List<User> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return firestoreDataSource.getFollowingUserIds(userId)
            .mapNotNull { targetUserId ->
                userFirestoreDataSource.getById(targetUserId)
                    ?: getSyntheticUserFromUploadedSongs(targetUserId)
            }
    }

    suspend fun getFollowerCount(userId: String): Long {
        return firestoreDataSource.getFollowerCount(userId)
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
        return statusType == SongStatus.APPROVED &&
            !isDeleted &&
            songUrl.isNotBlank()
    }

    private suspend fun createSongLikeNotification(actorId: String, song: Song) {
        val receiverId = song.uploaderId
        if (receiverId.isBlank() || receiverId == actorId) return

        val actor = userFirestoreDataSource.getById(actorId)
        val actorName = actor.displayName()
        val actorAvatarUrl = actor?.avatarUrl
            ?: auth.currentUser?.photoUrl?.toString()
            ?: ""

        notificationFirestoreDataSource.create(
            AppNotification(
                receiverId = receiverId,
                actorId = actorId,
                actorName = actorName,
                actorAvatarUrl = actorAvatarUrl,
                type = AppNotificationType.NEW_LIKE.value,
                title = "New like",
                message = "$actorName liked ${song.title}",
                targetId = song.id,
                targetType = AppNotificationTargetType.SONG.value,
                relatedSongId = song.id
            )
        )
    }

    private suspend fun createFollowNotification(actorId: String, receiverId: String) {
        val actor = userFirestoreDataSource.getById(actorId)
        val actorName = actor.displayName()
        val actorAvatarUrl = actor?.avatarUrl
            ?: auth.currentUser?.photoUrl?.toString()
            ?: ""

        notificationFirestoreDataSource.create(
            AppNotification(
                receiverId = receiverId,
                actorId = actorId,
                actorName = actorName,
                actorAvatarUrl = actorAvatarUrl,
                type = AppNotificationType.NEW_FOLLOWER.value,
                title = "New follower",
                message = "$actorName started following you",
                targetId = actorId,
                targetType = AppNotificationTargetType.USER.value
            )
        )
    }

    private fun User?.displayName(): String {
        return this?.displayName?.takeIf(String::isNotBlank)
            ?: this?.email?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.displayName?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.email?.takeIf(String::isNotBlank)
            ?: "Orange Music user"
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
        val normalizedSongs = songFirestoreDataSource.getApprovedSongsByUploaderId(userId)
        val legacySongs = runCatching {
            songFirestoreDataSource.getLegacyApprovedSongsByUploaderId(userId)
        }.getOrDefault(emptyList())

        return (normalizedSongs + legacySongs).distinctBy(Song::id)
    }
}
