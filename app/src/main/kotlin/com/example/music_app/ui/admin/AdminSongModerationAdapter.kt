package com.example.music_app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemAdminSongModerationBinding

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