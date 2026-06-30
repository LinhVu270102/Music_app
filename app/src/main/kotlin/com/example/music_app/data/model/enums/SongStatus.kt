package com.example.music_app.data.model.enums

enum class SongStatus(val value: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    HIDDEN("hidden");

    companion object {
        fun from(value: String?): SongStatus {
            return values().firstOrNull { status ->
                status.value.equals(value, ignoreCase = true) ||
                    status.name.equals(value, ignoreCase = true)
            } ?: PENDING
        }
    }
}
