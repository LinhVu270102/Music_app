package com.example.music_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(private val songs: List<Song>) :
    RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cover: ImageView = itemView.findViewById(R.id.songCover)
        val title: TextView = itemView.findViewById(R.id.songTitle)
        val artist: TextView = itemView.findViewById(R.id.songArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.cover.setImageResource(song.coverResId)
        holder.title.text = song.title
        holder.artist.text = song.artist
    }

    override fun getItemCount(): Int = songs.size
}
