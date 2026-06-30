package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.enums.AccountStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.UserRole
import com.example.music_app.utils.AppException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val usersCollection = firestore.collection("users")

    fun login(email: String, password: String): Task<AuthResult> {
        return auth.signInWithEmailAndPassword(email.trim(), password)
    }

    fun register(
        displayName: String,
        fullName: String,
        username: String,
        email: String,
        password: String,
        phoneNumber: String,
        gender: String,
        country: String,
        favoriteGenres: List<String>,
        musicMoodTags: List<String>
    ): Task<Void> {
        val cleanEmail = email.trim()

        return auth.createUserWithEmailAndPassword(cleanEmail, password)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: AppException(R.string.register_failed)
                }

                val firebaseUser = task.result?.user ?: auth.currentUser
                    ?: throw AppException(R.string.user_not_found)

                val user = createRegisteredUser(
                    firebaseUser = firebaseUser,
                    displayName = displayName,
                    fullName = fullName,
                    username = username,
                    email = cleanEmail,
                    phoneNumber = phoneNumber,
                    gender = gender,
                    country = country,
                    favoriteGenres = favoriteGenres,
                    musicMoodTags = musicMoodTags
                )

                usersCollection
                    .document(firebaseUser.uid)
                    .set(user)
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    private fun createRegisteredUser(
        firebaseUser: FirebaseUser,
        displayName: String,
        fullName: String,
        username: String,
        email: String,
        phoneNumber: String,
        gender: String,
        country: String,
        favoriteGenres: List<String>,
        musicMoodTags: List<String>
    ): User {
        val now = System.currentTimeMillis()
        val cleanDisplayName = displayName.trim()
        val cleanUsername = username.trim()

        return User(
            uid = firebaseUser.uid,
            email = email,
            displayName = cleanDisplayName,
            username = cleanUsername.ifBlank { generateUsername(cleanDisplayName, email) },
            avatarUrl = "",
            bio = "",
            fullName = fullName.trim(),
            phoneNumber = phoneNumber.trim(),
            gender = gender.trim(),
            country = country.trim(),
            favoriteGenres = favoriteGenres,
            musicMoodTags = musicMoodTags,
            role = UserRole.USER.value,
            accountStatus = AccountStatus.ACTIVE.value,
            createdAt = now,
            updatedAt = now,
            lastLoginAt = now
        )
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
