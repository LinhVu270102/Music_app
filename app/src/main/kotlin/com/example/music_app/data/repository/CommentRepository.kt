package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.AppNotificationTargetType
import com.example.music_app.data.model.enums.AppNotificationType
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.example.music_app.data.firebase.firestore.CommentFirestoreDataSource
import com.example.music_app.data.firebase.firestore.NotificationFirestoreDataSource
import com.example.music_app.data.firebase.firestore.ReportFirestoreDataSource
import com.example.music_app.data.firebase.firestore.UserFirestoreDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Provides the Firestore comment API for all in-app songs.
 */
class CommentRepository private constructor(
    private val firebaseComments: FirestoreCommentRepository = FirestoreCommentRepository()
) {

    constructor() : this(
        firebaseComments = FirestoreCommentRepository()
    )

    fun getCurrentUserId(): String = firebaseComments.getCurrentUserId()

    suspend fun getSong(songId: String, fallbackSong: Song? = null): Song? {
        return firebaseComments.getSong(songId) ?: fallbackSong
    }

    suspend fun getComments(songId: String): List<Comment> {
        return firebaseComments.getComments(songId)
    }

    suspend fun getCommentCount(songId: String): Long {
        return firebaseComments.getCommentCount(songId)
    }

    suspend fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long = 0L,
        parentCommentId: String = "",
        replyToUserId: String = "",
        replyToDisplayName: String = ""
    ) {
        firebaseComments.addComment(
            songId = songId,
            content = content,
            timelinePositionMs = timelinePositionMs,
            parentCommentId = parentCommentId,
            replyToUserId = replyToUserId,
            replyToDisplayName = replyToDisplayName
        )
    }

    suspend fun reportComment(
        songId: String,
        comment: Comment,
        reason: String,
        description: String = ""
    ) {
        firebaseComments.reportComment(
            songId = songId,
            comment = comment,
            reason = reason,
            description = description.ifBlank { comment.content }
        )
    }

    suspend fun hideComment(songId: String, comment: Comment) {
        firebaseComments.hideComment(songId, comment.id)
    }

    suspend fun toggleCommentLike(songId: String, comment: Comment): Boolean {
        return firebaseComments.toggleCommentLike(songId, comment)
    }

}

/** Handles Firebase comments and comment reports, including permission checks. */
private class FirestoreCommentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firestoreDataSource: CommentFirestoreDataSource =
        CommentFirestoreDataSource(firestore),
    private val userFirestoreDataSource: UserFirestoreDataSource =
        UserFirestoreDataSource(firestore),
    private val notificationFirestoreDataSource: NotificationFirestoreDataSource =
        NotificationFirestoreDataSource(firestore),
    private val reportFirestoreDataSource: ReportFirestoreDataSource =
        ReportFirestoreDataSource(firestore)
) {

    fun getCurrentUserId(): String = auth.currentUser?.uid.orEmpty()

    suspend fun getSong(songId: String): Song? = firestoreDataSource.getSong(songId)

    suspend fun getComments(songId: String): List<Comment> =
        firestoreDataSource.getAll(songId, getCurrentUserId())

    suspend fun getCommentCount(songId: String): Long =
        firestoreDataSource.getVisibleCount(songId)

    suspend fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long = 0L,
        parentCommentId: String = "",
        replyToUserId: String = "",
        replyToDisplayName: String = ""
    ): Comment {
        val normalizedContent = content.trim()
        val song = requireCommentableSong(songId, normalizedContent)
        val userId = requireCurrentUserId()
        val user = getCurrentUserProfile(userId)

        return firestoreDataSource.add(
            songId = song.id,
            user = user,
            content = normalizedContent,
            timelinePositionMs = timelinePositionMs,
            parentCommentId = parentCommentId,
            replyToUserId = replyToUserId,
            replyToDisplayName = replyToDisplayName
        )
    }

    suspend fun reportComment(
        songId: String,
        comment: Comment,
        reason: String,
        description: String = ""
    ): Report {
        val userId = requireCurrentUserId()
        val user = userFirestoreDataSource.getById(userId)
        val reporterName = user?.displayName
            ?.takeIf(String::isNotBlank)
            ?: user?.email?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.displayName?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.email?.takeIf(String::isNotBlank)
            ?: DEFAULT_REPORTER_NAME
        val song = firestoreDataSource.getSong(songId)

        return reportFirestoreDataSource.create(
            Report(
                targetId = comment.id,
                targetType = ReportTargetType.COMMENT.value,
                targetOwnerId = comment.userId,
                songId = songId,
                songOwnerId = song?.uploaderId.orEmpty(),
                targetTitle = comment.displayName.ifBlank { comment.userId },
                targetSubtitle = song?.title.orEmpty(),
                targetPreview = comment.content,
                reporterId = userId,
                reporterName = reporterName,
                reason = reason,
                description = "$songId|$description",
                status = ReportStatus.PENDING.value
            )
        )
    }

    suspend fun hideComment(
        songId: String,
        commentId: String
    ) {
        val userId = requireCurrentUserId()
        val currentUser = getCurrentUserProfile(userId)
        val comment = firestoreDataSource.getAll(songId)
            .firstOrNull { item -> item.id == commentId }
            ?: throw AppException(R.string.comment_not_found)
        val song = firestoreDataSource.getSong(songId)
            ?: throw AppException(R.string.invalid_song)

        val canHide = comment.userId == userId ||
            song.uploaderId == userId ||
            currentUser.roleType.canModerateContent

        if (!canHide) {
            throw AppException(R.string.no_permission)
        }

        firestoreDataSource.softDelete(songId, commentId, userId)
    }

    suspend fun toggleCommentLike(songId: String, comment: Comment): Boolean {
        val actorId = requireCurrentUserId()
        val isLiked = firestoreDataSource.toggleLike(songId, comment.id, actorId)

        if (isLiked && comment.userId.isNotBlank() && comment.userId != actorId) {
            val actor = userFirestoreDataSource.getById(actorId)
            val actorName = actor?.displayName?.takeIf(String::isNotBlank)
                ?: actor?.email
                ?: auth.currentUser?.displayName
                ?: auth.currentUser?.email
                ?: "Orange Music user"

            // The like transaction is already committed. A temporary
            // notification write problem must not make the UI report the
            // successful like as a failed action or prevent a later unlike.
            runCatching {
                notificationFirestoreDataSource.create(
                    AppNotification(
                        receiverId = comment.userId,
                        actorId = actorId,
                        actorName = actorName,
                        actorAvatarUrl = actor?.avatarUrl.orEmpty(),
                        type = AppNotificationType.NEW_LIKE.value,
                        title = "New comment like",
                        message = "$actorName liked your comment",
                        targetId = comment.id,
                        targetType = AppNotificationTargetType.COMMENT.value
                    )
                )
            }
        }

        return isLiked
    }

    private fun requireCurrentUserId(): String {
        return getCurrentUserId().ifBlank {
            throw AppException(R.string.not_logged_in)
        }
    }

    private suspend fun getCurrentUserProfile(userId: String): User {
        val firebaseUser = auth.currentUser

        return userFirestoreDataSource.getById(userId)
            ?: User(
                uid = userId,
                email = firebaseUser?.email.orEmpty(),
                displayName = authDisplayName(),
                avatarUrl = firebaseUser?.photoUrl?.toString().orEmpty()
            )
    }

    private fun authDisplayName(): String {
        return auth.currentUser?.displayName?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.email
                ?.substringBefore("@")
                ?.takeIf(String::isNotBlank)
            ?: DEFAULT_COMMENTER_NAME
    }

    private suspend fun requireCommentableSong(songId: String, content: String): Song {
        if (songId.isBlank()) throw AppException(R.string.invalid_song)
        if (content.isBlank()) throw AppException(R.string.comment_content_empty)

        val song = firestoreDataSource.getSong(songId)
            ?: throw AppException(R.string.invalid_song)

        if (song.isDeleted) throw AppException(R.string.song_deleted)
        if (!song.allowComments) throw AppException(R.string.comments_locked)

        return song
    }

    private companion object {
        private const val DEFAULT_REPORTER_NAME = "Orange Music user"
        private const val DEFAULT_COMMENTER_NAME = "Orange Music user"
    }
}
