package com.example.music_app.ui.admin.adminDashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.AdminDashboardStats
import com.example.music_app.data.model.Report
import com.example.music_app.data.repository.AdminRepository
import com.example.music_app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AdminDashboardViewModel(
    private val adminRepository: AdminRepository = AdminRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _stats = MutableLiveData<AdminDashboardStats>()
    val stats: LiveData<AdminDashboardStats> = _stats

    private val _latestReports = MutableLiveData<List<Report>>(emptyList())
    val latestReports: LiveData<List<Report>> = _latestReports

    private val _isModerationStaff = MutableLiveData<Boolean>()
    val isModerationStaff: LiveData<Boolean> = _isModerationStaff

    private val _errorMessageResId = MutableLiveData<Int?>()
    val errorMessageResId: LiveData<Int?> = _errorMessageResId

    fun loadDashboard() {
        viewModelScope.launch {
            try {
                val moderationStaff = adminRepository.isCurrentUserModerationStaff()
                publishModerationStaffState(moderationStaff)

                if (!moderationStaff) {
                    publishError(R.string.no_admin_permission)
                    return@launch
                }

                publishStats(adminRepository.getDashboardStats())
                publishLatestReports(loadLatestReportsSafely())
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

    private fun publishLatestReports(reports: List<Report>) {
        _latestReports.value = reports
    }

    private suspend fun loadLatestReportsSafely(): List<Report> {
        return runCatching {
            adminRepository.getPendingReports().take(LATEST_REPORT_LIMIT)
        }.getOrDefault(emptyList())
    }

    private fun publishModerationStaffState(isModerationStaff: Boolean) {
        _isModerationStaff.value = isModerationStaff
    }

    private fun publishError(messageResId: Int) {
        _errorMessageResId.value = messageResId
    }

    private companion object {
        private const val LATEST_REPORT_LIMIT = 3
    }
}
