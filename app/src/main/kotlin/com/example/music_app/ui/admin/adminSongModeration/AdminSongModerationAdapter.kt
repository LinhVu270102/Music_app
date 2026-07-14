package com.example.music_app.ui.admin.adminSongModeration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.FingerprintStatus
import com.example.music_app.databinding.ItemAdminSongModerationBinding
import java.util.Locale

class AdminSongModerationAdapter(
    private val onApprove: (Song) -> Unit,
    private val onReject: (Song) -> Unit,
    private val onHide: (Song) -> Unit,
    private val onToggleComments: (Song) -> Unit
) : ListAdapter<Song, AdminSongModerationAdapter.AdminSongViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AdminSongViewHolder {
        val binding = ItemAdminSongModerationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminSongViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AdminSongViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class AdminSongViewHolder(
        private val binding: ItemAdminSongModerationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            val context = binding.root.context

            binding.txtSongTitle.text = song.title
            binding.txtSongArtist.text = song.artist
            binding.txtSongGenre.text = song.genre.ifBlank {
                context.getString(R.string.unknown_genre)
            }

            binding.txtSongReports.text =
                context.getString(R.string.reports_count, song.reportsCount)

            binding.txtCommentStatus.text =
                if (song.allowComments) {
                    context.getString(R.string.comments_enabled)
                } else {
                    context.getString(R.string.comments_disabled)
                }

            bindFingerprintStatus(song)

            binding.btnToggleComments.text =
                if (song.allowComments) {
                    context.getString(R.string.lock_comments)
                } else {
                    context.getString(R.string.unlock_comments)
                }

            binding.btnApprove.setOnClickListener {
                onApprove(song)
            }

            binding.btnReject.setOnClickListener {
                onReject(song)
            }

            binding.btnHideSong.setOnClickListener {
                onHide(song)
            }

            binding.btnToggleComments.setOnClickListener {
                onToggleComments(song)
            }
        }

        private fun bindFingerprintStatus(song: Song) {
            val context = binding.root.context
            val status = song.fingerprintStatusType

            binding.txtFingerprintStatus.text = context.getString(
                R.string.fingerprint_status_format,
                status.displayLabel()
            )

            val hasDuplicateInfo = status == FingerprintStatus.DUPLICATE
            binding.txtFingerprintDuplicate.visibility =
                if (hasDuplicateInfo) View.VISIBLE else View.GONE

            if (hasDuplicateInfo) {
                binding.txtFingerprintDuplicate.text =
                    if (song.duplicateOfSongId.isNotBlank()) {
                        context.getString(
                            R.string.fingerprint_duplicate_format,
                            song.duplicateOfSongId,
                            song.duplicateScore.asPercent()
                        )
                    } else {
                        context.getString(R.string.fingerprint_duplicate_unknown)
                    }
            }

            val shouldShowError =
                status == FingerprintStatus.FAILED &&
                    song.fingerprintError.isNotBlank()

            binding.txtFingerprintError.visibility =
                if (shouldShowError) View.VISIBLE else View.GONE

            if (shouldShowError) {
                binding.txtFingerprintError.text = context.getString(
                    R.string.fingerprint_error_format,
                    song.fingerprintError
                )
            }
        }

        private fun FingerprintStatus.displayLabel(): String {
            val context = binding.root.context

            return when (this) {
                FingerprintStatus.PENDING -> context.getString(R.string.fingerprint_pending)
                FingerprintStatus.PROCESSING -> context.getString(R.string.fingerprint_processing)
                FingerprintStatus.UNIQUE -> context.getString(R.string.fingerprint_unique)
                FingerprintStatus.DUPLICATE -> context.getString(R.string.fingerprint_duplicate)
                FingerprintStatus.FAILED -> context.getString(R.string.fingerprint_failed)
            }
        }

        private fun Double.asPercent(): String {
            val normalizedScore = if (this <= 1.0) this * 100 else this
            return String.format(Locale.US, "%.0f%%", normalizedScore.coerceIn(0.0, 100.0))
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
                return oldItem == newItem
            }
        }
    }
}
