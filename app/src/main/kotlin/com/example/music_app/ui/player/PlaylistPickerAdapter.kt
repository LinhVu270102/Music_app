package com.example.music_app.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.data.model.PlaylistPickerItem
import com.example.music_app.databinding.ItemSelectPlaylistBinding

class PlaylistPickerAdapter(
    private val onItemClick: (PlaylistPickerItem) -> Unit
) : RecyclerView.Adapter<PlaylistPickerAdapter.PlaylistPickerViewHolder>() {

    private val items = mutableListOf<PlaylistPickerItem>()

    fun setData(newItems: List<PlaylistPickerItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

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
}