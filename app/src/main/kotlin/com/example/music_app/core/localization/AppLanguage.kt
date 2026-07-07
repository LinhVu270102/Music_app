package com.example.music_app.core.localization

enum class AppLanguage(val code: String) {
    VIETNAMESE("vi"),
    ENGLISH("en");

    companion object {
        val DEFAULT = ENGLISH

        fun fromCode(code: String?): AppLanguage {
            return values().firstOrNull { it.code == code } ?: DEFAULT
        }

        fun fromSpinnerPosition(position: Int): AppLanguage {
            return when (position) {
                0 -> VIETNAMESE
                1 -> ENGLISH
                else -> DEFAULT
            }
        }
    }
}
