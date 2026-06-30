package com.example.music_app.data.model.enums

enum class UserRole(val value: String) {
    USER("user"),
    ADMIN("admin");

    companion object {
        fun from(value: String?): UserRole {
            return values().firstOrNull { role ->
                role.value.equals(value, ignoreCase = true) ||
                    role.name.equals(value, ignoreCase = true)
            } ?: USER
        }
    }
}