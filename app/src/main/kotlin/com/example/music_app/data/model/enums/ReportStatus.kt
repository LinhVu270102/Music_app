package com.example.music_app.data.model.enums

enum class ReportStatus(val value: String) {
    PENDING("pending"),
    REVIEWED("reviewed"),
    REJECTED("rejected"),
    RESOLVED("resolved");

    companion object {
        fun from(value: String?): ReportStatus {
            return values().firstOrNull { status ->
                status.value.equals(value, ignoreCase = true) ||
                    status.name.equals(value, ignoreCase = true)
            } ?: PENDING
        }
    }
}
