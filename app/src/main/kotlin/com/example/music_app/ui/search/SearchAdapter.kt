package com.example.music_app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.User
import com.example.music_app.databinding.ItemSearchResultBinding
import com.example.music_app.databinding.ItemSearchSectionHeaderBinding

class SearchAdapter(
    private val onTrackClick: (Song) -> Unit,
    private val onProfileClick: (User) -> Unit,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onRecentQueryClick: (String) -> Unit
) : ListAdapter<SearchResultItem, RecyclerView.ViewHolder>(SearchResultDiffCallback) {

    fun setData(newItems: List<SearchResultItem>) {
        submitList(newItems)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SearchResultItem.Header -> VIEW_TYPE_HEADER
            is SearchResultItem.Track -> VIEW_TYPE_RESULT
            is SearchResultItem.Profile -> VIEW_TYPE_RESULT
            is SearchResultItem.PlaylistItem -> VIEW_TYPE_RESULT
            is SearchResultItem.RecentQuery -> VIEW_TYPE_RESULT
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemSearchSectionHeaderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            HeaderViewHolder(binding)
        } else {
            val binding = ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ResultViewHolder(binding)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val item = getItem(position)) {
            is SearchResultItem.Header -> {
                (holder as HeaderViewHolder).bind(item)
            }

            is SearchResultItem.Track -> {
                (holder as ResultViewHolder).bindTrack(item.song)
            }

            is SearchResultItem.Profile -> {
                (holder as ResultViewHolder).bindProfile(item.user)
            }

            is SearchResultItem.PlaylistItem -> {
                (holder as ResultViewHolder).bindPlaylist(item.playlist)
            }
            is SearchResultItem.RecentQuery -> {
                (holder as ResultViewHolder).bindRecentQuery(item.query)
            }
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemSearchSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResultItem.Header) {
            val context = binding.root.context

            binding.txtSectionTitle.text = context.getString(item.titleResId)
            binding.txtSectionCount.text =
                context.getString(R.string.search_result_count, item.count)
        }
    }

    inner class ResultViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindTrack(song: Song) {
            resetCoverImage()
            binding.txtTitle.text = song.title
            binding.txtArtist.text = song.artist

            Glide.with(binding.root)
                .load(song.coverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgCover)

            binding.root.setOnClickListener {
                onTrackClick(song)
            }
        }

        fun bindProfile(user: User) {
            resetCoverImage()
            val displayName = user.displayName.ifBlank {
                user.email
            }

            binding.txtTitle.text = displayName
            binding.txtArtist.text = binding.root.context.getString(
                R.string.user_search_subtitle,
                user.uploadedSongsCount
            )

            Glide.with(binding.root)
                .load(user.avatarUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgCover)

            binding.root.setOnClickListener {
                onProfileClick(user)
            }
        }

        fun bindPlaylist(playlist: Playlist) {
            resetCoverImage()
            val context = binding.root.context

            binding.txtTitle.text = playlist.name
            binding.txtArtist.text =
                context.getString(R.string.playlist_songs_count, playlist.songsCount)

            Glide.with(binding.root)
                .load(playlist.coverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgCover)

            binding.root.setOnClickListener {
                onPlaylistClick(playlist)
            }
        }
        fun bindRecentQuery(query: String) {
            val context = binding.root.context

            binding.txtTitle.text = query
            binding.txtArtist.text = context.getString(R.string.tap_to_search_again)

            val padding = (18 * context.resources.displayMetrics.density).toInt()

            binding.imgCover.setImageResource(R.drawable.ic_search)
            binding.imgCover.scaleType = android.widget.ImageView.ScaleType.CENTER
            binding.imgCover.setPadding(padding, padding, padding, padding)

            binding.root.setOnClickListener {
                onRecentQueryClick(query)
            }
        }

        private fun resetCoverImage() {
            binding.imgCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.imgCover.setPadding(0, 0, 0, 0)
        }

    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_RESULT = 2

        private val SearchResultDiffCallback = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(
                oldItem: SearchResultItem,
                newItem: SearchResultItem
            ): Boolean {
                return when {
                    oldItem is SearchResultItem.Header && newItem is SearchResultItem.Header ->
                        oldItem.titleResId == newItem.titleResId
                    oldItem is SearchResultItem.Track && newItem is SearchResultItem.Track ->
                        oldItem.song.id == newItem.song.id
                    oldItem is SearchResultItem.Profile && newItem is SearchResultItem.Profile ->
                        oldItem.user.uid == newItem.user.uid
                    oldItem is SearchResultItem.PlaylistItem && newItem is SearchResultItem.PlaylistItem ->
                        oldItem.playlist.id == newItem.playlist.id
                    oldItem is SearchResultItem.RecentQuery && newItem is SearchResultItem.RecentQuery ->
                        oldItem.query == newItem.query
                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: SearchResultItem,
                newItem: SearchResultItem
            ): Boolean = oldItem == newItem
        }
    }
}
