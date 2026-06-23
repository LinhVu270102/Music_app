package com.example.music_app.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.databinding.ItemLibraryPlaylistBinding

class LibraryPlaylistAdapter(
    private val onItemClick: (Playlist) -> Unit
) : ListAdapter<Playlist, LibraryPlaylistAdapter.ViewHolder>(DiffCallback) {

    fun setData(newItems: List<Playlist>) {
        submitList(newItems.toList())
    }

    inner class ViewHolder(
        private val binding: ItemLibraryPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.txtPlaylistName.text =
                playlist.name.ifBlank {
                    binding.root.context.getString(R.string.playlist_name_placeholder)
                }

            binding.txtPlaylistInfo.text = binding.root.context.getString(
                R.string.playlist_songs_count,
                playlist.songsCount
            )

            Glide.with(binding.root)
                .load(playlist.coverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgPlaylistCover)

            binding.root.setOnClickListener {
                onItemClick(playlist)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLibraryPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
                return oldItem == newItem
            }
        }
    }
}
