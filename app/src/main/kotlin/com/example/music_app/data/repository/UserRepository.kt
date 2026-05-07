package com.example.music_app.data.repository

import com.example.music_app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * UserRepository: quản lý dữ liệu người dùng (Auth + Firestore)
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val usersCollection = firestore.collection("users")

    /**
     * Lấy user hiện tại (nếu đã đăng nhập)
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Đăng ký tài khoản mới
     */
    suspend fun register(email: String, password: String, user: User): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                val userWithId = user.copy( uid = firebaseUser.uid)
                usersCollection.document(firebaseUser.uid).set(userWithId).await()
                Result.success(userWithId)
            } else {
                Result.failure(Exception("Register failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đăng nhập
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                val snapshot = usersCollection.document(firebaseUser.uid).get().await()
                val user = snapshot.toObject(User::class.java)
                if (user != null) Result.success(user)
                else Result.failure(Exception("User profile not found"))
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lấy thông tin user từ Firestore
     */
    suspend fun getUserProfile(uid: String): Result<User> {
        return try {
            val snapshot = usersCollection.document(uid).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) Result.success(user)
            else Result.failure(Exception("User not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đăng xuất
     */
    fun logout() {
        auth.signOut()
    }
}
