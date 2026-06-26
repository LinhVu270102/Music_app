package com.example.music_app.main

import android.view.View
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.ItemNowPlayingBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.home.MiniPlayerPagerAdapter
import kotlin.math.abs

/**
 * Coordinates the two-layer mini-player swipe animation.
 *
 * The primary bar moves out while a non-interactive ghost bar moves in with the
 * next candidate. Playback is changed only after the visual transition finishes.
 */
class MiniPlayerSwipeController(
    private val miniPlayer: ItemNowPlayingBinding,
    private val ghostMiniPlayer: ItemNowPlayingBinding,
    private val ghostPagerAdapter: MiniPlayerPagerAdapter
) {

    private var pendingEnterDirection = 0
    private var pendingTarget: SwipeTarget? = null
    private var isTailAnimating = false

    fun drag(diffX: Float) {
        val miniRoot = miniPlayer.root
        val ghostRoot = ghostMiniPlayer.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return

        val direction = if (diffX < 0) -1 else 1
        if (prepareGhost(direction) == null) {
            miniRoot.translationX = diffX * DRAG_RESISTANCE
            return
        }

        miniRoot.animate().cancel()
        ghostRoot.animate().cancel()
        ghostRoot.visibility = View.VISIBLE

        miniRoot.translationX = diffX
        miniRoot.alpha = (1f - abs(diffX) / width).coerceIn(MIN_ALPHA, 1f)

        ghostRoot.translationX = (-direction * width.toFloat()) + diffX
        ghostRoot.alpha = (abs(diffX) / width).coerceIn(MIN_ALPHA, 1f)
    }

    fun completeSwipe(direction: Int) {
        val miniRoot = miniPlayer.root
        val ghostRoot = ghostMiniPlayer.root
        val width = miniRoot.width.takeIf { it > 0 } ?: return
        val target = prepareGhost(direction)

        if (target == null) {
            reset()
            return
        }

        isTailAnimating = true
        miniRoot.animate().cancel()
        ghostRoot.animate().cancel()
        ghostRoot.visibility = View.VISIBLE

        miniRoot.animate()
            .translationX(direction * width.toFloat())
            .alpha(0f)
            .setDuration(SWIPE_ANIMATION_DURATION_MS)
            .start()

        ghostRoot.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(SWIPE_ANIMATION_DURATION_MS)
            .withEndAction {
                changePlayback(direction, target)
                ghostRoot.postDelayed(::resetIfTransitionDidNotComplete, TRANSITION_TIMEOUT_MS)
            }
            .start()
    }

    /** Called when PlayerManager publishes the newly selected song. */
    fun finishTransitionIfNeeded() {
        if (!isTailAnimating) {
            reset()
            return
        }

        clearPendingTransition()
        miniPlayer.root.animate().cancel()
        ghostMiniPlayer.root.animate().cancel()
        restoreDefaultPositions()
    }

    fun reset() {
        miniPlayer.root.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(RESET_ANIMATION_DURATION_MS)
            .start()

        ghostMiniPlayer.root.animate()
            .alpha(0f)
            .setDuration(RESET_ANIMATION_DURATION_MS)
            .withEndAction(::hideAndRestoreGhost)
            .start()
    }

    private fun prepareGhost(direction: Int): SwipeTarget? {
        pendingTarget?.takeIf { pendingEnterDirection == direction }?.let { return it }

        val target = findTarget(direction) ?: return null
        pendingEnterDirection = direction
        pendingTarget = target

        ghostPagerAdapter.submitList(listOf(target.song)) {
            ghostMiniPlayer.vpMiniPlayer.setCurrentItem(0, false)
        }

        ghostMiniPlayer.btnPlayPause.setImageResource(
            if (PlayerManager.isCurrentlyPlaying()) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play_button_circle
            }
        )
        ghostMiniPlayer.btnLike.alpha = INACTIVE_ACTION_ALPHA
        ghostMiniPlayer.btnFollow.alpha = INACTIVE_ACTION_ALPHA
        ghostMiniPlayer.root.visibility = View.VISIBLE

        return target
    }

    private fun findTarget(direction: Int): SwipeTarget? {
        val song = if (direction < 0) {
            PlayerManager.peekNextSong()
        } else {
            PlayerManager.peekPreviousSong()
        }

        return song?.let { SwipeTarget(song = it, fromPlaylist = true) }
    }

    private fun changePlayback(direction: Int, target: SwipeTarget) {
        if (target.fromPlaylist) {
            if (direction < 0) {
                PlayerManager.playNext()
            } else {
                PlayerManager.playPrevious()
            }
        }
    }

    private fun resetIfTransitionDidNotComplete() {
        if (!isTailAnimating) return

        clearPendingTransition()
        reset()
    }

    private fun clearPendingTransition() {
        isTailAnimating = false
        pendingEnterDirection = 0
        pendingTarget = null
    }

    private fun restoreDefaultPositions() {
        miniPlayer.root.translationX = 0f
        miniPlayer.root.alpha = 1f
        hideAndRestoreGhost()
    }

    private fun hideAndRestoreGhost() {
        ghostMiniPlayer.root.visibility = View.GONE
        ghostMiniPlayer.root.translationX = 0f
        ghostMiniPlayer.root.alpha = 1f
    }

    private data class SwipeTarget(
        val song: Song,
        val fromPlaylist: Boolean
    )

    private companion object {
        const val DRAG_RESISTANCE = 0.25f
        const val MIN_ALPHA = 0.45f
        const val INACTIVE_ACTION_ALPHA = 0.4f
        const val SWIPE_ANIMATION_DURATION_MS = 160L
        const val RESET_ANIMATION_DURATION_MS = 120L
        const val TRANSITION_TIMEOUT_MS = 1_000L
    }
}
