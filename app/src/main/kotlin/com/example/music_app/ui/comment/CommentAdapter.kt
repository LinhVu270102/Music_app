package com.example.music_app.ui.comment

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.databinding.ItemCommentBinding
import java.util.Locale

class CommentAdapter(
    private var currentUserId: String = "",
    private val onMoreClick: (Comment, View) -> Unit,
    private val onTimelineClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val comments = mutableListOf<Comment>()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newComments: List<Comment>) {
        comments.clear()
        comments.addAll(newComments)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateCurrentUserId(newUserId: String) {
        if (currentUserId == newUserId) return

        currentUserId = newUserId
        notifyDataSetChanged()
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            val context = binding.root.context

            val isOwner = comment.userId == currentUserId

            binding.txtCommentUser.text =
                comment.displayName.ifBlank {
                    context.getString(R.string.unknown_user)
                }

            binding.txtCommentContent.text = comment.content

            binding.txtCommentSongTime.text =
                formatTimelinePosition(comment.timelinePositionMs)

            binding.txtCommentSongTime.setOnClickListener {
                onTimelineClick(comment)
            }

            binding.txtCommentTimeAgo.text =
                formatTimeAgo(context, comment.createdAt)

            binding.txtCommentLikeCount.text =
                context.getString(R.string.default_comment_like_count)

            // Quan trọng: reset trạng thái ViewHolder, tránh bị lệch khi RecyclerView tái sử dụng item
            binding.btnLikeComment.alpha = 0.5f
            binding.txtMore.visibility = View.VISIBLE

            // Nếu muốn comment của chính mình nổi bật hơn một chút
            binding.txtMore.alpha =
                if (isOwner) {
                    1f
                } else {
                    0.8f
                }

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
                binding.btnLikeComment.alpha = 1f
            }

            binding.txtReply.setOnClickListener {
                // Reply sẽ làm sau
            }

            binding.txtMore.setOnClickListener { view ->
                onMoreClick(comment, view)
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

    private fun formatTimelinePosition(positionMs: Long): String {
        val safePosition = positionMs.coerceAtLeast(0L)

        val totalSeconds = safePosition / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    private fun formatTimeAgo(
        context: android.content.Context,
        createdAt: Long
    ): String {
        if (createdAt <= 0L) {
            return context.getString(R.string.time_now)
        }

        val now = System.currentTimeMillis()
        val diff = now - createdAt

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365

        return when {
            years > 0 -> context.getString(R.string.time_years_ago, years)
            months > 0 -> context.getString(R.string.time_months_ago, months)
            days > 0 -> context.getString(R.string.time_days_ago, days)
            hours > 0 -> context.getString(R.string.time_hours_ago, hours)
            minutes > 0 -> context.getString(R.string.time_minutes_ago, minutes)
            else -> context.getString(R.string.time_now)
        }
    }
}