package com.example.music_app.ui.yourupload

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.enums.SongStatus
import com.example.music_app.databinding.FragmentYourUploadBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.ui.common.showCustomDialog

class YourUploadFragment : Fragment(R.layout.fragment_your_upload) {

    private var _binding: FragmentYourUploadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: YourUploadViewModel by viewModels()

    private lateinit var adapter: SongAdapter
    private var currentSongs: List<Song> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentYourUploadBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadMyUploadedSongs()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMyUploadedSongs()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onItemClick = { song ->
                openPlayer(song)
            },
            onMoreClick = { song, anchor ->
                showSongOptions(song, anchor)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshYourUpload.setOnRefreshListener {
            viewModel.loadMyUploadedSongs()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            renderSongs(songs)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
                stopRefreshing()
            }
        }

        viewModel.successMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearSuccessMessage()
                stopRefreshing()
            }
        }
    }

    private fun showSongOptions(song: Song, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)

        addSongOptionMenuItems(popup, song)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DELETE -> {
                    confirmDeleteSong(song)
                    true
                }

                MENU_TOGGLE_COMMENTS -> {
                    viewModel.toggleComments(song)
                    true
                }

                MENU_RESUBMIT -> {
                    viewModel.resubmitSong(song)
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    private fun addSongOptionMenuItems(
        popup: PopupMenu,
        song: Song
    ) {
        popup.menu.add(0, MENU_DELETE, 0, getString(R.string.delete_song))
        popup.menu.add(0, MENU_TOGGLE_COMMENTS, 1, commentMenuTitle(song))

        if (song.statusType == SongStatus.REJECTED) {
            popup.menu.add(0, MENU_RESUBMIT, 2, getString(R.string.resubmit_song))
        }
    }

    private fun commentMenuTitle(song: Song): String {
        return if (song.allowComments) {
            getString(R.string.lock_comments)
        } else {
            getString(R.string.unlock_comments)
        }
    }

    private fun confirmDeleteSong(song: Song) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.txtDialogTitle.text = getString(R.string.delete_song)
        dialogBinding.txtDialogMessage.text =
            getString(R.string.delete_song_confirm_with_name, song.title)
        dialogBinding.btnConfirm.text = getString(R.string.delete)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.deleteSong(song)
        }

        dialog.showCustomDialog()
    }

    private fun renderSongs(songs: List<Song>) {
        currentSongs = songs
        adapter.setData(songs)
        binding.tvEmpty.isVisible = songs.isEmpty()
        stopRefreshing()
    }

    private fun openPlayer(song: Song) {
        PlaybackLauncher.openPlayer(
            fragment = this,
            song = song,
            playlist = currentSongs
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun stopRefreshing() {
        binding.swipeRefreshYourUpload.isRefreshing = false
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MENU_DELETE = 1
        private const val MENU_TOGGLE_COMMENTS = 2
        private const val MENU_RESUBMIT = 3
    }
}
