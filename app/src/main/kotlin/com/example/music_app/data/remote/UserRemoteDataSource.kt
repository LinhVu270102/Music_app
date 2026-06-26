package com.example.music_app.data.remote

import com.example.music_app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for public user profiles and user roles. */
class UserRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getById(userId: String): User? {
        if (userId.isBlank()) return null

        val document = firestore.collection("users").document(userId).get().await()
        return document.toObject(User::class.java)?.copy(uid = document.id)
    }

    suspend fun updateRole(userId: String, role: String) {
        if (userId.isBlank()) return

        firestore.collection("users")
            .document(userId)
            .set(mapOf("role" to role), SetOptions.merge())
            .await()
    }
}
