package com.example.music_app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemAlbumBinding

class AlbumAdapter(
    private var items: List<Song> = emptyList(),
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    inner class AlbumViewHolder(
        val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AlbumViewHolder,
        position: Int
    ) {
        val song = items[position]

        holder.binding.trackTitle.text = song.title
        holder.binding.trackArtist.text = song.artist

        Glide.with(holder.binding.root)
            .load(song.coverUrl)
            .placeholder(R.drawable.music_orange)
            .error(R.drawable.music_orange)
            .dontAnimate()
            .centerCrop()
            .into(holder.binding.albumCover)

        holder.itemView.setOnClickListener {
            onClick(song)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun setData(newItems: List<Song>) {
        val oldIds = items.map { song -> song.id }
        val newIds = newItems.map { song -> song.id }

        if (oldIds == newIds) return

        items = newItems
        notifyDataSetChanged()
    }
}