package com.example.music_app.data.model.enums

enum class AppNotificationTargetType(val value: String) {
    NONE(""),
    SONG("song"),
    COMMENT("comment"),
    USER("user"),
    PLAYLIST("playlist");

    companion object {
        fun from(value: String?): AppNotificationTargetType {
            return values().firstOrNull { type ->
                type.value.equals(value, ignoreCase = true) ||
                    type.name.equals(value, ignoreCase = true)
            } ?: NONE
        }
    }
}
