package com.example.music_app.main

import android.app.Application
import com.example.music_app.utils.LanguageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        LanguageManager.applySavedLanguage(this)
    }
}