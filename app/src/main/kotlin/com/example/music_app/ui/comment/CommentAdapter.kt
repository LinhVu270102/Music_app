package com.example.music_app.ui.comment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.databinding.ItemCommentBinding

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val comments = mutableListOf<Comment>()

    fun setData(newComments: List<Comment>) {
        comments.clear()
        comments.addAll(newComments)
        notifyDataSetChanged()
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val context = binding.root.context

            binding.txtCommentUser.text =
                comment.displayName.ifBlank {
                    context.getString(R.string.unknown_user)
                }

            binding.txtCommentContent.text = comment.content

            binding.txtCommentSongTime.text =
                context.getString(R.string.comment_song_time_placeholder)

            binding.txtCommentTimeAgo.text = formatTimeAgo(comment.createdAt)

            binding.txtCommentLikeCount.text =
                context.getString(R.string.default_comment_like_count)

            Glide.with(context)
                .load(comment.avatarUrl)
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .circleCrop()
                .into(binding.imgCommentAvatar)

            Glide.with(context)
                .load(comment.avatarUrl)
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .circleCrop()
                .into(binding.imgSmallAvatar)

            binding.btnLikeComment.setOnClickListener {
                // Bước sau sẽ làm like comment thật
                binding.btnLikeComment.alpha = 1f
            }

            binding.txtReply.setOnClickListener {
                // Bước sau sẽ làm reply comment
            }

            binding.txtMore.setOnClickListener {
                // Bước sau sẽ làm menu xoá / báo cáo comment
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    private fun formatTimeAgo(createdAt: Long): String {
        if (createdAt <= 0L) return "now"

        val now = System.currentTimeMillis()
        val diff = now - createdAt

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365

        return when {
            years > 0 -> "${years}y"
            months > 0 -> "${months}mo"
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "now"
        }
    }
}