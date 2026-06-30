package com.example.music_app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Report
import com.example.music_app.databinding.ItemAdminReportBinding

class AdminReportAdapter(
    private val onResolve: (Report) -> Unit,
    private val onReject: (Report) -> Unit,
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
                report.description.ifBlank {
                    context.getString(R.string.no_report_description)
                }

            binding.txtReportTarget.text =
                context.getString(
                    R.string.report_target_format,
                    report.targetKind.value,
                    report.targetId
                )

            binding.txtReporter.text =
                context.getString(
                    R.string.reporter_format,
                    report.reporterId
                )

            binding.btnResolveReport.setOnClickListener {
                onResolve(report)
            }

            binding.btnRejectReport.setOnClickListener {
                onReject(report)
            }

            binding.btnHideReportedTarget.setOnClickListener {
                onHideTarget(report)
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
