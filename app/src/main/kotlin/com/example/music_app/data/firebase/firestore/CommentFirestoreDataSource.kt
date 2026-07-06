package com.example.music_app.data.firebase.firestore

import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for comments and comment-moderation queries. */
class CommentFirestoreDataSource(
    private val firestore: FirebaseFirestore
) {

    suspend fun getSong(songId: String): Song? {
        if (songId.isBlank()) return null

        val document = firestore.collection("songs").document(songId).get().await()
        return document.toObject(Song::class.java)?.copy(id = document.id)
    }

    suspend fun add(
        songId: String,
        user: User,
        content: String,
        timelinePositionMs: Long,
        parentCommentId: String = "",
        replyToUserId: String = "",
        replyToDisplayName: String = ""
    ): Comment {
        val commentRef = comments(songId).document()
        val now = System.currentTimeMillis()
        val comment = Comment(
            id = commentRef.id,
            songId = songId,
            userId = user.uid,
            displayName = user.displayName.ifBlank { user.email },
            avatarUrl = user.avatarUrl,
            content = content,
            parentCommentId = parentCommentId,
            replyToUserId = replyToUserId,
            replyToDisplayName = replyToDisplayName,
            timelinePositionMs = timelinePositionMs,
            createdAt = now,
            updatedAt = now
        )

        firestore.batch().apply {
            set(commentRef, comment)
            update(
                firestore.collection("songs").document(songId),
                "commentsCount",
                FieldValue.increment(1)
            )
        }.commit().await()

        return comment
    }

    suspend fun getAll(songId: String): List<Comment> {
        if (songId.isBlank()) return emptyList()

        return comments(songId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(Comment::class.java)?.copy(id = document.id)
            }
            .filterNot(Comment::isDeleted)
            .arrangeThreaded()
    }

    suspend fun getVisibleCount(songId: String): Long {
        if (songId.isBlank()) return 0L

        return comments(songId)
            .get()
            .await()
            .documents
            .count { document ->
                document.getBoolean("isDeleted") != true
            }
            .toLong()
    }

    suspend fun getAll(songId: String, currentUserId: String): List<Comment> {
        val comments = getAll(songId)
        val songOwnerId = getSong(songId)?.uploaderId.orEmpty()
        val songOwnerAvatarUrl = getUserAvatarUrl(songOwnerId)

        return comments.map { comment ->
            comment.copy(
                isLikedByCurrentUser = isCommentLikedBy(
                    songId = songId,
                    commentId = comment.id,
                    userId = currentUserId
                ),
                isLikedBySongOwner = isCommentLikedBy(
                    songId = songId,
                    commentId = comment.id,
                    userId = songOwnerId
                ),
                songOwnerAvatarUrl = songOwnerAvatarUrl
            )
        }
    }

    suspend fun softDelete(songId: String, commentId: String, deletedBy: String) {
        if (songId.isBlank() || commentId.isBlank() || deletedBy.isBlank()) return

        val now = System.currentTimeMillis()
        val songRef = firestore.collection("songs").document(songId)
        val commentRef = comments(songId).document(commentId)

        firestore.runTransaction { transaction ->
            val commentSnapshot = transaction.get(commentRef)

            if (!commentSnapshot.exists() || commentSnapshot.getBoolean("isDeleted") == true) {
                return@runTransaction
            }

            val songSnapshot = transaction.get(songRef)
            val currentCount = songSnapshot.getLong("commentsCount") ?: 0L

            transaction.set(
                commentRef,
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to now,
                    "deletedBy" to deletedBy,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            transaction.update(songRef, "commentsCount", (currentCount - 1L).coerceAtLeast(0L))
        }.await()
    }

    suspend fun toggleLike(songId: String, commentId: String, userId: String): Boolean {
        if (songId.isBlank() || commentId.isBlank() || userId.isBlank()) {
            throw AppException(R.string.invalid_user)
        }

        val commentRef = comments(songId).document(commentId)
        val likeRef = commentLike(songId, commentId, userId)

        return firestore.runTransaction { transaction ->
            val commentSnapshot = transaction.get(commentRef)
            if (!commentSnapshot.exists() || commentSnapshot.getBoolean("isDeleted") == true) {
                throw AppException(R.string.comment_not_found)
            }

            val isLiked = transaction.get(likeRef).exists()
            val currentCount = commentSnapshot.getLong("likesCount") ?: 0L

            if (isLiked) {
                transaction.delete(likeRef)
                transaction.update(commentRef, "likesCount", (currentCount - 1L).coerceAtLeast(0L))
                false
            } else {
                transaction.set(
                    likeRef,
                    mapOf("userId" to userId, "likedAt" to System.currentTimeMillis())
                )
                transaction.update(commentRef, "likesCount", currentCount + 1L)
                true
            }
        }.await()
    }

    suspend fun getReported(): List<Comment> {
        return firestore.collectionGroup("comments")
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                val reportsCount = document.getLong("reportsCount") ?: 0L
                val isDeleted = document.getBoolean("isDeleted") ?: false
                if (reportsCount <= 0L || isDeleted) return@mapNotNull null

                document.toObject(Comment::class.java)?.copy(
                    id = document.id,
                    songId = document.reference.parent.parent?.id.orEmpty()
                )
            }
            .sortedByDescending(Comment::createdAt)
    }

    private fun comments(songId: String) = firestore.collection("songs")
        .document(songId)
        .collection("comments")

    private fun commentLike(songId: String, commentId: String, userId: String) = comments(songId)
        .document(commentId)
        .collection("likes")
        .document(userId)

    private suspend fun isCommentLikedBy(
        songId: String,
        commentId: String,
        userId: String
    ): Boolean {
        if (songId.isBlank() || commentId.isBlank() || userId.isBlank()) return false

        return runCatching {
            commentLike(songId, commentId, userId)
                .get()
                .await()
                .exists()
        }.getOrDefault(false)
    }

    private suspend fun getUserAvatarUrl(userId: String): String {
        if (userId.isBlank()) return ""

        return runCatching {
            firestore.collection("users")
                .document(userId)
                .get()
                .await()
                .getString("avatarUrl")
                .orEmpty()
        }.getOrDefault("")
    }

    private fun List<Comment>.arrangeThreaded(): List<Comment> {
        val rootComments = filter { comment -> comment.parentCommentId.isBlank() }
            .sortedByDescending { comment -> comment.createdAt }
        val rootCommentIds = rootComments.map { comment -> comment.id }.toSet()
        val repliesByParent = filter { comment -> comment.parentCommentId.isNotBlank() }
            .groupBy { comment -> comment.parentCommentId }

        return buildList {
            rootComments.forEach { rootComment ->
                add(rootComment)
                addAll(
                    repliesByParent[rootComment.id]
                        .orEmpty()
                        .sortedBy { reply -> reply.createdAt }
                )
            }

            addAll(
                filter { comment ->
                    comment.parentCommentId.isNotBlank() &&
                        comment.parentCommentId !in rootCommentIds
                }.sortedByDescending { comment -> comment.createdAt }
            )
        }
    }
}
