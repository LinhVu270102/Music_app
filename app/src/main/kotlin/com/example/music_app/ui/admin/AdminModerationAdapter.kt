package com.example.music_app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.databinding.ItemAdminModerationSongBinding

class AdminModerationAdapter(
    private val onPreviewClick: (Song) -> Unit,
    private val onApproveClick: (Song) -> Unit,
    private val onRejectClick: (Song) -> Unit,
    private val onHideClick: (Song) -> Unit,
    private val onRestoreClick: (Song) -> Unit
) : RecyclerView.Adapter<AdminModerationAdapter.AdminSongViewHolder>() {

    private val songs = mutableListOf<Song>()

    fun submitList(newSongs: List<Song>) {
        songs.clear()
        songs.addAll(newSongs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminSongViewHolder {
        val binding = ItemAdminModerationSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return AdminSongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminSongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size

    inner class AdminSongViewHolder(
        private val binding: ItemAdminModerationSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            val context = binding.root.context

            binding.txtTitle.text = song.title
            binding.txtArtist.text = song.artist

            binding.txtUploader.text = context.getString(
                R.string.uploaded_by_value,
                song.uploaderId.ifBlank { context.getString(R.string.unknown_user) }
            )

            binding.txtStatus.text = when (song.status) {
                SongStatus.APPROVED -> context.getString(R.string.approved)
                SongStatus.REJECTED -> context.getString(R.string.rejected)
                SongStatus.HIDDEN -> context.getString(R.string.hidden)
                else -> context.getString(R.string.pending)
            }

            if (song.rejectReason.isBlank()) {
                binding.txtRejectReason.visibility = View.GONE
            } else {
                binding.txtRejectReason.visibility = View.VISIBLE
                binding.txtRejectReason.text = context.getString(
                    R.string.reject_reason_value,
                    song.rejectReason
                )
            }

            Glide.with(binding.imgCover.context)
                .load(song.coverUrl)
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .into(binding.imgCover)

            binding.btnPreview.setOnClickListener {
                onPreviewClick(song)
            }

            binding.btnApprove.setOnClickListener {
                onApproveClick(song)
            }

            binding.btnReject.setOnClickListener {
                onRejectClick(song)
            }

            binding.btnHide.setOnClickListener {
                onHideClick(song)
            }

            updateActionButtons(song.status)
        }

        private fun updateActionButtons(status: String) {
            when (status) {
                SongStatus.PENDING -> {
                    binding.btnApprove.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.VISIBLE
                    binding.btnHide.visibility = View.GONE

                    binding.btnApprove.text =
                        binding.root.context.getString(R.string.approve)
                }

                SongStatus.APPROVED -> {
                    binding.btnApprove.visibility = View.GONE
                    binding.btnReject.visibility = View.VISIBLE
                    binding.btnHide.visibility = View.VISIBLE
                }

                SongStatus.REJECTED -> {
                    binding.btnApprove.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.GONE
                    binding.btnHide.visibility = View.GONE

                    binding.btnApprove.text =
                        binding.root.context.getString(R.string.restore)
                }

                SongStatus.HIDDEN -> {
                    binding.btnApprove.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.VISIBLE
                    binding.btnHide.visibility = View.GONE

                    binding.btnApprove.text =
                        binding.root.context.getString(R.string.restore)
                }

                else -> {
                    binding.btnApprove.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.VISIBLE
                    binding.btnHide.visibility = View.GONE

                    binding.btnApprove.text =
                        binding.root.context.getString(R.string.approve)
                }
            }
        }
    }
}