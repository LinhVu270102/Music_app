package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AccountStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.UserRole
import com.example.music_app.utils.AppException
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
                    throw task.exception ?: AppException(R.string.register_failed)
                }

                val firebaseUser = auth.currentUser
                    ?: throw AppException(R.string.user_not_found)

                val uid = firebaseUser.uid
                val now = System.currentTimeMillis()

                val user = User(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    username = generateUsername(displayName, email),
                    avatarUrl = "",
                    bio = "",
                    role = UserRole.USER,
                    accountStatus = AccountStatus.ACTIVE,
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
