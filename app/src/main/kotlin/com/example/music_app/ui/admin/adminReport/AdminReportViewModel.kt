package com.example.music_app.ui.admin.adminReport

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

    private val _isReviewedMode = MutableLiveData(false)
    val isReviewedMode: LiveData<Boolean> = _isReviewedMode

    private val _messageResId = MutableLiveData<Int?>()
    val messageResId: LiveData<Int?> = _messageResId

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentFilter: ReportFilter = ReportFilter.PENDING

    fun loadReports() {
        viewModelScope.launch {
            try {
                setLoading(true)
                publishReports(loadReportsByCurrentFilter())
            } catch (_: Exception) {
                publishMessage(R.string.load_reports_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    fun showPendingReports() {
        currentFilter = ReportFilter.PENDING
        publishReviewedMode(false)
        loadReports()
    }

    fun showReviewedReports() {
        currentFilter = ReportFilter.REVIEWED
        publishReviewedMode(true)
        loadReports()
    }

    fun resolveReport(report: Report) {
        viewModelScope.launch {
            try {
                adminRepository.resolveReport(report.id)
                publishMessage(R.string.report_resolved_success)
                refreshCurrentReports()
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
                refreshCurrentReports()
            } catch (_: Exception) {
                publishMessage(R.string.report_update_failed)
            }
        }
    }

    fun reopenReport(report: Report) {
        viewModelScope.launch {
            try {
                adminRepository.reopenReport(report.id)
                publishMessage(R.string.report_reopened_success)
                refreshCurrentReports()
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
                refreshCurrentReports()
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

    private suspend fun loadReportsByCurrentFilter(): List<Report> {
        return when (currentFilter) {
            ReportFilter.PENDING -> adminRepository.getPendingReports()
            ReportFilter.REVIEWED -> adminRepository.getReviewedReports()
        }
    }

    private fun refreshCurrentReports() {
        loadReports()
    }

    private fun publishReviewedMode(isReviewedMode: Boolean) {
        _isReviewedMode.value = isReviewedMode
    }

    private fun publishMessage(messageResId: Int) {
        _messageResId.value = messageResId
    }

    private fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    private enum class ReportFilter {
        PENDING,
        REVIEWED
    }
}
