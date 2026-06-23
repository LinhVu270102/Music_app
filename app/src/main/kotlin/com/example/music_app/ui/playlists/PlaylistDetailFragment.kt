package com.example.music_app.ui.playlists

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.music_app.R
import com.example.music_app.data.model.Playlist
import com.example.music_app.data.model.Song
import com.example.music_app.data.repository.SoundCloudSocialRepository
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.FragmentPlaylistDetailBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter

class PlaylistDetailFragment : Fragment(R.layout.fragment_playlist_detail) {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlaylistDetailViewModel by viewModels()

    private lateinit var adapter: SongAdapter

    private var playlistId: String = ""
    private var playlistName: String = ""
    private var currentSongs: List<Song> = emptyList()
    private var ownerId: String = ""
    private var coverUrl: String = ""
    private var isCurrentPlaylistLiked = false

    private fun isOwnerPlaylist(): Boolean {
        if (isSoundCloudApiPlaylist()) return false

        return viewModel.isCurrentUserPlaylistOwner(
            ownerId = ownerId,
            isSoundCloudPlaylist = false
        )
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlistId"
        private const val ARG_PLAYLIST_NAME = "playlistName"
        private const val ARG_OWNER_ID = "ownerId"
        private const val ARG_COVER_URL = "coverUrl"

        fun newInstance(
            playlistId: String,
            playlistName: String,
            ownerId: String = "",
            coverUrl: String = ""
        ): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                    putString(ARG_PLAYLIST_NAME, playlistName)
                    putString(ARG_OWNER_ID, ownerId)
                    putString(ARG_COVER_URL, coverUrl)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ownerId = arguments?.getString(ARG_OWNER_ID).orEmpty()
        coverUrl = arguments?.getString(ARG_COVER_URL).orEmpty()
        _binding = FragmentPlaylistDetailBinding.bind(view)

        playlistId = arguments?.getString(ARG_PLAYLIST_ID).orEmpty()
        playlistName = arguments?.getString(ARG_PLAYLIST_NAME).orEmpty()

        binding.tvPlaylistName.text = playlistName.ifBlank { getString(R.string.playlist) }
        binding.tvPlaylistSubtitle.text = if (isSoundCloudApiPlaylist()) {
            getString(R.string.soundcloud_api_playlist_subtitle)
        } else {
            getString(R.string.playlist_detail_subtitle)
        }

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        updatePlaylistActions()

        viewModel.loadPlaylistSongs(
            playlistId = playlistId,
            ownerId = ownerId
        )
    }

    override fun onResume() {
        super.onResume()

        if (playlistId.isNotBlank()) {
            updatePlaylistActions()
            viewModel.loadPlaylistSongs(
                playlistId = playlistId,
                ownerId = ownerId
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song -> openPlayer(song) },
            onItemLongClick = { song ->
                if (isOwnerPlaylist()) {
                    confirmRemoveSong(song)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnPlayAll.setOnClickListener {
            val firstSong = currentSongs.firstOrNull()
            if (firstSong == null) {
                showToast(getString(R.string.no_songs_in_playlist))
            } else {
                openPlayer(firstSong)
            }
        }

        binding.btnAddCurrentSong.setOnClickListener {
            addCurrentSongToPlaylist()
        }

        binding.btnTogglePlaylistLike.setOnClickListener {
            togglePlaylistLike()
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.tvEmpty.isVisible = songs.isEmpty()
            binding.tvSongCount.text =
                getString(R.string.playlist_song_count_format, songs.size)

            val finalCoverUrl = coverUrl.ifBlank {
                songs.firstOrNull()?.coverUrl.orEmpty()
            }

            Glide.with(this)
                .load(finalCoverUrl.ifBlank { R.drawable.music_orange })
                .placeholder(R.drawable.music_orange)
                .error(R.drawable.music_orange)
                .centerCrop()
                .into(binding.imgPlaylistCover)
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

        viewModel.isPlaylistLiked.observe(viewLifecycleOwner) { isLiked ->
            isCurrentPlaylistLiked = isLiked
            updatePlaylistLikeButton()
        }
    }

    private fun updatePlaylistActions() {
        val isOwner = isOwnerPlaylist()
        binding.btnAddCurrentSong.isVisible = isOwner
        binding.btnTogglePlaylistLike.isVisible = !isOwner

        if (!isOwner) {
            viewModel.loadPlaylistLikeState(playlistId)
        }
    }

    private fun togglePlaylistLike() {
        if (isOwnerPlaylist()) return
        viewModel.togglePlaylistLike(currentPlaylist())
    }

    private fun updatePlaylistLikeButton() {
        binding.btnTogglePlaylistLike.text = getString(
            if (isCurrentPlaylistLiked) {
                R.string.unlike_playlist
            } else {
                R.string.like_playlist
            }
        )
    }

    private fun currentPlaylist(): Playlist {
        return Playlist(
            id = playlistId,
            name = playlistName,
            coverUrl = coverUrl.ifBlank { currentSongs.firstOrNull()?.coverUrl.orEmpty() },
            ownerId = ownerId,
            isPublic = true,
            songsCount = currentSongs.size.toLong()
        )
    }

    private fun addCurrentSongToPlaylist() {
        if (!isOwnerPlaylist()) return

        val song = PlayerManager.currentSong.value
        if (song == null) {
            showToast(getString(R.string.no_song_playing))
            return
        }

        viewModel.addCurrentSongToPlaylist(
            playlistId = playlistId,
            song = song,
            ownerId = ownerId
        )
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs
        )
    }

    private fun confirmRemoveSong(song: Song) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialogBinding.txtDialogTitle.text = getString(R.string.remove_from_playlist_title)
        dialogBinding.txtDialogMessage.text =
            getString(R.string.remove_song_from_playlist_confirm, song.title)
        dialogBinding.btnConfirm.text = getString(R.string.delete)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.removeSongFromPlaylist(
                playlistId = playlistId,
                songId = song.id,
                ownerId = ownerId
            )
        }

        dialog.show()
    }

    private fun isSoundCloudApiPlaylist(): Boolean {
        return SoundCloudSocialRepository.isSoundCloudApiPlaylist(
            playlistId = playlistId,
            ownerId = ownerId
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
