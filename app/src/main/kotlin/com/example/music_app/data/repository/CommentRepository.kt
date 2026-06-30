package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.AppNotificationTargetType
import com.example.music_app.data.model.enums.AppNotificationType
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.example.music_app.data.model.enums.UserRole
import com.example.music_app.data.remote.CommentRemoteDataSource
import com.example.music_app.data.remote.NotificationRemoteDataSource
import com.example.music_app.data.remote.ReportRemoteDataSource
import com.example.music_app.data.remote.UserRemoteDataSource
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

    suspend fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long = 0L
    ) {
        firebaseComments.addComment(songId, content, timelinePositionMs)
    }

    suspend fun reportComment(
        songId: String,
        comment: Comment,
        reason: String,
        description: String = ""
    ) {
        firebaseComments.reportComment(
            songId = songId,
            commentId = comment.id,
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
    private val remoteDataSource: CommentRemoteDataSource =
        CommentRemoteDataSource(firestore),
    private val userRemoteDataSource: UserRemoteDataSource =
        UserRemoteDataSource(firestore),
    private val notificationRemoteDataSource: NotificationRemoteDataSource =
        NotificationRemoteDataSource(firestore),
    private val reportRemoteDataSource: ReportRemoteDataSource =
        ReportRemoteDataSource(firestore)
) {

    fun getCurrentUserId(): String = auth.currentUser?.uid.orEmpty()

    suspend fun getSong(songId: String): Song? = remoteDataSource.getSong(songId)

    suspend fun getComments(songId: String): List<Comment> =
        remoteDataSource.getAll(songId, getCurrentUserId())

    suspend fun addComment(
        songId: String,
        content: String,
        timelinePositionMs: Long = 0L
    ): Comment {
        val normalizedContent = content.trim()
        val song = requireCommentableSong(songId, normalizedContent)
        val userId = requireCurrentUserId()
        val user = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        return remoteDataSource.add(
            songId = song.id,
            user = user,
            content = normalizedContent,
            timelinePositionMs = timelinePositionMs
        )
    }

    suspend fun reportComment(
        songId: String,
        commentId: String,
        reason: String,
        description: String = ""
    ): Report {
        val userId = requireCurrentUserId()
        val user = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)

        return reportRemoteDataSource.create(
            Report(
                targetId = commentId,
                targetType = ReportTargetType.COMMENT.value,
                reporterId = userId,
                reporterName = user.displayName.ifBlank { user.email },
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
        val currentUser = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.user_not_found)
        val comment = remoteDataSource.getAll(songId)
            .firstOrNull { item -> item.id == commentId }
            ?: throw AppException(R.string.comment_not_found)
        val song = remoteDataSource.getSong(songId)
            ?: throw AppException(R.string.invalid_song)

        val canHide = comment.userId == userId ||
            song.uploaderId == userId ||
            currentUser.roleType == UserRole.ADMIN

        if (!canHide) {
            throw AppException(R.string.no_permission)
        }

        remoteDataSource.softDelete(songId, commentId, userId)
    }

    suspend fun toggleCommentLike(songId: String, comment: Comment): Boolean {
        val actorId = requireCurrentUserId()
        val isLiked = remoteDataSource.toggleLike(songId, comment.id, actorId)

        if (isLiked && comment.userId.isNotBlank() && comment.userId != actorId) {
            val actor = userRemoteDataSource.getById(actorId)
            val actorName = actor?.displayName?.takeIf(String::isNotBlank)
                ?: actor?.email
                ?: auth.currentUser?.displayName
                ?: auth.currentUser?.email
                ?: "Orange Music user"

            // The like transaction is already committed. A temporary
            // notification write problem must not make the UI report the
            // successful like as a failed action or prevent a later unlike.
            runCatching {
                notificationRemoteDataSource.create(
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

    private suspend fun requireCommentableSong(songId: String, content: String): Song {
        if (songId.isBlank()) throw AppException(R.string.invalid_song)
        if (content.isBlank()) throw AppException(R.string.comment_content_empty)

        val song = remoteDataSource.getSong(songId)
            ?: throw AppException(R.string.invalid_song)

        if (song.isDeleted) throw AppException(R.string.song_deleted)
        if (!song.allowComments) throw AppException(R.string.comments_locked)

        return song
    }
}
