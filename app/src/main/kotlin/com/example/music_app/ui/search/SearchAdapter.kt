package com.example.music_app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
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
            bindSearchResult(
                title = song.title,
                subtitle = song.artist,
                coverUrl = song.coverUrl
            ) {
                onTrackClick(song)
            }
        }

        fun bindProfile(user: User) {
            val displayName = user.displayName.ifBlank {
                user.email
            }

            bindSearchResult(
                title = displayName,
                subtitle = binding.root.context.getString(
                    R.string.user_search_subtitle,
                    user.uploadedSongsCount
                ),
                coverUrl = user.avatarUrl
            ) {
                onProfileClick(user)
            }
        }

        fun bindPlaylist(playlist: Playlist) {
            val context = binding.root.context

            bindSearchResult(
                title = playlist.name,
                subtitle = context.getString(R.string.playlist_songs_count, playlist.songsCount),
                coverUrl = playlist.coverUrl
            ) {
                onPlaylistClick(playlist)
            }
        }

        fun bindRecentQuery(query: String) {
            val context = binding.root.context

            bindTitleSubtitle(
                title = query,
                subtitle = context.getString(R.string.tap_to_search_again)
            )

            val padding = RECENT_QUERY_ICON_PADDING_DP.toPx()

            binding.imgCover.setImageResource(R.drawable.ic_search)
            binding.imgCover.scaleType = ImageView.ScaleType.CENTER
            binding.imgCover.setPadding(padding, padding, padding, padding)

            bindClick {
                onRecentQueryClick(query)
            }
        }

        private fun bindSearchResult(
            title: String,
            subtitle: String,
            coverUrl: String,
            onClick: () -> Unit
        ) {
            resetCoverImage()
            bindTitleSubtitle(title, subtitle)
            loadCover(coverUrl)
            bindClick(onClick)
        }

        private fun bindTitleSubtitle(title: String, subtitle: String) {
            binding.txtTitle.text = title
            binding.txtArtist.text = subtitle
        }

        private fun loadCover(coverUrl: String) {
            Glide.with(binding.root)
                .load(coverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgCover)
        }

        private fun bindClick(onClick: () -> Unit) {
            binding.root.setOnClickListener {
                onClick()
            }
        }

        private fun resetCoverImage() {
            binding.imgCover.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.imgCover.setPadding(0, 0, 0, 0)
        }

        private fun Int.toPx(): Int {
            return (this * binding.root.context.resources.displayMetrics.density).toInt()
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_RESULT = 2
        private const val RECENT_QUERY_ICON_PADDING_DP = 18

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
