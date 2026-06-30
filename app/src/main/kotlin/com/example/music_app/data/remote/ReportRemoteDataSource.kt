package com.example.music_app.data.remote

import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.example.music_app.utils.AppException
import com.google.firebase.firestore.DocumentSnapshot
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

        val reportRef = reports().document()
        val reportWithId = report.withGeneratedMetadata(reportRef.id)

        reportRef.set(reportWithId).await()
        incrementTargetReportCount(reportWithId)
        return reportWithId
    }

    suspend fun getPending(): List<Report> {
        return reports()
            .whereEqualTo("status", ReportStatus.PENDING.value)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.toReport() }
    }

    suspend fun updateStatus(
        reportId: String,
        status: String,
        reviewedBy: String,
        adminNote: String = ""
    ) {
        if (reportId.isBlank()) return

        reports()
            .document(reportId)
            .set(reviewStatusData(status, reviewedBy, adminNote), SetOptions.merge())
            .await()
    }

    private fun validate(report: Report) {
        if (report.targetId.isBlank()) throw AppException(R.string.invalid_report_target)
        if (report.reporterId.isBlank()) throw AppException(R.string.invalid_user)
        if (report.reason.isBlank()) throw AppException(R.string.report_reason_empty)
    }

    private suspend fun incrementTargetReportCount(report: Report) {
        when (report.targetKind) {
            ReportTargetType.SONG -> songs()
                .document(report.targetId)
                .update("reportsCount", FieldValue.increment(1))
                .await()

            ReportTargetType.COMMENT -> {
                val songId = report.commentSongId()
                    ?: return
                comments(songId)
                    .document(report.targetId)
                    .update("reportsCount", FieldValue.increment(1))
                    .await()
            }

            ReportTargetType.USER -> Unit
        }
    }

    private fun reports() = firestore.collection("reports")

    private fun songs() = firestore.collection("songs")

    private fun comments(songId: String) = songs()
        .document(songId)
        .collection("comments")

    private fun reviewStatusData(
        status: String,
        reviewedBy: String,
        adminNote: String
    ): Map<String, Any> {
        val now = System.currentTimeMillis()

        return mapOf(
            "status" to status,
            "reviewedBy" to reviewedBy,
            "reviewedAt" to now,
            "adminNote" to adminNote,
            "updatedAt" to now
        )
    }

    private fun Report.withGeneratedMetadata(reportId: String): Report {
        val now = System.currentTimeMillis()

        return copy(
            id = reportId,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun Report.commentSongId(): String? {
        return description.substringBefore("|").takeIf(String::isNotBlank)
    }

    private fun DocumentSnapshot.toReport(): Report? {
        return toObject(Report::class.java)?.copy(id = id)
    }
}
