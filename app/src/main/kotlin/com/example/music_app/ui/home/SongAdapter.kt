package com.example.music_app.ui.song

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemSongBinding

class SongAdapter(
    private val onItemClick: (Song) -> Unit,
    private val onItemLongClick: ((Song) -> Unit)? = null,
    private val onMoreClick: ((Song, View) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private val songs = mutableListOf<Song>()

    fun setData(newSongs: List<Song>) {
        if (songs == newSongs) return

        songs.clear()
        songs.addAll(newSongs)
        notifyDataSetChanged()
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.txtTitle.text = song.title
            binding.txtArtist.text = song.artist

            Glide.with(binding.root)
                .load(song.coverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .dontAnimate()
                .centerCrop()
                .into(binding.imgCover)

            binding.btnMore.visibility =
                if (onMoreClick != null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            binding.root.setOnClickListener {
                onItemClick(song)
            }

            binding.root.setOnLongClickListener {
                if (onItemLongClick != null) {
                    onItemLongClick.invoke(song)
                    true
                } else {
                    false
                }
            }

            binding.btnMore.setOnClickListener { anchor ->
                onMoreClick?.invoke(song, anchor)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: SongViewHolder,
        position: Int
    ) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int {
        return songs.size
    }
}