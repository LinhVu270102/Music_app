package com.example.music_app.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.databinding.ItemLibraryPlaylistBinding

class LibraryPlaylistAdapter(
    private val onItemClick: (Playlist) -> Unit
) : RecyclerView.Adapter<LibraryPlaylistAdapter.ViewHolder>() {

    private val items = mutableListOf<Playlist>()

    fun setData(newItems: List<Playlist>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemLibraryPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.txtPlaylistName.text =
                playlist.name.ifBlank {
                    binding.root.context.getString(R.string.playlist_name_placeholder)
                }

            binding.txtPlaylistInfo.text =
                binding.root.context.getString(R.string.playlist)

            binding.imgPlaylistCover.setImageResource(R.drawable.music_orange)

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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}