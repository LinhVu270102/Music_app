package com.example.music_app.data.remote

import com.example.music_app.data.model.AppNotification
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for a user's in-app notifications. */
class NotificationRemoteDataSource(
    private val firestore: FirebaseFirestore
) {

    suspend fun create(notification: AppNotification): AppNotification {
        if (notification.receiverId.isBlank()) return notification

        val reference = firestore.collection("users")
            .document(notification.receiverId)
            .collection("notifications")
            .document()

        val notificationWithId = notification.copy(
            id = reference.id,
            createdAt = notification.createdAt.takeIf { it > 0 }
                ?: System.currentTimeMillis()
        )

        reference.set(notificationWithId).await()
        return notificationWithId
    }

    suspend fun getAllForUser(userId: String): List<AppNotification> {
        if (userId.isBlank()) return emptyList()

        return firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_NOTIFICATIONS)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(AppNotification::class.java)?.copy(id = document.id)
            }
    }

    suspend fun markRead(userId: String, notificationId: String) {
        if (userId.isBlank() || notificationId.isBlank()) return

        firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .document(notificationId)
            .update("isRead", true)
            .await()
    }

    suspend fun markAllRead(userId: String) {
        if (userId.isBlank()) return

        val unreadNotifications = firestore.collection("users")
            .document(userId)
            .collection("notifications")
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

    private companion object {
        const val MAX_NOTIFICATIONS = 100L
    }
}
