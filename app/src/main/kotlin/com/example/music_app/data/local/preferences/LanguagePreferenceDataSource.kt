package com.example.music_app.data.local.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.music_app.core.localization.AppLanguage

class LanguagePreferenceDataSource(context: Context) {

    private val appContext = context.applicationContext

    fun saveLanguage(language: AppLanguage) {
        preferences.edit {
            putString(KEY_LANGUAGE, language.code)
        }
    }

    fun getSavedLanguage(): AppLanguage {
        val savedCode = preferences.getString(KEY_LANGUAGE, AppLanguage.DEFAULT.code)
        return AppLanguage.fromCode(savedCode)
    }

    private val preferences
        get() = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREF_NAME = "language_pref"
        const val KEY_LANGUAGE = "app_language"
    }
}
