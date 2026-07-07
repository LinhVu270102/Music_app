package com.example.music_app.core.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.music_app.data.local.preferences.LanguagePreferenceDataSource

object LanguageManager {

    fun saveLanguage(context: Context, language: AppLanguage) {
        LanguagePreferenceDataSource(context).saveLanguage(language)
    }

    fun getSavedLanguage(context: Context): AppLanguage {
        return LanguagePreferenceDataSource(context).getSavedLanguage()
    }

    fun applyLanguage(language: AppLanguage) {
        val localeList = LocaleListCompat.forLanguageTags(language.code)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun applySavedLanguage(context: Context) {
        applyLanguage(getSavedLanguage(context))
    }
}
