package com.example.music_app.ui.player

import android.app.AlertDialog
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
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.databinding.FragmentPlayerBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.comment.CommentFragment
import com.example.music_app.utils.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.example.music_app.databinding.DialogReportSongBinding
import com.example.music_app.databinding.DialogSongOptionsBinding

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()
    private val songRepository = SongRepository()

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

            handler.postDelayed(this, 500)
        }
    }

    companion object {
        private const val ARG_SONG_ID = "songId"

        private const val MENU_DELETE_SONG = 1
        private const val MENU_TOGGLE_COMMENTS = 2
        private const val MENU_REPORT_SONG = 3

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
            showCurrentPlaylistDialog()
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
        binding.btnSongOptions.setOnClickListener { anchor ->
            showSongOptions(anchor)
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
    }

    private fun updateUI(song: Song) {
        binding.playerSongTitle.text = song.title
        binding.playerArtist.text = song.artist

        binding.tvLikeCount.text = formatCount(song.likes)
        binding.tvCommentCount.text = formatCount(song.commentsCount)

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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val liked = songRepository.isSongLiked(song.id)

                withContext(Dispatchers.Main) {
                    isCurrentSongLiked = liked
                    updateLikeIcon()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isCurrentSongLiked = false
                    updateLikeIcon()
                }
            }
        }
    }

    private fun toggleLikeCurrentSong() {
        val song = PlayerManager.currentSong.value ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val liked = songRepository.toggleLikeSong(song)

                withContext(Dispatchers.Main) {
                    isCurrentSongLiked = liked
                    updateLikeIcon()

                    val currentCount =
                        binding.tvLikeCount.text.toString()
                            .replace("K", "000")
                            .replace("M", "000000")
                            .toLongOrNull() ?: song.likes

                    val newCount = if (liked) {
                        currentCount + 1
                    } else {
                        (currentCount - 1).coerceAtLeast(0)
                    }

                    binding.tvLikeCount.text = formatCount(newCount)

                    showToast(
                        if (liked) {
                            getString(R.string.added_to_your_likes)
                        } else {
                            getString(R.string.removed_from_your_likes)
                        }
                    )
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.update_like_failed))
                }
            }
        }
    }

    private fun updateLikeIcon() {
        binding.btnLike.alpha = if (isCurrentSongLiked) 1f else 0.4f
    }

    private fun loadFollowState(song: Song) {
        binding.tvFollowCount.text = getString(R.string.default_follow_count)

        if (song.uploaderId.isBlank()) {
            isCurrentArtistFollowed = false
            updateFollowIcon()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val followed = songRepository.isFollowing(song.uploaderId)

                withContext(Dispatchers.Main) {
                    isCurrentArtistFollowed = followed
                    updateFollowIcon()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isCurrentArtistFollowed = false
                    updateFollowIcon()
                }
            }
        }
    }

    private fun toggleFollowCurrentArtist() {
        val song = PlayerManager.currentSong.value ?: return

        if (song.uploaderId.isBlank()) {
            showToast(getString(R.string.song_has_no_uploader))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val followed = songRepository.toggleFollowUser(song.uploaderId)

                withContext(Dispatchers.Main) {
                    isCurrentArtistFollowed = followed
                    updateFollowIcon()

                    val currentCount = binding.tvFollowCount.text.toString().toLongOrNull() ?: 0L
                    val newCount = if (followed) {
                        currentCount + 1
                    } else {
                        (currentCount - 1).coerceAtLeast(0)
                    }

                    binding.tvFollowCount.text = formatCount(newCount)

                    showToast(
                        if (followed) {
                            getString(R.string.follow_artist_success)
                        } else {
                            getString(R.string.unfollow_artist_success)
                        }
                    )
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.follow_failed))
                }
            }
        }
    }

    private fun updateFollowIcon() {
        binding.btnFollow.alpha = if (isCurrentArtistFollowed) 1f else 0.4f
    }

    private fun showCurrentPlaylistDialog() {
        val songs = PlayerManager.getCurrentPlaylist()

        if (songs.isEmpty()) {
            showToast(getString(R.string.current_playlist_empty))
            return
        }

        val songTitles = songs.mapIndexed { index, song ->
            "${index + 1}. ${song.title} - ${song.artist}"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.current_playlist))
            .setItems(songTitles) { _, which ->
                val selectedSong = songs[which]
                PlayerManager.playSongAt(which)
                updateUI(selectedSong)
            }
            .setPositiveButton(getString(R.string.add_current_song_to_playlist)) { _, _ ->
                val currentSong = PlayerManager.currentSong.value

                if (currentSong != null) {
                    showAddToPlaylistDialog(currentSong)
                } else {
                    showToast(getString(R.string.no_current_song))
                }
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val playlists = songRepository.getMyPlaylists()

                withContext(Dispatchers.Main) {
                    if (playlists.isEmpty()) {
                        showToast(getString(R.string.no_playlists))
                        return@withContext
                    }

                    val playlistNames = playlists.map { it.name }.toTypedArray()

                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.add_to_playlist))
                        .setItems(playlistNames) { _, which ->
                            val selectedPlaylist = playlists[which]
                            addSongToSelectedPlaylist(selectedPlaylist.id, song)
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.load_playlists_failed))
                }
            }
        }
    }

    private fun addSongToSelectedPlaylist(
        playlistId: String,
        song: Song
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                songRepository.addSongToPlaylist(
                    playlistId = playlistId,
                    song = song
                )

                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.added_to_playlist_success))
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.add_song_to_playlist_failed))
                }
            }
        }
    }

    private fun showSongOptions(anchor: View) {
        val song = PlayerManager.currentSong.value ?: return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val isOwner = song.uploaderId.isNotBlank() && song.uploaderId == currentUserId

        val dialogBinding = DialogSongOptionsBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.txtOptionsSongTitle.text = song.title
        dialogBinding.txtOptionsSongArtist.text = song.artist

        dialogBinding.actionDeleteSong.visibility =
            if (isOwner) View.VISIBLE else View.GONE

        dialogBinding.actionToggleComments.visibility =
            if (isOwner) View.VISIBLE else View.GONE

        dialogBinding.txtToggleCommentsAction.text =
            if (song.allowComments) {
                getString(R.string.lock_comments)
            } else {
                getString(R.string.unlock_comments)
            }

        dialogBinding.actionReportSong.setOnClickListener {
            dialog.dismiss()
            showReportSongDialog(song)
        }

        dialogBinding.actionToggleComments.setOnClickListener {
            dialog.dismiss()
            toggleCurrentSongComments(song)
        }

        dialogBinding.actionDeleteSong.setOnClickListener {
            dialog.dismiss()
            confirmDeleteCurrentSong(song)
        }

        dialog.show()
    }

    private fun showReportSongDialog(song: Song) {
        val dialogBinding = DialogReportSongBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.txtReportSongName.text =
            getString(
                R.string.report_song_name_format,
                song.title,
                song.artist
            )

        dialogBinding.radioSpam.isChecked = true

        dialogBinding.btnCancelReport.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitReport.setOnClickListener {
            val reason = when (dialogBinding.radioReportReasons.checkedRadioButtonId) {
                R.id.radioSpam -> getString(R.string.report_reason_spam)
                R.id.radioOffensive -> getString(R.string.report_reason_offensive)
                R.id.radioCopyright -> getString(R.string.report_reason_copyright)
                R.id.radioOther -> getString(R.string.report_reason_other)
                else -> ""
            }

            if (reason.isBlank()) {
                showToast(getString(R.string.report_reason_empty))
                return@setOnClickListener
            }

            val description = dialogBinding.edtReportDescription.text
                ?.toString()
                ?.trim()
                .orEmpty()

            dialog.dismiss()

            reportCurrentSong(
                song = song,
                reason = reason,
                description = description
            )
        }

        dialog.show()
    }

    private fun confirmDeleteCurrentSong(song: Song) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_song))
            .setMessage(getString(R.string.delete_song_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteCurrentSong(song)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteCurrentSong(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                songRepository.softDeleteMySong(song.id)

                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.delete_song_success))
                    PlayerManager.stop()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.delete_song_failed))
                }
            }
        }
    }

    private fun toggleCurrentSongComments(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                songRepository.updateMySongCommentPermission(
                    songId = song.id,
                    allowComments = !song.allowComments
                )

                withContext(Dispatchers.Main) {
                    val messageResId =
                        if (song.allowComments) {
                            R.string.comments_locked_success
                        } else {
                            R.string.comments_unlocked_success
                        }

                    showToast(getString(messageResId))
                    viewModel.loadSong(song.id)
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.update_comment_permission_failed))
                }
            }
        }
    }


    private fun reportCurrentSong(
        song: Song,
        reason: String,
        description: String = ""
    ) {
        if (reason.isBlank()) {
            showToast(getString(R.string.report_reason_empty))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                songRepository.reportSong(
                    songId = song.id,
                    reason = reason,
                    description = description
                )

                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.report_song_success))
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.report_song_failed))
                }
            }
        }
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
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 1_000 -> "${count / 1_000}K"
            else -> count.toString()
        }
    }
}