package com.example.music_app.data.remote

import com.example.music_app.data.model.User
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for public user profiles and user roles. */
class UserRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getById(userId: String): User? {
        if (userId.isBlank()) return null

        val document = user(userId).get().await()
        return document.toUser()
    }

    suspend fun updateRole(userId: String, role: String) {
        if (userId.isBlank()) return

        user(userId)
            .set(roleUpdateData(role), SetOptions.merge())
            .await()
    }

    private fun users() = firestore.collection("users")

    private fun user(userId: String) = users().document(userId)

    private fun roleUpdateData(role: String): Map<String, Any> {
        return mapOf("role" to role)
    }

    private fun DocumentSnapshot.toUser(): User? {
        return toObject(User::class.java)?.copy(uid = id)
    }
}
