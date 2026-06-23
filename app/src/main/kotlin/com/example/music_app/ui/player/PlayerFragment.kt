package com.example.music_app.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.databinding.FragmentPlayerBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.comment.CommentFragment
import java.util.Locale
import java.util.concurrent.TimeUnit


class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()
    private val playlistDialogs by lazy {
        PlayerPlaylistDialogController(
            fragment = this,
            viewModel = viewModel,
            showMessage = ::showToast
        )
    }
    private val songOptionsDialogs by lazy {
        PlayerSongOptionsDialogController(
            fragment = this,
            viewModel = viewModel,
            canManageSong = viewModel::isCurrentUserSongOwner,
            showMessage = ::showToast
        )
    }
    private val handler = Handler(Looper.getMainLooper())

    private var isCurrentSongLiked = false
    private var isCurrentArtistFollowed = false

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            val position = PlayerManager.getCurrentPosition()
            val duration = PlayerManager.getDuration()

            binding.playerSeekBar.max = duration.toInt()
            binding.playerSeekBar.progress = position.toInt()

            binding.playerCurrentTime.text = formatTime(position)
            binding.playerTotalTime.text = formatTime(duration)

            updatePlayPauseIcon()

            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val ARG_SONG_ID = "songId"

        fun newInstance(songId: String): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SONG_ID, songId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as? MainActivity)?.setMiniPlayerVisible(false)
        (requireActivity() as? MainActivity)?.setFooterVisible(false)

        setupActions()
        initObservers()

        updateShuffleIcon()
        updateLoopIcon()

        PlayerManager.currentSong.value?.let {
            updateUI(it)
            startProgressUpdater()
            return
        }

        val songId = arguments?.getString(ARG_SONG_ID)
        songId?.let {
            viewModel.loadSong(it)
        }
    }

    private fun setupActions() {

        binding.btnPlayPause.setOnClickListener {
            PlayerManager.toggle()
            updatePlayPauseIcon()
        }

        binding.btnReturn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnShuffle.setOnClickListener {
            PlayerManager.toggleShuffle()
        }

        binding.btnLoop.setOnClickListener {
            PlayerManager.toggleLoopMode()
        }

        binding.btnNext.setOnClickListener {
            PlayerManager.playNext()
            updatePlayPauseIcon()
        }

        binding.btnPrev.setOnClickListener {
            PlayerManager.playPrevious()
            updatePlayPauseIcon()
        }

        binding.btnLike.setOnClickListener {
            toggleLikeCurrentSong()
        }

        binding.btnFollow.setOnClickListener {
            toggleFollowCurrentArtist()
        }

        binding.btnComment.setOnClickListener {
            val song = PlayerManager.currentSong.value

            if (song == null) {
                showToast(getString(R.string.no_current_song))
            } else {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, CommentFragment.newInstance(song.id))
                    .addToBackStack(null)
                    .commit()

                (requireActivity() as? MainActivity)?.updateMainChromeVisibility()
            }
        }

        binding.btnCurrentPlaylist.setOnClickListener {
            playlistDialogs.showCurrentPlaylist()
        }

        binding.playerSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        PlayerManager.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
        binding.btnSongOptions.setOnClickListener {
            PlayerManager.currentSong.value?.let(songOptionsDialogs::show)
        }
        binding.btnCurrentPlaylist.setOnClickListener {
            playlistDialogs.showCurrentPlaylist()
        }
    }

    private fun initObservers() {
        viewModel.song.observe(viewLifecycleOwner) { song ->
            song?.let {
                updateUI(it)
                startProgressUpdater()
            }
        }

        PlayerManager.currentSong.observe(viewLifecycleOwner) { song ->
            song?.let {
                updateUI(it)
                startProgressUpdater()
            }
        }

        PlayerManager.isPlaying.observe(viewLifecycleOwner) {
            updatePlayPauseIcon()
        }

        PlayerManager.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                PlayerManager.clearErrorMessage()
            }
        }

        PlayerManager.isShuffleEnabled.observe(viewLifecycleOwner) {
            updateShuffleIcon()
        }

        PlayerManager.loopMode.observe(viewLifecycleOwner) {
            updateLoopIcon()
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }

        viewModel.successMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearSuccessMessage()
            }
        }

        viewModel.songDeleted.observe(viewLifecycleOwner) { deleted ->
            if (!deleted) return@observe

            showToast(getString(R.string.delete_song_success))
            PlayerManager.stop()
            parentFragmentManager.popBackStack()
            viewModel.consumeSongDeleted()
        }

        viewModel.playlistPickerState.observe(viewLifecycleOwner) { state ->
            state ?: return@observe
            playlistDialogs.showPicker(state)
            viewModel.consumePlaylistPickerState()
        }

        PlayerInteractionState.songLikeUpdates.observe(viewLifecycleOwner) { state ->
            val displayedSong = PlayerManager.currentSong.value ?: viewModel.song.value

            if (displayedSong?.id != state.songId) return@observe

            isCurrentSongLiked = state.liked
            state.likesCount?.let { binding.tvLikeCount.text = formatCount(it) }
            state.commentsCount?.let { binding.tvCommentCount.text = formatCount(it) }
            updateLikeIcon()
        }

        PlayerInteractionState.artistFollowUpdates.observe(viewLifecycleOwner) { state ->
            val displayedSong = PlayerManager.currentSong.value ?: viewModel.song.value

            if (displayedSong?.uploaderId != state.userId) return@observe

            isCurrentArtistFollowed = state.followed
            state.followerCount?.let { binding.tvFollowCount.text = formatCount(it) }
            updateFollowIcon()
        }
    }

    private fun updateUI(song: Song) {
        binding.playerSongTitle.text = song.title
        binding.playerArtist.text = song.artist

        val cachedLikeState = PlayerInteractionState.songState(song.id)
        isCurrentSongLiked = cachedLikeState?.liked ?: false
        binding.tvLikeCount.text = formatCount(cachedLikeState?.likesCount ?: song.likes)
        binding.tvCommentCount.text = formatCount(cachedLikeState?.commentsCount ?: song.commentsCount)
        updateLikeIcon()

        loadLikeState(song)
        loadFollowState(song)

        Glide.with(this)
            .asBitmap()
            .load(song.coverUrl)
            .placeholder(R.drawable.music_orange)
            .error(R.drawable.music_orange)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    binding.playerCover.setImageBitmap(resource)

                    Palette.from(resource).generate { palette ->
                        val dominantColor = palette?.getDominantColor(
                            requireContext().getColor(R.color.black)
                        ) ?: requireContext().getColor(R.color.black)

                        applyGradientBackground(dominantColor)
                    }
                }

                override fun onLoadCleared(
                    placeholder: android.graphics.drawable.Drawable?
                ) {
                }
            })

        updatePlayPauseIcon()
    }

    private fun loadLikeState(song: Song) {
        viewModel.loadLikeState(song)
    }

    private fun toggleLikeCurrentSong() {
        val song = PlayerManager.currentSong.value ?: return
        viewModel.toggleLike(song)
    }

    private fun updateLikeIcon() {
        binding.btnLike.setImageResource(
            if (isCurrentSongLiked) R.drawable.ic_liked else R.drawable.ic_like
        )
        binding.btnLike.alpha = if (isCurrentSongLiked) 1f else 0.4f
    }

    private fun loadFollowState(song: Song) {
        val targetUserId = song.uploaderId

        if (targetUserId.isBlank()) {
            isCurrentArtistFollowed = false
            binding.btnFollow.isEnabled = false
            binding.btnFollow.alpha = 0.4f
            binding.tvFollowCount.text = getString(R.string.soundcloud_source)
            updateFollowIcon()
            return
        }

        if (viewModel.isCurrentUser(targetUserId)) {
            isCurrentArtistFollowed = false
            binding.btnFollow.isEnabled = false
            binding.btnFollow.alpha = 0.4f
            binding.tvFollowCount.text = getString(R.string.cannot_follow_yourself)
            updateFollowIcon()
            return
        }

        binding.btnFollow.isEnabled = true
        binding.btnFollow.alpha = 1f

        PlayerInteractionState.artistState(targetUserId)?.let { cachedState ->
            isCurrentArtistFollowed = cachedState.followed
            binding.tvFollowCount.text = formatCount(cachedState.followerCount ?: 0L)
            updateFollowIcon()
        }

        viewModel.loadFollowState(targetUserId)
    }

    private fun toggleFollowCurrentArtist() {
        val song = PlayerManager.currentSong.value ?: viewModel.song.value ?: return
        val targetUserId = song.uploaderId

        if (targetUserId.isBlank()) {
            showToast(getString(R.string.invalid_user))
            return
        }

        if (!binding.btnFollow.isEnabled) return

        viewModel.toggleFollow(targetUserId)
    }

    private fun updateFollowIcon() {
        binding.btnFollow.setImageResource(
            if (isCurrentArtistFollowed) R.drawable.ic_followed else R.drawable.ic_follow
        )
        binding.btnFollow.alpha = if (isCurrentArtistFollowed) 1f else 0.4f
    }

    private fun applyGradientBackground(color: Int) {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                color,
                requireContext().getColor(R.color.black)
            )
        )

        gradient.cornerRadius = 0f
        binding.playerRoot.background = gradient
    }

    private fun updatePlayPauseIcon() {
        val icon = if (PlayerManager.isCurrentlyPlaying()) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }

        binding.btnPlayPause.setImageResource(icon)
    }

    private fun updateShuffleIcon() {
        val enabled = PlayerManager.isShuffleEnabled.value == true
        binding.btnShuffle.alpha = if (enabled) 1f else 0.4f
    }

    private fun updateLoopIcon() {
        when (PlayerManager.loopMode.value) {
            PlayerManager.LoopMode.OFF -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop)
                binding.btnLoop.alpha = 0.4f
            }

            PlayerManager.LoopMode.PLAYLIST -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop)
                binding.btnLoop.alpha = 1f
            }

            PlayerManager.LoopMode.ONE -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop1)
                binding.btnLoop.alpha = 1f
            }

            null -> {
                binding.btnLoop.setImageResource(R.drawable.ic_loop)
                binding.btnLoop.alpha = 0.4f
            }
        }
    }

    private fun startProgressUpdater() {
        handler.removeCallbacks(updateSeekBarRunnable)
        handler.post(updateSeekBarRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateSeekBarRunnable)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateSeekBarRunnable)
        _binding = null
        super.onDestroyView()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 1_000 -> "${count / 1_000}K"
            else -> count.toString()
        }
    }
}
