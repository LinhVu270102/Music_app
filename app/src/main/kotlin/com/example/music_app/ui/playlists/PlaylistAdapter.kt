package com.example.music_app.ui.playlists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.data.model.Playlist
import com.example.music_app.databinding.ItemPlaylistBinding
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.repository.SoundCloudSocialRepository

class PlaylistAdapter(
    private val onItemClick: (Playlist) -> Unit,
    private val onDeleteClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private val playlists = mutableListOf<Playlist>()

    fun setData(newPlaylists: List<Playlist>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        notifyDataSetChanged()
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            val context = binding.root.context
            val isApiPlaylist = SoundCloudSocialRepository.isSoundCloudApiPlaylist(playlist)

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
        holder.bind(playlists[position])
    }

    override fun getItemCount(): Int = playlists.size
}