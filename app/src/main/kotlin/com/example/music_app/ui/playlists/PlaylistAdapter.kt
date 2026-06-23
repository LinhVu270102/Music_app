package com.example.music_app.ui.playlists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.isSoundCloudApiPlaylist
import com.example.music_app.databinding.ItemPlaylistBinding
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.music_app.R

class PlaylistAdapter(
    private val onItemClick: (Playlist) -> Unit,
    private val onDeleteClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder>(DiffCallback) {

    fun setData(newPlaylists: List<Playlist>) {
        submitList(newPlaylists.toList())
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            val context = binding.root.context
            val isApiPlaylist = playlist.isSoundCloudApiPlaylist()

            binding.txtPlaylistName.text = playlist.name

            binding.txtPlaylistInfo.text =
                context.getString(
                    if (isApiPlaylist) {
                        R.string.soundcloud_playlist_count_format
                    } else {
                        R.string.playlist_songs_count
                    },
                    playlist.songsCount
                )

            Glide.with(binding.root)
                .load(playlist.coverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgPlaylistCover)

            binding.btnDeletePlaylist.isVisible = !isApiPlaylist

            binding.root.setOnClickListener {
                onItemClick(playlist)
            }

            binding.btnDeletePlaylist.setOnClickListener {
                onDeleteClick(playlist)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
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
