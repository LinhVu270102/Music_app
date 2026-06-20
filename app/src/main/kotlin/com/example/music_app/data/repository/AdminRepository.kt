package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AdminDashboardStats
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.ReportStatus
import com.example.music_app.data.model.ReportTargetType
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.data.model.UserRole
import com.example.music_app.data.remote.FirebaseService
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseService = FirebaseService(firestore)
    private val auth = FirebaseAuth.getInstance()

    // =========================
    // ADMIN AUTH GUARD
    // =========================
    suspend fun isCurrentUserAdmin(): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        val user = firebaseService.getUserById(userId) ?: return false

        return user.role == UserRole.ADMIN
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

    // =========================
    // DASHBOARD
    // =========================

    suspend fun getDashboardStats(): AdminDashboardStats {
        requireAdmin()

        val songs = firebaseService.getAllSongsWithIds()
        val reports = firebaseService.getPendingReports()
        val reportedComments = firebaseService.getReportedComments()

        return AdminDashboardStats(
            pendingSongs = songs.count { song ->
                song.status == SongStatus.PENDING && !song.isDeleted
            },
            approvedSongs = songs.count { song ->
                song.status == SongStatus.APPROVED && !song.isDeleted
            },
            rejectedSongs = songs.count { song ->
                song.status == SongStatus.REJECTED && !song.isDeleted
            },
            hiddenSongs = songs.count { song ->
                song.isDeleted
            },
            pendingReports = reports.size,
            reportedSongs = songs.count { song ->
                song.reportsCount > 0L && !song.isDeleted
            },
            reportedComments = reportedComments.size
        )
    }

    // =========================
    // SONG MODERATION
    // =========================

    suspend fun getPendingSongs(): List<Song> {
        requireAdmin()

        return firebaseService.getSongsByStatus(SongStatus.PENDING)
            .filter { song ->
                !song.isDeleted
            }
    }

    suspend fun approveSong(songId: String) {
        val adminId = requireAdmin()

        firebaseService.updateSongStatus(
            songId = songId,
            status = SongStatus.APPROVED,
            reviewedBy = adminId,
            rejectReason = ""
        )
    }

    suspend fun rejectSong(
        songId: String,
        reason: String
    ) {
        val adminId = requireAdmin()

        firebaseService.updateSongStatus(
            songId = songId,
            status = SongStatus.REJECTED,
            reviewedBy = adminId,
            rejectReason = reason
        )
    }

    suspend fun hideSong(songId: String) {
        val adminId = requireAdmin()

        firebaseService.softDeleteSong(
            songId = songId,
            deletedBy = adminId
        )
    }

    suspend fun updateSongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        requireAdmin()

        firebaseService.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }

    // =========================
    // REPORT MANAGEMENT
    // =========================

    suspend fun getPendingReports(): List<Report> {
        requireAdmin()
        return firebaseService.getPendingReports()
    }

    suspend fun resolveReport(reportId: String) {
        val adminId = requireAdmin()

        firebaseService.updateReportStatus(
            reportId = reportId,
            status = ReportStatus.RESOLVED,
            reviewedBy = adminId
        )
    }

    suspend fun rejectReport(reportId: String) {
        val adminId = requireAdmin()

        firebaseService.updateReportStatus(
            reportId = reportId,
            status = ReportStatus.REJECTED,
            reviewedBy = adminId
        )
    }

    suspend fun hideReportedTarget(report: Report) {
        val adminId = requireAdmin()

        when (report.targetType) {
            ReportTargetType.SONG -> {
                firebaseService.softDeleteSong(
                    songId = report.targetId,
                    deletedBy = adminId
                )
            }

            ReportTargetType.COMMENT -> {
                val songId = report.description
                    .split("|")
                    .getOrNull(0)
                    .orEmpty()

                if (songId.isNotBlank()) {
                    firebaseService.softDeleteComment(
                        songId = songId,
                        commentId = report.targetId,
                        deletedBy = adminId
                    )
                }
            }
        }

        firebaseService.updateReportStatus(
            reportId = report.id,
            status = ReportStatus.RESOLVED,
            reviewedBy = adminId
        )
    }

    // =========================
    // COMMENT MODERATION
    // =========================

    suspend fun getReportedComments(): List<Comment> {
        requireAdmin()
        return firebaseService.getReportedComments()
    }

    suspend fun hideComment(comment: Comment) {
        val adminId = requireAdmin()

        firebaseService.softDeleteComment(
            songId = comment.songId,
            commentId = comment.id,
            deletedBy = adminId
        )
    }
}