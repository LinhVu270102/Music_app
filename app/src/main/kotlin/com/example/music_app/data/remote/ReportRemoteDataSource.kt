package com.example.music_app.data.remote

import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.ReportStatus
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Low-level Firestore access for moderation reports and their review status. */
class ReportRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun create(report: Report): Report {
        validate(report)

        val reportRef = firestore.collection("reports").document()
        val now = System.currentTimeMillis()
        val reportWithId = report.copy(
            id = reportRef.id,
            createdAt = now,
            updatedAt = now
        )

        reportRef.set(reportWithId).await()
        incrementTargetReportCount(reportWithId)
        return reportWithId
    }

    suspend fun getPending(): List<Report> {
        return firestore.collection("reports")
            .whereEqualTo("status", ReportStatus.PENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(Report::class.java)?.copy(id = document.id)
            }
    }

    suspend fun updateStatus(
        reportId: String,
        status: String,
        reviewedBy: String,
        adminNote: String = ""
    ) {
        if (reportId.isBlank()) return

        val now = System.currentTimeMillis()
        firestore.collection("reports")
            .document(reportId)
            .set(
                mapOf(
                    "status" to status,
                    "reviewedBy" to reviewedBy,
                    "reviewedAt" to now,
                    "adminNote" to adminNote,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            .await()
    }

    private fun validate(report: Report) {
        if (report.targetId.isBlank()) throw AppException(R.string.invalid_report_target)
        if (report.reporterId.isBlank()) throw AppException(R.string.invalid_user)
        if (report.reason.isBlank()) throw AppException(R.string.report_reason_empty)
    }

    private suspend fun incrementTargetReportCount(report: Report) {
        when (report.targetType) {
            "song" -> firestore.collection("songs")
                .document(report.targetId)
                .update("reportsCount", FieldValue.increment(1))
                .await()

            "comment" -> {
                val songId = report.description.substringBefore("|").takeIf(String::isNotBlank)
                    ?: return
                firestore.collection("songs")
                    .document(songId)
                    .collection("comments")
                    .document(report.targetId)
                    .update("reportsCount", FieldValue.increment(1))
                    .await()
            }
        }
    }
}
