package com.example.music_app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

object LanguageManager {

    private const val PREF_NAME = "language_pref"
    private const val KEY_LANGUAGE = "app_language"

    const val LANGUAGE_VI = "vi"
    const val LANGUAGE_EN = "en"

    fun saveLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LANGUAGE, languageCode)
        }
    }

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_EN) ?: LANGUAGE_EN
    }

    fun applyLanguage(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun applySavedLanguage(context: Context) {
        val languageCode = getSavedLanguage(context)
        applyLanguage(languageCode)
    }
}