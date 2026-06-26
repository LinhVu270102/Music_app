package com.example.music_app.ui.notification

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.AppNotification
import com.example.music_app.data.model.AppNotificationType
import com.example.music_app.databinding.ItemNotificationBinding

class NotificationAdapter(
    private val onItemClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.ViewHolder>(NotificationDiffCallback) {

    fun setData(newItems: List<AppNotification>) {
        submitList(newItems)
    }

    inner class ViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppNotification) {
            val context = binding.root.context

            binding.tvNotificationTitle.text = when (item.type) {
                AppNotificationType.NEW_FOLLOWER ->
                    context.getString(R.string.notification_new_follower)
                AppNotificationType.NEW_LIKE ->
                    context.getString(R.string.notification_new_like)
                AppNotificationType.NEW_COMMENT ->
                    context.getString(R.string.notification_new_comment)
                else -> item.title.ifBlank {
                    context.getString(R.string.notifications)
                }
            }

            binding.tvNotificationMessage.text = item.message.ifBlank {
                context.getString(R.string.notification_default_message)
            }

            binding.tvNotificationTime.text = DateUtils.getRelativeTimeSpanString(
                item.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            binding.unreadIndicator.visibility =
                if (item.isRead) View.INVISIBLE else View.VISIBLE
            binding.root.setBackgroundColor(
                if (item.isRead) Color.TRANSPARENT else Color.rgb(38, 38, 38)
            )

            Glide.with(binding.root)
                .load(item.actorAvatarUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .circleCrop()
                .into(binding.imgActorAvatar)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object NotificationDiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(
            oldItem: AppNotification,
            newItem: AppNotification
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: AppNotification,
            newItem: AppNotification
        ): Boolean = oldItem == newItem
    }
}
