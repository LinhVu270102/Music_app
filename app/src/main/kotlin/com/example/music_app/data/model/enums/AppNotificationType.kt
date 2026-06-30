package com.example.music_app.data.model.enums

enum class AppNotificationType(val value: String) {
    GENERAL("general"),
    SONG_APPROVED("song_approved"),
    SONG_REJECTED("song_rejected"),
    NEW_FOLLOWER("new_follower"),
    NEW_COMMENT("new_comment"),
    NEW_LIKE("new_like"),
    REPORT_RESOLVED("report_resolved");

    companion object {
        fun from(value: String?): AppNotificationType {
            return values().firstOrNull { type ->
                type.value.equals(value, ignoreCase = true) ||
                    type.name.equals(value, ignoreCase = true)
            } ?: GENERAL
        }
    }
}
