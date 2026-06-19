package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.ReportStatus
import com.example.music_app.data.model.UserRole
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ModerationRepository {

    private val db = FirebaseFirestore.getInstance()
    private val firebaseService = FirebaseService(db)
    private val auth = FirebaseAuth.getInstance()

    suspend fun getPendingReports(): List<Report> {
        requireAdmin()
        return firebaseService.getPendingReports()
    }

    suspend fun resolveReport(
        reportId: String,
        adminNote: String = ""
    ) {
        val adminId = requireAdmin()

        firebaseService.updateReportStatus(
            reportId = reportId,
            status = ReportStatus.RESOLVED,
            reviewedBy = adminId,
            adminNote = adminNote
        )
    }

    suspend fun rejectReport(
        reportId: String,
        adminNote: String = ""
    ) {
        val adminId = requireAdmin()

        firebaseService.updateReportStatus(
            reportId = reportId,
            status = ReportStatus.REJECTED,
            reviewedBy = adminId,
            adminNote = adminNote
        )
    }

    private suspend fun requireAdmin(): String {
        val userId = auth.currentUser?.uid
            ?: throw AppException(R.string.not_logged_in)

        val user = firebaseService.getUserById(userId)
            ?: throw AppException(R.string.account_not_found)

        if (user.role != UserRole.ADMIN) {
            throw AppException(R.string.no_admin_permission)
        }

        return userId
    }
}