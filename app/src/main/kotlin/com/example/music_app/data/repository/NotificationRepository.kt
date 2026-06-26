package com.example.music_app.data.repository

import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.remote.NotificationRemoteDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Data access dedicated to the current user's in-app notifications. */
class NotificationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val remoteDataSource: NotificationRemoteDataSource =
        NotificationRemoteDataSource(FirebaseFirestore.getInstance())
) {
    suspend fun getNotifications(): List<AppNotification> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return remoteDataSource.getAllForUser(userId)
    }

    suspend fun markRead(notificationId: String) {
        val userId = auth.currentUser?.uid ?: return
        remoteDataSource.markRead(userId, notificationId)
    }

    suspend fun markAllRead() {
        val userId = auth.currentUser?.uid ?: return
        remoteDataSource.markAllRead(userId)
    }
}
