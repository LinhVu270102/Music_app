package com.example.music_app.data.repository

import com.example.music_app.data.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun login(email: String, password: String): Task<AuthResult> {
        return auth.signInWithEmailAndPassword(email, password)
    }

    fun register(
        displayName: String,
        email: String,
        password: String
    ): Task<Void> {
        return auth.createUserWithEmailAndPassword(email, password)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Đăng ký thất bại")
                }

                val firebaseUser = auth.currentUser
                    ?: throw Exception("Không lấy được thông tin user")

                val uid = firebaseUser.uid
                val now = System.currentTimeMillis()

                val user = User(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    username = generateUsername(displayName, email),
                    avatarUrl = "",
                    bio = "",
                    role = "listener",
                    accountStatus = "active",
                    createdAt = now,
                    updatedAt = now,
                    lastLoginAt = now
                )

                firestore.collection("users")
                    .document(uid)
                    .set(user)
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    private fun generateUsername(displayName: String, email: String): String {
        val namePart = displayName
            .lowercase()
            .trim()
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-z0-9_]".toRegex(), "")

        return if (namePart.isNotBlank()) {
            "${namePart}_${System.currentTimeMillis().toString().takeLast(4)}"
        } else {
            email.substringBefore("@")
                .lowercase()
                .replace("[^a-z0-9_]".toRegex(), "")
        }
    }
}