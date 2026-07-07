package com.example.music_app.main

import android.app.Application
import com.example.music_app.core.localization.LanguageManager
import com.google.firebase.FirebaseApp

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        LanguageManager.applySavedLanguage(this)
    }
}
