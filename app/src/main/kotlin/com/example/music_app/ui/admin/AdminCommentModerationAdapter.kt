package com.example.music_app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.databinding.ItemAdminCommentModerationBinding

class AdminCommentModerationAdapter(
    private val onHideComment: (Comment) -> Unit
) : ListAdapter<Comment, AdminCommentModerationAdapter.AdminCommentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AdminCommentViewHolder {
        val binding = ItemAdminCommentModerationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminCommentViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AdminCommentViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class AdminCommentViewHolder(
        private val binding: ItemAdminCommentModerationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val context = binding.root.context

            binding.txtCommentUser.text = comment.displayName
            binding.txtCommentContent.text = comment.content
            binding.txtCommentSong.text =
                context.getString(R.string.comment_song_format, comment.songId)

            binding.btnHideComment.setOnClickListener {
                onHideComment(comment)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
                return oldItem == newItem
            }
        }
    }
}