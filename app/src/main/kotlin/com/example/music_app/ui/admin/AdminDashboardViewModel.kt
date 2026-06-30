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

class AdminDashboardViewModel(
    private val adminRepository: AdminRepository = AdminRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

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
                publishAdminState(admin)

                if (!admin) {
                    publishError(R.string.no_admin_permission)
                    return@launch
                }

                publishStats(adminRepository.getDashboardStats())
            } catch (_: Exception) {
                publishError(R.string.load_admin_dashboard_failed)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessageResId.value = null
    }

    fun logout() {
        authRepository.logout()
    }

    private fun publishStats(stats: AdminDashboardStats) {
        _stats.value = stats
    }

    private fun publishAdminState(isAdmin: Boolean) {
        _isAdmin.value = isAdmin
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }
}
