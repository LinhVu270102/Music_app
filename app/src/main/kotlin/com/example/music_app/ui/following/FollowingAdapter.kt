package com.example.music_app.ui.following

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.User
import com.example.music_app.databinding.ItemFollowingUserBinding

class FollowingAdapter(
    private val onItemClick: (User) -> Unit
) : ListAdapter<User, FollowingAdapter.FollowingViewHolder>(FollowingUserDiffCallback) {

    fun setData(newUsers: List<User>) {
        submitList(newUsers)
    }

    inner class FollowingViewHolder(
        private val binding: ItemFollowingUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.txtDisplayName.text =
                user.displayName.ifBlank {
                    user.email.ifBlank {
                        binding.root.context.getString(R.string.unknown_user)
                    }
                }

            binding.txtUsername.text =
                if (user.username.isNotBlank()) {
                    "@${user.username}"
                } else {
                    user.email
                }

            Glide.with(binding.root.context)
                .load(user.avatarUrl)
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .circleCrop()
                .into(binding.imgAvatar)

            binding.root.setOnClickListener {
                onItemClick(user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingViewHolder {
        val binding = ItemFollowingUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return FollowingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FollowingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object FollowingUserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
