package com.example.music_app.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemCurrentPlaylistSongBinding

class CurrentPlaylistAdapter(
    private val onItemClick: (index: Int, song: Song) -> Unit
) : ListAdapter<Song, CurrentPlaylistAdapter.CurrentPlaylistViewHolder>(SongDiffCallback) {

    private var currentIndex: Int = -1

    fun setData(
        newSongs: List<Song>,
        newCurrentIndex: Int
    ) {
        val previousCurrentIndex = currentIndex
        currentIndex = newCurrentIndex
        submitList(newSongs.toList()) {
            listOf(previousCurrentIndex, currentIndex)
                .distinct()
                .filter { index -> index in 0 until itemCount }
                .forEach(::notifyItemChanged)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CurrentPlaylistViewHolder {
        val binding = ItemCurrentPlaylistSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return CurrentPlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: CurrentPlaylistViewHolder,
        position: Int
    ) {
        holder.bind(
            song = getItem(position),
            index = position,
            isCurrent = position == currentIndex
        )
    }

    inner class CurrentPlaylistViewHolder(
        private val binding: ItemCurrentPlaylistSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            song: Song,
            index: Int,
            isCurrent: Boolean
        ) {
            binding.txtIndex.text =
                if (isCurrent) {
                    "▶"
                } else {
                    "${index + 1}"
                }

            binding.txtSongTitle.text = song.title
            binding.txtSongArtist.text = song.artist

            binding.txtSongTitle.setTextColor(
                binding.root.context.getColor(
                    if (isCurrent) {
                        R.color.orange
                    } else {
                        R.color.white
                    }
                )
            )

            binding.root.setOnClickListener {
                onItemClick(index, song)
            }
        }
    }

    private object SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}
