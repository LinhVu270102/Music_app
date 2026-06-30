package com.example.music_app.data.model.enums

enum class ReportTargetType(val value: String) {
    SONG("song"),
    COMMENT("comment"),
    USER("user");

    companion object {
        fun from(value: String?): ReportTargetType {
            return values().firstOrNull { type ->
                type.value.equals(value, ignoreCase = true) ||
                    type.name.equals(value, ignoreCase = true)
            } ?: SONG
        }
    }
}
