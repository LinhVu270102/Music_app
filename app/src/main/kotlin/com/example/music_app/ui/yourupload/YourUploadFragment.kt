package com.example.music_app.ui.yourupload

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.data.model.SongStatus
import com.example.music_app.databinding.FragmentYourUploadBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.example.music_app.databinding.DialogConfirmActionBinding

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
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { songs ->
            currentSongs = songs
            adapter.setData(songs)

            binding.tvEmpty.isVisible = songs.isEmpty()
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
        viewModel.actionMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearActionMessage()
            }
        }
    }

    private fun showSongOptions(song: Song, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)

        popup.menu.add(
            0,
            MENU_DELETE,
            0,
            getString(R.string.delete_song)
        )

        popup.menu.add(
            0,
            MENU_TOGGLE_COMMENTS,
            1,
            if (song.allowComments) {
                getString(R.string.lock_comments)
            } else {
                getString(R.string.unlock_comments)
            }
        )

        if (song.status == SongStatus.REJECTED) {
            popup.menu.add(
                0,
                MENU_RESUBMIT,
                2,
                getString(R.string.resubmit_song)
            )
        }

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
    private fun confirmDeleteSong(song: Song) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

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

        dialog.show()
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