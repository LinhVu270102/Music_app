package com.example.music_app.main

import android.view.View
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemNowPlayingBinding
import com.example.music_app.ui.home.MiniPlayerPagerAdapter

/** Owns the mini-player views, pagination, social button state, and swipe wiring. */
class MiniPlayerPresentationController(
    private val miniPlayer: ItemNowPlayingBinding,
    private val ghostMiniPlayer: ItemNowPlayingBinding,
    private val onSongClick: (Song) -> Unit,
    private val onPlayPauseClick: () -> Unit,
    private val onLikeClick: (Song) -> Unit,
    private val onFollowClick: (Song) -> Unit
) {

    private lateinit var miniPlayerPagerAdapter: MiniPlayerPagerAdapter
    private lateinit var swipeController: MiniPlayerSwipeController

    private var currentSong: Song? = null
    private var isCurrentSongLiked = false
    private var isCurrentUploaderFollowed = false
    private var isFollowButtonVisible = true

    fun bind() {
        miniPlayer.root.visibility = View.GONE
        ghostMiniPlayer.root.visibility = View.GONE

        miniPlayerPagerAdapter = MiniPlayerPagerAdapter(
            onSongClick = onSongClick,
            onDrag = { diffX -> swipeController.drag(diffX) },
            onSwipeLeft = { swipeController.completeSwipe(direction = -1) },
            onSwipeRight = { swipeController.completeSwipe(direction = 1) },
            // A lambda defers access until after swipeController is initialized below.
            onCancelSwipe = { swipeController.reset() }
        )

        val ghostPagerAdapter = MiniPlayerPagerAdapter(
            onSongClick = {},
            onDrag = {},
            onSwipeLeft = {},
            onSwipeRight = {},
            onCancelSwipe = {}
        )

        swipeController = MiniPlayerSwipeController(
            miniPlayer = miniPlayer,
            ghostMiniPlayer = ghostMiniPlayer,
            ghostPagerAdapter = ghostPagerAdapter
        )

        miniPlayer.vpMiniPlayer.apply {
            adapter = miniPlayerPagerAdapter
            offscreenPageLimit = 1
            isUserInputEnabled = false
        }
        ghostMiniPlayer.vpMiniPlayer.apply {
            adapter = ghostPagerAdapter
            offscreenPageLimit = 1
            isUserInputEnabled = false
        }
        ghostMiniPlayer.btnPlayPause.isEnabled = false
        ghostMiniPlayer.btnFollow.isEnabled = false
        ghostMiniPlayer.btnLike.isEnabled = false

        miniPlayer.btnPlayPause.setOnClickListener { onPlayPauseClick() }
        miniPlayer.btnLike.setOnClickListener { currentSong?.let(onLikeClick) }
        miniPlayer.btnFollow.setOnClickListener { currentSong?.let(onFollowClick) }

        renderLikeState()
        renderFollowState()
    }

    fun renderPlaylist(songs: List<Song>, currentIndex: Int) {
        miniPlayerPagerAdapter.submitList(songs) {
            renderCurrentPage(currentIndex, smoothScroll = false)
        }
    }

    fun renderCurrentPage(index: Int, smoothScroll: Boolean) {
        if (index !in 0 until miniPlayerPagerAdapter.itemCount) return
        if (miniPlayer.vpMiniPlayer.currentItem == index) return

        miniPlayer.vpMiniPlayer.setCurrentItem(index, smoothScroll)
    }

    fun renderSong(
        song: Song?,
        isLiked: Boolean = false,
        isFollowed: Boolean = false,
        currentIndex: Int = -1
    ) {
        currentSong = song
        isCurrentSongLiked = isLiked
        isCurrentUploaderFollowed = isFollowed

        if (song == null) {
            miniPlayer.root.visibility = View.GONE
            ghostMiniPlayer.root.visibility = View.GONE
            isFollowButtonVisible = false
            renderLikeState()
            renderFollowState()
            swipeController.reset()
            return
        }

        renderLikeState()
        renderCurrentPage(currentIndex, smoothScroll = true)
        miniPlayer.root.post(swipeController::finishTransitionIfNeeded)
    }

    fun renderPlaybackState(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_button_circle
        miniPlayer.btnPlayPause.setImageResource(icon)
        ghostMiniPlayer.btnPlayPause.setImageResource(icon)
    }

    fun renderLikeState(isLiked: Boolean) {
        isCurrentSongLiked = isLiked
        renderLikeState()
    }

    fun renderFollowState(isFollowed: Boolean) {
        isCurrentUploaderFollowed = isFollowed
        renderFollowState()
    }

    fun setFollowButtonVisible(visible: Boolean) {
        isFollowButtonVisible = visible
        if (!visible) isCurrentUploaderFollowed = false
        renderFollowState()
    }

    fun isShowingSong(songId: String): Boolean = currentSong?.id == songId

    fun isShowingUploader(userId: String): Boolean = currentSong?.uploaderId == userId

    fun setVisible(visible: Boolean) {
        miniPlayer.root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun hideAll() {
        miniPlayer.root.visibility = View.GONE
        ghostMiniPlayer.root.visibility = View.GONE
    }

    private fun renderLikeState() {
        miniPlayer.btnLike.alpha = if (isCurrentSongLiked) ACTIVE_ALPHA else INACTIVE_ALPHA
        val icon = if (isCurrentSongLiked) R.drawable.ic_liked else R.drawable.ic_like
        miniPlayer.btnLike.setImageResource(icon)
        ghostMiniPlayer.btnLike.setImageResource(icon)
    }

    private fun renderFollowState() {
        miniPlayer.btnFollow.visibility = if (isFollowButtonVisible) View.VISIBLE else View.GONE
        miniPlayer.btnFollow.alpha = if (isCurrentUploaderFollowed) ACTIVE_ALPHA else INACTIVE_ALPHA
        val icon = if (isCurrentUploaderFollowed) R.drawable.ic_followed else R.drawable.ic_follow
        miniPlayer.btnFollow.setImageResource(icon)
        ghostMiniPlayer.btnFollow.setImageResource(icon)
    }

    private companion object {
        const val ACTIVE_ALPHA = 1f
        const val INACTIVE_ALPHA = 0.4f
    }
}
