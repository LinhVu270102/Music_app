package com.example.music_app.data.model.enums

enum class FingerprintStatus(val value: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    UNIQUE("unique"),
    DUPLICATE("duplicate"),
    FAILED("failed");

    companion object {
        fun from(value: String?): FingerprintStatus {
            return values().firstOrNull { status ->
                status.value.equals(value, ignoreCase = true) ||
                    status.name.equals(value, ignoreCase = true)
            } ?: PENDING
        }
    }
}
