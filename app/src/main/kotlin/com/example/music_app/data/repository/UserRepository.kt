package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AccountStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.UserRole
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * UserRepository: quản lý dữ liệu người dùng Auth + Firestore
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val usersCollection = firestore.collection("users")

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    suspend fun register(
        email: String,
        password: String,
        user: User
    ): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                val now = System.currentTimeMillis()

                val userWithId = user.copy(
                    uid = firebaseUser.uid,
                    role = UserRole.USER,
                    accountStatus = AccountStatus.ACTIVE,
                    createdAt = if (user.createdAt == 0L) now else user.createdAt,
                    updatedAt = now,
                    lastLoginAt = now
                )

                usersCollection.document(firebaseUser.uid)
                    .set(userWithId)
                    .await()

                Result.success(userWithId)
            } else {
                Result.failure(AppException(R.string.register_failed))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                val snapshot = usersCollection.document(firebaseUser.uid).get().await()
                val user = snapshot.toObject(User::class.java)

                if (user != null) {
                    usersCollection.document(firebaseUser.uid)
                        .update("lastLoginAt", System.currentTimeMillis())
                        .await()

                    Result.success(user)
                } else {
                    Result.failure(AppException(R.string.user_not_found))
                }
            } else {
                Result.failure(AppException(R.string.login_failed))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): Result<User> {
        return try {
            val snapshot = usersCollection.document(uid).get().await()
            val user = snapshot.toObject(User::class.java)

            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(AppException(R.string.user_not_found))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUserProfile(): Result<User> {
        return try {
            val firebaseUser = auth.currentUser
                ?: return Result.failure(AppException(R.string.not_logged_in))

            val snapshot = usersCollection.document(firebaseUser.uid)
                .get()
                .await()

            val user = snapshot.toObject(User::class.java)?.copy(uid = snapshot.id)

            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(AppException(R.string.user_not_found))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCurrentUserProfile(
        fullName: String,
        displayName: String,
        username: String,
        bio: String,
        phoneNumber: String,
        gender: String,
        country: String,
        favoriteGenres: List<String>,
        musicMoodTags: List<String>
    ): Result<User> {
        return try {
            val firebaseUser = auth.currentUser
                ?: return Result.failure(AppException(R.string.not_logged_in))

            val now = System.currentTimeMillis()

            val data = mapOf(
                "fullName" to fullName,
                "displayName" to displayName,
                "username" to username,
                "bio" to bio,
                "phoneNumber" to phoneNumber,
                "gender" to gender,
                "country" to country,
                "favoriteGenres" to favoriteGenres,
                "musicMoodTags" to musicMoodTags,
                "updatedAt" to now
            )

            usersCollection.document(firebaseUser.uid)
                .set(data, SetOptions.merge())
                .await()

            val updatedSnapshot = usersCollection.document(firebaseUser.uid)
                .get()
                .await()

            val updatedUser = updatedSnapshot.toObject(User::class.java)
                ?.copy(uid = updatedSnapshot.id)

            if (updatedUser != null) {
                Result.success(updatedUser)
            } else {
                Result.failure(AppException(R.string.user_not_found))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAvatarUrl(avatarUrl: String): Result<Unit> {
        return try {
            val firebaseUser = auth.currentUser
                ?: return Result.failure(AppException(R.string.not_logged_in))

            usersCollection.document(firebaseUser.uid)
                .set(
                    mapOf(
                        "avatarUrl" to avatarUrl,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }
}