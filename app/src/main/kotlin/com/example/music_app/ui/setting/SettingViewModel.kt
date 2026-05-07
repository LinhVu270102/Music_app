package com.example.music_app.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingViewModel : ViewModel() {

    private val _selectedLanguage = MutableLiveData("Tiếng Việt")
    val selectedLanguage: LiveData<String> = _selectedLanguage

    fun setLanguage(language: String) {
        _selectedLanguage.value = language
    }

    fun clearData() {

        // TODO:
        // sau này clear cache, history, downloaded songs

    }

    fun deleteAccount() {

        // TODO:
        // sau này xoá account Firebase Auth + Firestore

    }
}