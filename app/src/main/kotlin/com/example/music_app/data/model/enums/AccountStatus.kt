package com.example.music_app.data.model.enums

enum class AccountStatus(val value: String) {
    ACTIVE("active"),
    SUSPENDED("suspended"),
    BANNED("banned");

    companion object {
        fun from(value: String?): AccountStatus {
            return values().firstOrNull { status ->
                status.value.equals(value, ignoreCase = true) ||
                    status.name.equals(value, ignoreCase = true)
            } ?: ACTIVE
        }
    }
}