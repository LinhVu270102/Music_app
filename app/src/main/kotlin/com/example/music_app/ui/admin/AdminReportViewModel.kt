package com.example.music_app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.repository.AdminRepository
import kotlinx.coroutines.launch

class AdminReportViewModel(
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _reports = MutableLiveData<List<Report>>(emptyList())
    val reports: LiveData<List<Report>> = _reports

    private val _messageResId = MutableLiveData<Int?>()
    val messageResId: LiveData<Int?> = _messageResId

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadReports() {
        viewModelScope.launch {
            try {
                setLoading(true)
                publishReports(adminRepository.getPendingReports())
            } catch (_: Exception) {
                publishMessage(R.string.load_reports_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    fun resolveReport(report: Report) {
        viewModelScope.launch {
            try {
                adminRepository.resolveReport(report.id)
                publishMessage(R.string.report_resolved_success)
                loadReports()
            } catch (_: Exception) {
                publishMessage(R.string.report_update_failed)
            }
        }
    }

    fun rejectReport(report: Report) {
        viewModelScope.launch {
            try {
                adminRepository.rejectReport(report.id)
                publishMessage(R.string.report_rejected_success)
                loadReports()
            } catch (_: Exception) {
                publishMessage(R.string.report_update_failed)
            }
        }
    }

    fun hideReportedTarget(report: Report) {
        viewModelScope.launch {
            try {
                adminRepository.hideReportedTarget(report)
                publishMessage(R.string.report_target_hidden_success)
                loadReports()
            } catch (_: Exception) {
                publishMessage(R.string.report_target_hidden_failed)
            }
        }
    }

    fun clearMessage() {
        _messageResId.value = null
    }

    private fun publishReports(reports: List<Report>) {
        _reports.value = reports
    }

    private fun publishMessage(messageResId: Int) {
        _messageResId.value = messageResId
    }

    private fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
}
