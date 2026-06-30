package com.example.music_app.data.repository

import com.example.music_app.R
import com.example.music_app.data.model.AdminDashboardStats
import com.example.music_app.data.model.Comment
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.data.model.enums.ReportTargetType
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.data.model.enums.UserRole
import com.example.music_app.data.remote.CommentRemoteDataSource
import com.example.music_app.data.remote.ReportRemoteDataSource
import com.example.music_app.data.remote.SongRemoteDataSource
import com.example.music_app.data.remote.UserRemoteDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val songRemoteDataSource: SongRemoteDataSource = SongRemoteDataSource(firestore),
    private val commentRemoteDataSource: CommentRemoteDataSource =
        CommentRemoteDataSource(firestore),
    private val userRemoteDataSource: UserRemoteDataSource = UserRemoteDataSource(firestore),
    private val reportRemoteDataSource: ReportRemoteDataSource =
        ReportRemoteDataSource(firestore)
) {

    // =========================
    // ADMIN AUTH GUARD
    // =========================
    suspend fun isCurrentUserAdmin(): Boolean {
        val userId = currentUserIdOrNull() ?: return false

        val user = userRemoteDataSource.getById(userId) ?: return false

        return user.isAdmin()
    }

    private suspend fun requireAdmin(): String {
        val userId = currentUserIdOrNull() ?: throw AppException(R.string.not_logged_in)

        val user = userRemoteDataSource.getById(userId)
            ?: throw AppException(R.string.account_not_found)

        if (!user.isAdmin()) {
            throw AppException(R.string.no_admin_permission)
        }

        return userId
    }

    // =========================
    // DASHBOARD
    // =========================

    suspend fun getDashboardStats(): AdminDashboardStats {
        requireAdmin()

        val songs = songRemoteDataSource.getAllSongsWithIds()
        val reports = reportRemoteDataSource.getPending()
        val reportedComments = commentRemoteDataSource.getReported()

        return AdminDashboardStats(
            pendingSongs = songs.count { song ->
                song.hasVisibleStatus(SongStatus.PENDING)
            },
            approvedSongs = songs.count { song ->
                song.hasVisibleStatus(SongStatus.APPROVED)
            },
            rejectedSongs = songs.count { song ->
                song.hasVisibleStatus(SongStatus.REJECTED)
            },
            hiddenSongs = songs.count { song ->
                song.isDeleted
            },
            pendingReports = reports.size,
            reportedSongs = songs.count { song ->
                song.isReportedVisible()
            },
            reportedComments = reportedComments.size
        )
    }

    // =========================
    // SONG MODERATION
    // =========================

    suspend fun getPendingSongs(): List<Song> {
        requireAdmin()

        return songRemoteDataSource.getSongsByStatus(SongStatus.PENDING.value)
            .filter { song -> song.isVisibleForAdmin() }
    }

    suspend fun approveSong(songId: String) {
        updateSongStatus(
            songId = songId,
            status = SongStatus.APPROVED,
            rejectReason = ""
        )
    }

    suspend fun rejectSong(
        songId: String,
        reason: String
    ) {
        updateSongStatus(
            songId = songId,
            status = SongStatus.REJECTED,
            rejectReason = reason
        )
    }

    suspend fun hideSong(songId: String) {
        val adminId = requireAdmin()

        songRemoteDataSource.softDeleteSong(
            songId = songId,
            deletedBy = adminId
        )
    }

    suspend fun updateSongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        requireAdmin()

        songRemoteDataSource.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }

    // =========================
    // REPORT MANAGEMENT
    // =========================

    suspend fun getPendingReports(): List<Report> {
        requireAdmin()
        return reportRemoteDataSource.getPending()
    }

    suspend fun resolveReport(reportId: String) {
        updateReportStatus(
            reportId = reportId,
            status = ReportStatus.RESOLVED
        )
    }

    suspend fun rejectReport(reportId: String) {
        updateReportStatus(
            reportId = reportId,
            status = ReportStatus.REJECTED
        )
    }

    suspend fun hideReportedTarget(report: Report) {
        val adminId = requireAdmin()

        when (report.targetKind) {
            ReportTargetType.SONG -> hideReportedSong(report, adminId)

            ReportTargetType.COMMENT -> hideReportedComment(report, adminId)

            ReportTargetType.USER -> Unit
        }

        reportRemoteDataSource.updateStatus(
            reportId = report.id,
            status = ReportStatus.RESOLVED.value,
            reviewedBy = adminId
        )
    }

    // =========================
    // COMMENT MODERATION
    // =========================

    suspend fun getReportedComments(): List<Comment> {
        requireAdmin()
        return commentRemoteDataSource.getReported()
    }

    suspend fun hideComment(comment: Comment) {
        val adminId = requireAdmin()

        commentRemoteDataSource.softDelete(
            songId = comment.songId,
            commentId = comment.id,
            deletedBy = adminId
        )
    }

    private fun currentUserIdOrNull(): String? {
        return auth.currentUser?.uid?.takeIf(String::isNotBlank)
    }

    private suspend fun updateSongStatus(
        songId: String,
        status: SongStatus,
        rejectReason: String = ""
    ) {
        val adminId = requireAdmin()

        songRemoteDataSource.updateSongStatus(
            songId = songId,
            status = status.value,
            reviewedBy = adminId,
            rejectReason = rejectReason
        )
    }

    private suspend fun updateReportStatus(
        reportId: String,
        status: ReportStatus
    ) {
        val adminId = requireAdmin()

        reportRemoteDataSource.updateStatus(
            reportId = reportId,
            status = status.value,
            reviewedBy = adminId
        )
    }

    private suspend fun hideReportedSong(report: Report, adminId: String) {
        songRemoteDataSource.softDeleteSong(
            songId = report.targetId,
            deletedBy = adminId
        )
    }

    private suspend fun hideReportedComment(report: Report, adminId: String) {
        val songId = report.commentSongId()
        if (songId.isBlank()) return

        commentRemoteDataSource.softDelete(
            songId = songId,
            commentId = report.targetId,
            deletedBy = adminId
        )
    }

    private fun Report.commentSongId(): String {
        return description
            .split("|")
            .getOrNull(0)
            .orEmpty()
    }

    private fun Song.hasVisibleStatus(status: SongStatus): Boolean {
        return statusType == status && isVisibleForAdmin()
    }

    private fun Song.isReportedVisible(): Boolean {
        return reportsCount > 0L && isVisibleForAdmin()
    }

    private fun Song.isVisibleForAdmin(): Boolean {
        return !isDeleted
    }

    private fun User.isAdmin(): Boolean {
        return roleType == UserRole.ADMIN
    }
}
