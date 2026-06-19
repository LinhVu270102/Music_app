package com.example.music_app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemMiniPlayerPageBinding

class MiniPlayerPagerAdapter(
    private val onSongClick: (Song) -> Unit
) : ListAdapter<Song, MiniPlayerPagerAdapter.MiniPlayerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MiniPlayerViewHolder {
        val binding = ItemMiniPlayerPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MiniPlayerViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MiniPlayerViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class MiniPlayerViewHolder(
        private val binding: ItemMiniPlayerPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.txtTitle.text = song.title
            binding.txtArtist.text = song.artist

            binding.root.setOnClickListener {
                onSongClick(song)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(
                oldItem: Song,
                newItem: Song
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: Song,
                newItem: Song
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}