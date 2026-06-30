package com.example.music_app.ui.setting

import androidx.lifecycle.ViewModel
import com.example.music_app.data.repository.AuthRepository

class SettingViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    fun logout() {
        authRepository.logout()
    }
}
