package com.example.music_app.data.repository

import com.example.music_app.data.model.AdminDashboardStats
import com.example.music_app.data.model.ReportStatus
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.music_app.data.model.Song
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.Comment

class AdminRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val firebaseService = FirebaseService(firestore)

    private fun currentAdminId(): String {
        return auth.currentUser?.uid.orEmpty()
    }

    suspend fun getPendingSongs(): List<Song> {
        return firebaseService.getSongsByStatus(SongStatus.PENDING)
    }

    suspend fun approveSong(songId: String) {
        firebaseService.updateSongStatus(
            songId = songId,
            status = SongStatus.APPROVED,
            reviewedBy = currentAdminId()
        )
    }

    suspend fun rejectSong(
        songId: String,
        reason: String
    ) {
        firebaseService.updateSongStatus(
            songId = songId,
            status = SongStatus.REJECTED,
            reviewedBy = currentAdminId(),
            rejectReason = reason
        )
    }

    suspend fun hideSong(songId: String) {
        firebaseService.softDeleteSong(
            songId = songId,
            deletedBy = currentAdminId()
        )
    }

    suspend fun updateSongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        firebaseService.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }

    suspend fun isCurrentUserAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false

        val userDoc = firestore.collection("users")
            .document(uid)
            .get()
            .await()

        val role = userDoc.getString("role").orEmpty()

        return role == UserRole.ADMIN
    }

    suspend fun getDashboardStats(): AdminDashboardStats {
        val pendingSongs = firestore.collection("songs")
            .whereEqualTo("status", SongStatus.PENDING)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .size()

        val approvedSongs = firestore.collection("songs")
            .whereEqualTo("status", SongStatus.APPROVED)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .size()

        val rejectedSongs = firestore.collection("songs")
            .whereEqualTo("status", SongStatus.REJECTED)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .size()

        val hiddenSongs = firestore.collection("songs")
            .whereEqualTo("isDeleted", true)
            .get()
            .await()
            .size()

        val pendingReports = firestore.collection("reports")
            .whereEqualTo("status", ReportStatus.PENDING)
            .get()
            .await()
            .size()

        val reportedSongs = firestore.collection("songs")
            .get()
            .await()
            .documents
            .count { doc ->
                val reportsCount = doc.getLong("reportsCount") ?: 0L
                val isDeleted = doc.getBoolean("isDeleted") ?: false

                reportsCount > 0L && !isDeleted
            }

        return AdminDashboardStats(
            pendingSongs = pendingSongs,
            approvedSongs = approvedSongs,
            rejectedSongs = rejectedSongs,
            hiddenSongs = hiddenSongs,
            pendingReports = pendingReports,
            reportedSongs = reportedSongs
        )
    }
    suspend fun getPendingReports(): List<Report> {
        return firebaseService.getPendingReports()
    }

    suspend fun resolveReport(reportId: String) {
        firebaseService.updateReportStatus(
            reportId = reportId,
            status = REPORT_STATUS_RESOLVED,
            reviewedBy = currentAdminId()
        )
    }

    suspend fun rejectReport(reportId: String) {
        firebaseService.updateReportStatus(
            reportId = reportId,
            status = REPORT_STATUS_REJECTED,
            reviewedBy = currentAdminId()
        )
    }

    suspend fun hideReportedTarget(report: Report) {
        when (report.targetType) {
            TARGET_TYPE_SONG -> {
                firebaseService.softDeleteSong(
                    songId = report.targetId,
                    deletedBy = currentAdminId()
                )
            }

            TARGET_TYPE_COMMENT -> {
                val songId = report.description
                    .split("|")
                    .getOrNull(0)
                    .orEmpty()

                if (songId.isNotBlank()) {
                    firebaseService.softDeleteComment(
                        songId = songId,
                        commentId = report.targetId,
                        deletedBy = currentAdminId()
                    )
                }
            }
        }

        resolveReport(report.id)
    }
    suspend fun getReportedComments(): List<Comment> {
        return firebaseService.getReportedComments()
    }

    suspend fun hideComment(comment: Comment) {
        firebaseService.softDeleteComment(
            songId = comment.songId,
            commentId = comment.id,
            deletedBy = currentAdminId()
        )
    }

    companion object {
        private const val REPORT_STATUS_RESOLVED = "RESOLVED"
        private const val REPORT_STATUS_REJECTED = "REJECTED"

        private const val TARGET_TYPE_SONG = "song"
        private const val TARGET_TYPE_COMMENT = "comment"
    }
}