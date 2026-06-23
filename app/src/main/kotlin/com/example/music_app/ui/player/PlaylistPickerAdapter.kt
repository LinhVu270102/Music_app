package com.example.music_app.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.databinding.ItemSelectPlaylistBinding

class PlaylistPickerAdapter(
    private val onItemClick: (PlaylistPickerItem) -> Unit
) : ListAdapter<PlaylistPickerItem, PlaylistPickerAdapter.PlaylistPickerViewHolder>(
    PlaylistPickerDiffCallback
) {

    fun setData(newItems: List<PlaylistPickerItem>) {
        submitList(newItems)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PlaylistPickerViewHolder {
        val binding = ItemSelectPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return PlaylistPickerViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PlaylistPickerViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class PlaylistPickerViewHolder(
        private val binding: ItemSelectPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlaylistPickerItem) {
            binding.txtPlaylistName.text = item.name
            binding.txtPlaylistSubtitle.text = item.subtitle

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private object PlaylistPickerDiffCallback : DiffUtil.ItemCallback<PlaylistPickerItem>() {
        override fun areItemsTheSame(
            oldItem: PlaylistPickerItem,
            newItem: PlaylistPickerItem
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: PlaylistPickerItem,
            newItem: PlaylistPickerItem
        ): Boolean = oldItem == newItem
    }
}
