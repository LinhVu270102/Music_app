package com.example.music_app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
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
    private val onApiArtistProfileClick: (SearchResultItem.ApiArtistProfile) -> Unit,
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SearchResultItem>()

    fun setData(newItems: List<SearchResultItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchResultItem.Header -> VIEW_TYPE_HEADER
            is SearchResultItem.Track -> VIEW_TYPE_RESULT
            is SearchResultItem.Profile -> VIEW_TYPE_RESULT
            is SearchResultItem.ApiArtistProfile -> VIEW_TYPE_RESULT
            is SearchResultItem.PlaylistItem -> VIEW_TYPE_RESULT
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
        when (val item = items[position]) {
            is SearchResultItem.Header -> {
                (holder as HeaderViewHolder).bind(item)
            }

            is SearchResultItem.Track -> {
                (holder as ResultViewHolder).bindTrack(item.song)
            }

            is SearchResultItem.Profile -> {
                (holder as ResultViewHolder).bindProfile(item.user)
            }

            is SearchResultItem.ApiArtistProfile -> {
                (holder as ResultViewHolder).bindApiArtistProfile(item)
            }

            is SearchResultItem.PlaylistItem -> {
                (holder as ResultViewHolder).bindPlaylist(item.playlist)
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
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
            val displayName = user.displayName.ifBlank {
                user.email
            }

            binding.txtTitle.text = displayName
            binding.txtArtist.text =
                user.username.ifBlank {
                    user.email
                }

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
        fun bindApiArtistProfile(profile: SearchResultItem.ApiArtistProfile) {
            val context = binding.root.context

            binding.txtTitle.text =
                profile.artistName.ifBlank {
                    context.getString(R.string.unknown_artist)
                }

            binding.txtArtist.text =
                context.getString(
                    R.string.api_artist_search_subtitle,
                    getSourceLabel(profile.source),
                    profile.trackCount
                )

            Glide.with(binding.root)
                .load(profile.avatarUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgCover)

            binding.root.setOnClickListener {
                onApiArtistProfileClick(profile)
            }
        }

        private fun getSourceLabel(source: String): String {
            val context = binding.root.context

            return if (source.equals("soundcloud", ignoreCase = true)) {
                context.getString(R.string.soundcloud_source)
            } else {
                context.getString(R.string.orange_music_source)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_RESULT = 2
    }
}