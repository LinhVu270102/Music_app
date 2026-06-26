package com.example.music_app.data.remote

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
class CommentRemoteDataSource(
    private val firestore: FirebaseFirestore
) {

    suspend fun getSong(songId: String): Song? {
        if (songId.isBlank()) return null

        val document = firestore.collection("songs").document(songId).get().await()
        return document.toObject(Song::class.java)?.copy(id = document.id)
    }

    suspend fun add(songId: String, user: User, content: String, timelinePositionMs: Long): Comment {
        if (songId.isBlank()) throw AppException(R.string.invalid_song)
        if (user.uid.isBlank()) throw AppException(R.string.invalid_user)
        if (content.isBlank()) throw AppException(R.string.comment_content_empty)

        val song = getSong(songId) ?: throw AppException(R.string.invalid_song)
        if (song.isDeleted) throw AppException(R.string.song_deleted)
        if (!song.allowComments) throw AppException(R.string.comments_locked)

        val commentRef = comments(songId).document()
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
    }

    suspend fun getAll(songId: String, currentUserId: String): List<Comment> {
        val comments = getAll(songId)
        if (currentUserId.isBlank()) return comments

        return comments.map { comment ->
            comment.copy(
                isLikedByCurrentUser = commentLike(songId, comment.id, currentUserId)
                    .get()
                    .await()
                    .exists()
            )
        }
    }

    suspend fun softDelete(songId: String, commentId: String, deletedBy: String) {
        if (songId.isBlank() || commentId.isBlank() || deletedBy.isBlank()) return

        val now = System.currentTimeMillis()
        firestore.batch().apply {
            set(
                comments(songId).document(commentId),
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to now,
                    "deletedBy" to deletedBy,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            update(
                firestore.collection("songs").document(songId),
                "commentsCount",
                FieldValue.increment(-1)
            )
        }.commit().await()
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
}
