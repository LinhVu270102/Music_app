package com.example.music_app.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemMiniPlayerPageBinding
import kotlin.math.abs

class MiniPlayerPagerAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onDrag: (Float) -> Unit,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit,
    private val onCancelSwipe: () -> Unit
) : ListAdapter<Song, MiniPlayerPagerAdapter.MiniPlayerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MiniPlayerViewHolder {
        val binding = ItemMiniPlayerPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return MiniPlayerViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MiniPlayerViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class MiniPlayerViewHolder(
        private val binding: ItemMiniPlayerPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var downX = 0f
        private var downY = 0f
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility")
        fun bind(song: Song) {
            binding.txtTitle.text = song.title
            binding.txtArtist.text = song.artist

            binding.root.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        isDragging = false

                        view.parent.requestDisallowInterceptTouchEvent(true)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val diffX = event.rawX - downX
                        val diffY = event.rawY - downY

                        if (abs(diffX) > abs(diffY)) {
                            isDragging = true
                            onDrag(diffX)
                        }

                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val diffX = event.rawX - downX
                        val diffY = event.rawY - downY

                        view.parent.requestDisallowInterceptTouchEvent(false)

                        val isHorizontalSwipe = abs(diffX) > abs(diffY)
                        val isSwipeEnough = abs(diffX) > SWIPE_THRESHOLD

                        if (isDragging && isHorizontalSwipe && isSwipeEnough) {
                            if (diffX < 0) {
                                onSwipeLeft()
                            } else {
                                onSwipeRight()
                            }
                        } else {
                            onCancelSwipe()

                            if (!isDragging || abs(diffX) < TAP_THRESHOLD) {
                                view.performClick()
                                onSongClick(song)
                            }
                        }

                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        view.parent.requestDisallowInterceptTouchEvent(false)
                        onCancelSwipe()
                        true
                    }

                    else -> true
                }
            }
        }
    }

    companion object {
        private const val SWIPE_THRESHOLD = 120f
        private const val TAP_THRESHOLD = 20f

        private val DiffCallback = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(
                oldItem: Song,
                newItem: Song
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: Song,
                newItem: Song
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}