package com.example.music_app.ui.song

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemSongBinding

class SongAdapter(
    private val onItemClick: (Song) -> Unit,
    private val onItemLongClick: ((Song) -> Unit)? = null,
    private val onMoreClick: ((Song, View) -> Unit)? = null,
    private val onLikeClick: ((Song) -> Unit)? = null,
    private val isSongLiked: (Song) -> Boolean = { false },
    private val useFullWidth: Boolean = false
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DiffCallback) {

    private var likedSongIds: Set<String> = emptySet()

    fun setData(newSongs: List<Song>) {
        // Keep an immutable snapshot so DiffUtil can compare lists safely.
        submitList(newSongs.toList())
    }

    fun setLikedSongIds(newLikedSongIds: Set<String>) {
        if (likedSongIds == newLikedSongIds) return

        val previousLikedSongIds = likedSongIds
        likedSongIds = newLikedSongIds

        currentList.forEachIndexed { index, song ->
            val wasLiked = song.id in previousLikedSongIds
            val isLiked = song.id in newLikedSongIds

            if (wasLiked != isLiked) {
                notifyItemChanged(index)
            }
        }
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

            if (useFullWidth) {
                binding.root.layoutParams = binding.root.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }

            binding.root.setOnClickListener { onItemClick(song) }

            binding.root.setOnLongClickListener {
                onItemLongClick?.let {
                    it(song)
                    true
                } ?: false
            }

            bindMoreButton(song)
            bindLikeButton(song)
        }

        private fun bindMoreButton(song: Song) {
            if (onMoreClick == null) {
                binding.btnMore.visibility = View.GONE
                binding.btnMore.isClickable = false
                binding.btnMore.isFocusable = false
                binding.btnMore.setOnClickListener(null)
                return
            }

            binding.btnMore.visibility = View.VISIBLE
            binding.btnMore.isEnabled = true
            binding.btnMore.isClickable = true
            binding.btnMore.isFocusable = true
            binding.btnMore.setImageResource(R.drawable.ic_more_vert)
            binding.btnMore.contentDescription =
                binding.root.context.getString(R.string.song_options)
            binding.btnMore.alpha = 1f
            binding.btnMore.setOnClickListener { anchor ->
                onMoreClick.invoke(song, anchor)
            }
        }

        private fun bindLikeButton(song: Song) {
            if (onLikeClick == null) {
                binding.btnLike.visibility = View.GONE
                binding.btnLike.isClickable = false
                binding.btnLike.isFocusable = false
                binding.btnLike.setOnClickListener(null)
                return
            }

            val liked = song.id in likedSongIds || isSongLiked(song)

            binding.btnLike.visibility = View.VISIBLE
            binding.btnLike.isEnabled = true
            binding.btnLike.isClickable = true
            binding.btnLike.isFocusable = true
            binding.btnLike.setImageResource(
                if (liked) R.drawable.ic_liked else R.drawable.ic_like
            )
            binding.btnLike.contentDescription = binding.root.context.getString(
                if (liked) R.string.unlike else R.string.like
            )
            binding.btnLike.alpha = if (liked) 1f else 0.55f
            binding.btnLike.setOnClickListener {
                onLikeClick.invoke(song)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SongViewHolder {
        return SongViewHolder(
            ItemSongBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Song>() {
            // ID identifies an item; the Song data class determines visual changes.
            override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
                return oldItem == newItem
            }
        }
    }
}
