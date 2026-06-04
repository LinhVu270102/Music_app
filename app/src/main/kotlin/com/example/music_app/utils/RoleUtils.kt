package com.example.music_app.utils

import com.example.music_app.data.model.AccountStatus
import com.example.music_app.data.model.User
import com.example.music_app.data.model.UserRole

object RoleUtils {

    fun isActive(user: User?): Boolean {
        return user?.accountStatus == AccountStatus.ACTIVE
    }

    fun isUser(user: User?): Boolean {
        return user?.role == UserRole.USER
    }

    fun isAdmin(user: User?): Boolean {
        return user?.role == UserRole.ADMIN
    }

    fun canListen(user: User?): Boolean {
        return isActive(user) && isUser(user)
    }

    fun canUpload(user: User?): Boolean {
        return isActive(user) && isUser(user)
    }

    fun canLike(user: User?): Boolean {
        return isActive(user) && isUser(user)
    }

    fun canFollow(user: User?): Boolean {
        return isActive(user) && isUser(user)
    }

    fun canCreatePlaylist(user: User?): Boolean {
        return isActive(user) && isUser(user)
    }

    fun canModerate(user: User?): Boolean {
        return isActive(user) && isAdmin(user)
    }

    fun canManageSystem(user: User?): Boolean {
        return isActive(user) && isAdmin(user)
    }
}