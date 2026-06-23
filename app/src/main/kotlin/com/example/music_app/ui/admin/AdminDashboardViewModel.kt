package com.example.music_app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.AdminDashboardStats
import com.example.music_app.data.repository.AdminRepository
import com.example.music_app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AdminDashboardViewModel : ViewModel() {

    private val adminRepository = AdminRepository()
    private val authRepository = AuthRepository()

    private val _stats = MutableLiveData<AdminDashboardStats>()
    val stats: LiveData<AdminDashboardStats> = _stats

    private val _isAdmin = MutableLiveData<Boolean>()
    val isAdmin: LiveData<Boolean> = _isAdmin

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadDashboard() {
        viewModelScope.launch {
            try {
                val admin = adminRepository.isCurrentUserAdmin()
                _isAdmin.value = admin

                if (!admin) {
                    _errorMessageResId.value = R.string.no_admin_permission
                    return@launch
                }

                _stats.value = adminRepository.getDashboardStats()
            } catch (_: Exception) {
                _errorMessageResId.value = R.string.load_admin_dashboard_failed
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun logout() {
        authRepository.logout()
    }
}
