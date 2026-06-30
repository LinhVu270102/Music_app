package com.example.music_app.data.repository

import android.net.Uri
import com.example.music_app.R
import com.example.music_app.data.model.enums.AccountStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.UserRole
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * UserRepository: quản lý dữ liệu người dùng Auth + Firestore
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    private val usersCollection = firestore.collection("users")

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun getCurrentUserId(): String = auth.currentUser?.uid.orEmpty()

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
                val userWithId = user.withRegistrationDefaults(firebaseUser, now)

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
                val user = getUserByIdOrNull(firebaseUser.uid)

                if (user != null) {
                    updateLastLoginAt(firebaseUser.uid)

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
            val user = getUserByIdOrNull(uid)

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
            val firebaseUser = currentFirebaseUserOrNull()
                ?: return Result.failure(AppException(R.string.not_logged_in))

            val user = getUserByIdOrNull(firebaseUser.uid)

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
            val firebaseUser = currentFirebaseUserOrNull()
                ?: return Result.failure(AppException(R.string.not_logged_in))

            val updatedUser = updateCurrentUserAndReload(
                firebaseUser = firebaseUser,
                data = profileUpdateData(
                    fullName = fullName,
                    displayName = displayName,
                    username = username,
                    bio = bio,
                    phoneNumber = phoneNumber,
                    gender = gender,
                    country = country,
                    favoriteGenres = favoriteGenres,
                    musicMoodTags = musicMoodTags
                )
            )

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
            val firebaseUser = currentFirebaseUserOrNull()
                ?: return Result.failure(AppException(R.string.not_logged_in))

            usersCollection.document(firebaseUser.uid)
                .set(avatarUpdateData(avatarUrl), SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadCurrentUserAvatar(uri: Uri): Result<User> {
        return try {
            val firebaseUser = currentFirebaseUserOrNull()
                ?: return Result.failure(AppException(R.string.not_logged_in))

            val reference = storage.reference.child("avatars/${firebaseUser.uid}/profile.jpg")
            reference.putFile(uri).await()
            val avatarUrl = reference.downloadUrl.await().toString()

            val updatedUser = updateCurrentUserAndReload(
                firebaseUser = firebaseUser,
                data = avatarUpdateData(avatarUrl)
            )
                ?: return Result.failure(AppException(R.string.user_not_found))

            Result.success(updatedUser)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun logout() {
        auth.signOut()
    }

    private fun currentFirebaseUserOrNull(): FirebaseUser? {
        return auth.currentUser
    }

    private suspend fun getUserByIdOrNull(uid: String): User? {
        if (uid.isBlank()) return null

        val snapshot = usersCollection.document(uid).get().await()
        return snapshot.toUserOrNull()
    }

    private suspend fun updateCurrentUserAndReload(
        firebaseUser: FirebaseUser,
        data: Map<String, Any>
    ): User? {
        usersCollection.document(firebaseUser.uid)
            .set(data, SetOptions.merge())
            .await()

        return getUserByIdOrNull(firebaseUser.uid)
    }

    private suspend fun updateLastLoginAt(uid: String) {
        usersCollection.document(uid)
            .update("lastLoginAt", System.currentTimeMillis())
            .await()
    }

    private fun profileUpdateData(
        fullName: String,
        displayName: String,
        username: String,
        bio: String,
        phoneNumber: String,
        gender: String,
        country: String,
        favoriteGenres: List<String>,
        musicMoodTags: List<String>
    ): Map<String, Any> {
        return mapOf(
            "fullName" to fullName,
            "displayName" to displayName,
            "username" to username,
            "bio" to bio,
            "phoneNumber" to phoneNumber,
            "gender" to gender,
            "country" to country,
            "favoriteGenres" to favoriteGenres,
            "musicMoodTags" to musicMoodTags,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    private fun avatarUpdateData(avatarUrl: String): Map<String, Any> {
        return mapOf(
            "avatarUrl" to avatarUrl,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    private fun User.withRegistrationDefaults(firebaseUser: FirebaseUser, now: Long): User {
        return copy(
            uid = firebaseUser.uid,
            role = UserRole.USER.value,
            accountStatus = AccountStatus.ACTIVE.value,
            createdAt = if (createdAt == 0L) now else createdAt,
            updatedAt = now,
            lastLoginAt = now
        )
    }

    private fun DocumentSnapshot.toUserOrNull(): User? {
        return toObject(User::class.java)?.copy(uid = id)
    }
}
