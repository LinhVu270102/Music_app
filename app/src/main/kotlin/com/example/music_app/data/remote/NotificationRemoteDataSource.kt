package com.example.music_app.data.remote

import com.example.music_app.data.model.AppNotification
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for a user's in-app notifications. */
class NotificationRemoteDataSource(
    private val firestore: FirebaseFirestore
) {

    suspend fun create(notification: AppNotification): AppNotification {
        if (notification.receiverId.isBlank()) return notification

        val reference = notifications(notification.receiverId).document()

        val notificationWithId = notification.withGeneratedMetadata(reference.id)

        reference.set(notificationWithId).await()
        return notificationWithId
    }

    suspend fun getAllForUser(userId: String): List<AppNotification> {
        if (userId.isBlank()) return emptyList()

        return notifications(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_NOTIFICATIONS)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.toNotification() }
    }

    suspend fun markRead(userId: String, notificationId: String) {
        if (userId.isBlank() || notificationId.isBlank()) return

        notifications(userId)
            .document(notificationId)
            .update("isRead", true)
            .await()
    }

    suspend fun markAllRead(userId: String) {
        if (userId.isBlank()) return

        val unreadNotifications = notifications(userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()

        if (unreadNotifications.isEmpty) return

        firestore.batch().apply {
            unreadNotifications.documents.forEach { document ->
                update(document.reference, "isRead", true)
            }
        }.commit().await()
    }

    private fun notifications(userId: String) = firestore.collection("users")
        .document(userId)
        .collection("notifications")

    private fun AppNotification.withGeneratedMetadata(notificationId: String): AppNotification {
        return copy(
            id = notificationId,
            createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    private fun DocumentSnapshot.toNotification(): AppNotification? {
        return toObject(AppNotification::class.java)?.copy(id = id)
    }

    private companion object {
        const val MAX_NOTIFICATIONS = 100L
    }
}
