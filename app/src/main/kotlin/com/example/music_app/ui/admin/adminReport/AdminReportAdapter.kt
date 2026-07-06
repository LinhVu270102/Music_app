package com.example.music_app.ui.admin.adminReport

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.data.model.enums.ReportStatus
import com.example.music_app.databinding.ItemAdminReportBinding

class AdminReportAdapter(
    private val onResolve: (Report) -> Unit,
    private val onReject: (Report) -> Unit,
    private val onReopen: (Report) -> Unit,
    private val onHideTarget: (Report) -> Unit
) : ListAdapter<Report, AdminReportAdapter.AdminReportViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AdminReportViewHolder {
        val binding = ItemAdminReportBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminReportViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AdminReportViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class AdminReportViewHolder(
        private val binding: ItemAdminReportBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(report: Report) {
            val context = binding.root.context

            binding.txtReportReason.text = report.reason
            binding.txtReportDescription.text =
                report.displayDescription().ifBlank {
                    context.getString(R.string.no_report_description)
                }

            binding.txtReportTarget.text =
                if (report.targetTitle.isNotBlank() || report.targetPreview.isNotBlank()) {
                    context.getString(
                        R.string.report_target_detail_format,
                        report.targetKind.value,
                        report.displayTargetTitle(),
                        report.displayTargetSubtitle(),
                        report.targetId
                    )
                } else {
                    context.getString(
                        R.string.report_target_format,
                        report.targetKind.value,
                        report.targetId
                    )
                }

            binding.txtReporter.text =
                if (report.reportOwnerId().isNotBlank()) {
                    context.getString(
                        R.string.reporter_and_owner_format,
                        report.reporterId,
                        report.reportOwnerId()
                    )
                } else {
                    context.getString(
                        R.string.reporter_format,
                        report.reporterId
                    )
                }

            binding.txtReportStatus.text = report.displayReviewStatus()

            bindActions(report)
        }

        private fun bindActions(report: Report) {
            val context = binding.root.context
            val isPending = report.statusType == ReportStatus.PENDING

            binding.btnResolveReport.visibility = View.VISIBLE
            binding.btnRejectReport.visibility = View.VISIBLE
            binding.btnHideReportedTarget.visibility = View.VISIBLE

            binding.btnResolveReport.text =
                if (isPending) {
                    context.getString(R.string.resolve_report)
                } else {
                    context.getString(R.string.mark_report_resolved)
                }

            binding.btnRejectReport.text =
                if (isPending) {
                    context.getString(R.string.reject_report)
                } else {
                    context.getString(R.string.mark_report_rejected)
                }

            binding.btnHideReportedTarget.text =
                if (isPending) {
                    context.getString(R.string.hide_reported_target)
                } else {
                    context.getString(R.string.reopen_report)
                }

            binding.btnResolveReport.setOnClickListener {
                onResolve(report)
            }

            binding.btnRejectReport.setOnClickListener {
                onReject(report)
            }

            binding.btnHideReportedTarget.setOnClickListener {
                if (isPending) {
                    onHideTarget(report)
                } else {
                    onReopen(report)
                }
            }
        }

        private fun Report.reportOwnerId(): String {
            return songOwnerId.ifBlank { targetOwnerId }
        }

        private fun Report.displayTargetTitle(): String {
            return targetTitle.ifBlank { targetId }
        }

        private fun Report.displayTargetSubtitle(): String {
            return targetSubtitle
                .ifBlank { targetPreview }
                .ifBlank { songOwnerId }
                .ifBlank { targetOwnerId }
        }

        private fun Report.displayDescription(): String {
            return if (targetKind.value.equals("comment", ignoreCase = true)) {
                description.substringAfter("|", description)
            } else {
                description
            }
        }

        private fun Report.displayReviewStatus(): String {
            val context = binding.root.context

            return if (reviewedBy.isBlank()) {
                context.getString(R.string.report_status_format, statusType.value)
            } else {
                context.getString(
                    R.string.report_status_reviewed_by_format,
                    statusType.value,
                    reviewedBy
                )
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Report>() {
            override fun areItemsTheSame(oldItem: Report, newItem: Report): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Report, newItem: Report): Boolean {
                return oldItem == newItem
            }
        }
    }
}
