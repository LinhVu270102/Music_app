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
import com.example.music_app.data.firebase.firestore.CommentFirestoreDataSource
import com.example.music_app.data.firebase.firestore.ReportFirestoreDataSource
import com.example.music_app.data.firebase.firestore.SongFirestoreDataSource
import com.example.music_app.data.firebase.firestore.UserFirestoreDataSource
import com.example.music_app.utils.AppException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val songFirestoreDataSource: SongFirestoreDataSource = SongFirestoreDataSource(firestore),
    private val commentFirestoreDataSource: CommentFirestoreDataSource =
        CommentFirestoreDataSource(firestore),
    private val userFirestoreDataSource: UserFirestoreDataSource = UserFirestoreDataSource(firestore),
    private val reportFirestoreDataSource: ReportFirestoreDataSource =
        ReportFirestoreDataSource(firestore)
) {

    // =========================
    // ADMIN AUTH GUARD
    // =========================
    suspend fun isCurrentUserAdmin(): Boolean {
        return isCurrentUserModerationStaff()
    }

    suspend fun isCurrentUserModerationStaff(): Boolean {
        val userId = currentUserIdOrNull() ?: return false

        val user = userFirestoreDataSource.getById(userId) ?: return false

        return user.canModerateContent()
    }

    private suspend fun requireModerationStaff(): String {
        val userId = currentUserIdOrNull() ?: throw AppException(R.string.not_logged_in)

        val user = userFirestoreDataSource.getById(userId)
            ?: throw AppException(R.string.account_not_found)

        if (!user.canModerateContent()) {
            throw AppException(R.string.no_admin_permission)
        }

        return userId
    }

    // =========================
    // DASHBOARD
    // =========================

    suspend fun getDashboardStats(): AdminDashboardStats {
        requireModerationStaff()

        val songs = songFirestoreDataSource.getAllSongsWithIds()
        val reports = runCatching { reportFirestoreDataSource.getPending() }
            .getOrDefault(emptyList())

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
            reportedComments = reports.count { report ->
                report.targetKind == ReportTargetType.COMMENT
            }
        )
    }

    // =========================
    // SONG MODERATION
    // =========================

    suspend fun getPendingSongs(): List<Song> {
        requireModerationStaff()

        val pendingSongs = songFirestoreDataSource.getSongsByStatus(SongStatus.PENDING.value)
        val legacyPendingSongs = songFirestoreDataSource.getSongsByStatus(SongStatus.PENDING.name)

        return (pendingSongs + legacyPendingSongs)
            .distinctBy { song -> song.id }
            .filter { song -> song.isVisibleForAdmin() }
            .sortedByDescending { song -> song.createdAt }
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
        val moderatorId = requireModerationStaff()

        songFirestoreDataSource.softDeleteSong(
            songId = songId,
            deletedBy = moderatorId
        )
    }

    suspend fun updateSongCommentPermission(
        songId: String,
        allowComments: Boolean
    ) {
        requireModerationStaff()

        songFirestoreDataSource.updateSongCommentPermission(
            songId = songId,
            allowComments = allowComments
        )
    }

    // =========================
    // REPORT MANAGEMENT
    // =========================

    suspend fun getPendingReports(): List<Report> {
        requireModerationStaff()
        return reportFirestoreDataSource.getPending()
            .map { report -> enrichReportSafely(report) }
    }

    suspend fun getReviewedReports(): List<Report> {
        requireModerationStaff()
        return reportFirestoreDataSource.getReviewed()
            .map { report -> enrichReportSafely(report) }
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

    suspend fun reopenReport(reportId: String) {
        val moderatorId = requireModerationStaff()

        reportFirestoreDataSource.reopen(
            reportId = reportId,
            reopenedBy = moderatorId
        )
    }

    suspend fun hideReportedTarget(report: Report) {
        val moderatorId = requireModerationStaff()

        when (report.targetKind) {
            ReportTargetType.SONG -> hideReportedSong(report, moderatorId)

            ReportTargetType.COMMENT -> hideReportedComment(report, moderatorId)

            ReportTargetType.USER -> Unit
        }

        reportFirestoreDataSource.updateStatus(
            reportId = report.id,
            status = ReportStatus.RESOLVED.value,
            reviewedBy = moderatorId
        )
    }

    // =========================
    // COMMENT MODERATION
    // =========================

    suspend fun getReportedComments(): List<Comment> {
        requireModerationStaff()

        return reportFirestoreDataSource.getPending()
            .filter { report -> report.targetKind == ReportTargetType.COMMENT }
            .map { report ->
                runCatching {
                    report.toReportedComment()
                }.getOrDefault(report.toReportedComment())
            }
            .distinctBy { comment -> "${comment.songId}/${comment.id}" }
            .sortedByDescending { comment -> comment.createdAt }
    }

    suspend fun hideComment(comment: Comment) {
        val moderatorId = requireModerationStaff()

        commentFirestoreDataSource.softDelete(
            songId = comment.songId,
            commentId = comment.id,
            deletedBy = moderatorId
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
        val moderatorId = requireModerationStaff()

        songFirestoreDataSource.updateSongStatus(
            songId = songId,
            status = status.value,
            reviewedBy = moderatorId,
            rejectReason = rejectReason
        )
    }

    private suspend fun updateReportStatus(
        reportId: String,
        status: ReportStatus
    ) {
        val moderatorId = requireModerationStaff()

        reportFirestoreDataSource.updateStatus(
            reportId = reportId,
            status = status.value,
            reviewedBy = moderatorId
        )
    }

    private suspend fun hideReportedSong(report: Report, moderatorId: String) {
        songFirestoreDataSource.softDeleteSong(
            songId = report.targetId,
            deletedBy = moderatorId
        )
    }

    private suspend fun hideReportedComment(report: Report, moderatorId: String) {
        val songId = report.songId.ifBlank { report.commentSongId() }
        if (songId.isBlank()) return

        commentFirestoreDataSource.softDelete(
            songId = songId,
            commentId = report.targetId,
            deletedBy = moderatorId
        )
    }

    private fun Report.commentSongId(): String {
        return description
            .split("|")
            .getOrNull(0)
            .orEmpty()
    }

    private suspend fun enrichReportSafely(report: Report): Report {
        return runCatching {
            enrichReportForDisplay(report)
        }.getOrDefault(report)
    }

    private suspend fun Report.toReportedComment(): Comment {
        val songId = songId.ifBlank { commentSongId() }
        val fallbackComment = toFallbackReportedComment(songId)

        if (songId.isBlank() || targetId.isBlank()) return fallbackComment

        return runCatching {
            commentFirestoreDataSource.getAll(songId)
                .firstOrNull { comment -> comment.id == targetId }
                ?.copy(songId = songId)
        }.getOrNull() ?: fallbackComment
    }

    private fun Report.toFallbackReportedComment(songId: String): Comment {
        return Comment(
            id = targetId,
            songId = songId,
            userId = targetOwnerId,
            displayName = targetTitle.ifBlank { targetOwnerId },
            content = targetPreview.ifBlank { reportDescriptionText() },
            reportsCount = 1L,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Report.reportDescriptionText(): String {
        return description.substringAfter("|", description)
    }

    private suspend fun enrichReportForDisplay(report: Report): Report {
        if (
            report.targetTitle.isNotBlank() &&
            report.targetPreview.isNotBlank() &&
            report.songOwnerId.isNotBlank()
        ) {
            return report
        }

        return when (report.targetKind) {
            ReportTargetType.SONG -> enrichSongReport(report)
            ReportTargetType.COMMENT -> enrichCommentReport(report)
            ReportTargetType.USER -> report
        }
    }

    private suspend fun enrichSongReport(report: Report): Report {
        val songId = report.songId.ifBlank { report.targetId }
        val song = songFirestoreDataSource.getSongById(songId) ?: return report

        return report.copy(
            targetOwnerId = report.targetOwnerId.ifBlank { song.uploaderId },
            songId = report.songId.ifBlank { song.id },
            songOwnerId = report.songOwnerId.ifBlank { song.uploaderId },
            targetTitle = report.targetTitle.ifBlank { song.title.ifBlank { song.id } },
            targetSubtitle = report.targetSubtitle.ifBlank { song.artist.ifBlank { song.uploaderId } },
            targetPreview = report.targetPreview.ifBlank { song.genre }
        )
    }

    private suspend fun enrichCommentReport(report: Report): Report {
        val songId = report.songId.ifBlank { report.commentSongId() }
        val song = songFirestoreDataSource.getSongById(songId)
        val comment = commentFirestoreDataSource.getAll(songId)
            .firstOrNull { comment -> comment.id == report.targetId }

        return report.copy(
            targetOwnerId = report.targetOwnerId.ifBlank { comment?.userId.orEmpty() },
            songId = report.songId.ifBlank { songId },
            songOwnerId = report.songOwnerId.ifBlank { song?.uploaderId.orEmpty() },
            targetTitle = report.targetTitle.ifBlank {
                comment?.displayName?.ifBlank { comment.userId }.orEmpty()
            },
            targetSubtitle = report.targetSubtitle.ifBlank { song?.title.orEmpty() },
            targetPreview = report.targetPreview.ifBlank { comment?.content.orEmpty() }
        )
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

    private fun User.canModerateContent(): Boolean {
        return roleType.canModerateContent
    }
}
