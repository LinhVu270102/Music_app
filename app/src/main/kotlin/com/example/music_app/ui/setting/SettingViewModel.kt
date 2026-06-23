package com.example.music_app.ui.setting

import androidx.lifecycle.ViewModel
import com.example.music_app.data.repository.AuthRepository

class SettingViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    fun logout() {
        authRepository.logout()
    }
}
