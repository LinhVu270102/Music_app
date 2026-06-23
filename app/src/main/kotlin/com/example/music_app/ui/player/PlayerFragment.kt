package com.example.music_app.ui.player

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.PlaylistPickerItem
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SongRepository
import com.example.music_app.data.repository.SoundCloudSocialRepository
import com.example.music_app.player.state.PlayerInteractionState
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.DialogCurrentPlaylistBinding
import com.example.music_app.databinding.DialogInputActionBinding
import com.example.music_app.databinding.DialogReportSongBinding
import com.example.music_app.databinding.DialogSelectPlaylistBinding
import com.example.music_app.databinding.DialogSongOptionsBinding
import com.example.music_app.databinding.FragmentPlayerBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.comment.CommentFragment
import com.example.music_app.utils.AppException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.app.Dialog


class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()
    private val songRepository = SongRepository()
    private val soundCloudSocialRepository = SoundCloudSocialRepository()
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
        binding.btnSongOptions.setOnClickListener {
            showSongOptions()
        }
        binding.btnCurrentPlaylist.setOnClickListener {
            showCurrentPlaylistDialog()
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

    private fun showCurrentPlaylistDialog() {
        val songs = PlayerManager.playlistSongs.value.orEmpty()
        val currentIndex = PlayerManager.currentIndex.value ?: -1
        val currentSong = PlayerManager.currentSong.value

        if (songs.isEmpty()) {
            if (currentSong == null) {
                showToast(getString(R.string.no_songs_in_playlist))
                return
            }

            showAddToPlaylistDialog(currentSong)
            return
        }

        val dialogBinding = DialogCurrentPlaylistBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialogBinding.txtDialogTitle.text = getString(R.string.current_playlist)
        dialogBinding.txtDialogSubtitle.text =
            getString(R.string.playlist_song_count_format, songs.size)

        val currentPlaylistAdapter = CurrentPlaylistAdapter(
            onItemClick = { index, _ ->
                PlayerManager.playSongAt(index)
                dialog.dismiss()
            }
        )

        dialogBinding.rvCurrentPlaylist.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvCurrentPlaylist.adapter = currentPlaylistAdapter

        currentPlaylistAdapter.setData(
            newSongs = songs,
            newCurrentIndex = currentIndex
        )

        if (currentIndex >= 0) {
            dialogBinding.rvCurrentPlaylist.scrollToPosition(currentIndex)
        }

        dialogBinding.btnAddToPlaylist.setOnClickListener {
            val song = PlayerManager.currentSong.value

            if (song == null) {
                showToast(getString(R.string.no_song_playing))
                return@setOnClickListener
            }

            dialog.dismiss()
            showAddToPlaylistDialog(song)
        }

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCreatePlaylistThenAddSong(song: Song) {
        val dialogBinding = DialogInputActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.txtDialogTitle.text = getString(R.string.create_playlist)
        dialogBinding.txtDialogMessage.text = getString(R.string.create_playlist_message)
        dialogBinding.edtDialogInput.hint = getString(R.string.playlist_name_hint)
        dialogBinding.btnConfirm.text = getString(R.string.create)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            val name = dialogBinding.edtDialogInput.text
                ?.toString()
                ?.trim()
                .orEmpty()

            if (name.isBlank()) {
                showToast(getString(R.string.playlist_name_empty))
                return@setOnClickListener
            }

            dialogBinding.btnConfirm.isEnabled = false
            dialogBinding.btnCancel.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val playlist = songRepository.createPlaylist(name)

                    songRepository.addSongToPlaylist(
                        playlistId = playlist.id,
                        song = song
                    )

                    showToast(getString(R.string.added_to_playlist_success))
                    dialog.dismiss()
                } catch (_: Exception) {
                    dialogBinding.btnConfirm.isEnabled = true
                    dialogBinding.btnCancel.isEnabled = true

                    showToast(getString(R.string.add_to_playlist_failed))
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())    }

    private fun showAddToPlaylistDialog(song: Song) {
        if (soundCloudSocialRepository.isSoundCloudSong(song)) {
            showAddToApiPlaylistDialog(song)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val playlists = songRepository.getMyPlaylists()

                withContext(Dispatchers.Main) {
                    if (playlists.isEmpty()) {
                        showCreatePlaylistThenAddSong(song)
                        return@withContext
                    }

                    val pickerItems = playlists.map { playlist ->
                        PlaylistPickerItem(
                            id = playlist.id,
                            name = playlist.name,
                            subtitle = getString(R.string.playlist)
                        )
                    }

                    showPlaylistPickerDialog(
                        title = getString(R.string.add_to_playlist),
                        subtitle = getString(R.string.add_current_song_to_playlist),
                        items = pickerItems,
                        onCreateClick = {
                            showCreatePlaylistThenAddSong(song)
                        },
                        onPlaylistClick = { item: PlaylistPickerItem ->
                            addSongToSelectedPlaylist(
                                playlistId = item.id,
                                song = song
                            )
                        }
                    )
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.load_playlists_failed))
                }
            }
        }
    }
    private fun showPlaylistPickerDialog(
        title: String,
        subtitle: String,
        items: List<PlaylistPickerItem>,
        onCreateClick: () -> Unit,
        onPlaylistClick: (PlaylistPickerItem) -> Unit
    ) {
        val dialogBinding = DialogSelectPlaylistBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.txtDialogTitle.text = title
        dialogBinding.txtDialogSubtitle.text = subtitle

        val pickerAdapter = PlaylistPickerAdapter { item: PlaylistPickerItem ->
            dialog.dismiss()
            onPlaylistClick(item)
        }

        dialogBinding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvPlaylists.adapter = pickerAdapter
        pickerAdapter.setData(items)

        dialogBinding.btnCreatePlaylist.setOnClickListener {
            dialog.dismiss()
            onCreateClick()
        }

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
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
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.add_song_to_playlist_failed))
                }
            }
        }
    }

    private fun showAddToApiPlaylistDialog(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val playlists = soundCloudSocialRepository.getUserApiPlaylists()

                withContext(Dispatchers.Main) {
                    if (playlists.isEmpty()) {
                        showCreateApiPlaylistDialog(song)
                        return@withContext
                    }

                    val pickerItems = playlists.map { playlist ->
                        PlaylistPickerItem(
                            id = playlist.id,
                            name = playlist.name,
                            subtitle = getString(
                                R.string.playlist_songs_count,
                                playlist.songsCount
                            )
                        )
                    }

                    showPlaylistPickerDialog(
                        title = getString(R.string.add_to_playlist),
                        subtitle = getString(R.string.soundcloud_source),
                        items = pickerItems,
                        onCreateClick = {
                            showCreateApiPlaylistDialog(song)
                        },
                        onPlaylistClick = { item: PlaylistPickerItem ->
                            addSoundCloudSongToApiPlaylist(
                                playlistId = item.id,
                                song = song
                            )
                        }
                    )
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.load_playlists_failed))
                }
            }
        }
    }

    private fun showCreateApiPlaylistDialog(song: Song) {
        val dialogBinding = DialogInputActionBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogBinding.root)

        dialogBinding.txtDialogTitle.text = getString(R.string.create_playlist)
        dialogBinding.txtDialogMessage.text = getString(R.string.create_playlist_message)
        dialogBinding.edtDialogInput.hint = getString(R.string.playlist_name_hint)
        dialogBinding.btnConfirm.text = getString(R.string.create)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            val name = dialogBinding.edtDialogInput.text
                ?.toString()
                ?.trim()
                .orEmpty()

            if (name.isBlank()) {
                showToast(getString(R.string.playlist_name_empty))
                return@setOnClickListener
            }

            dialogBinding.btnConfirm.isEnabled = false
            dialogBinding.btnCancel.isEnabled = false

            createApiPlaylistAndAddSong(
                name = name,
                song = song
            )

            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    private fun createApiPlaylistAndAddSong(
        name: String,
        song: Song
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val playlist = soundCloudSocialRepository.createUserApiPlaylist(name)

                soundCloudSocialRepository.addTrackToUserApiPlaylist(
                    playlistId = playlist.id,
                    song = song
                )

                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.added_to_playlist_success))
                }
            } catch (e: AppException) {
                withContext(Dispatchers.Main) {
                    showToast(getString(e.messageResId))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.add_song_to_playlist_failed))
                }
            }
        }
    }

    private fun addSoundCloudSongToApiPlaylist(
        playlistId: String,
        song: Song
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                soundCloudSocialRepository.addTrackToUserApiPlaylist(
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
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.add_song_to_playlist_failed))
                }
            }
        }
    }

    private fun showSongOptions() {
        val song = PlayerManager.currentSong.value ?: return
        val isOwner = viewModel.isCurrentUserSongOwner(song)

        val dialogBinding = DialogSongOptionsBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
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

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
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
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialogBinding.txtDialogTitle.text = getString(R.string.delete_song)
        dialogBinding.txtDialogMessage.text =
            getString(R.string.delete_song_confirm_with_name, song.title)
        dialogBinding.btnConfirm.text = getString(R.string.delete)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            deleteCurrentSong(song)
        }

        dialog.show()
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
