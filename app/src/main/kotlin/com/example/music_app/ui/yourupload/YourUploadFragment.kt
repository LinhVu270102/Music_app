package com.example.music_app.ui.library

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.FragmentYourUploadBinding
import com.example.music_app.ui.player.PlaybackLauncher
import com.example.music_app.ui.song.SongAdapter

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
                showSongOptions(
                    song = song,
                    anchor = anchor
                )
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
    }

    private fun showSongOptions(
        song: Song,
        anchor: View
    ) {
        val popup = PopupMenu(requireContext(), anchor)

        popup.menu.add(
            0,
            MENU_DELETE,
            0,
            getString(R.string.delete_song)
        )

        val commentTextResId =
            if (song.allowComments) {
                R.string.lock_comments
            } else {
                R.string.unlock_comments
            }

        popup.menu.add(
            0,
            MENU_TOGGLE_COMMENTS,
            1,
            getString(commentTextResId)
        )

        popup.menu.add(
            0,
            MENU_REPORT,
            2,
            getString(R.string.report_song)
        )

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

                MENU_REPORT -> {
                    showReportDialog(song)
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    private fun confirmDeleteSong(song: Song) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_song))
            .setMessage(getString(R.string.delete_song_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteSong(song)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showReportDialog(song: Song) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.enter_report_reason)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 5
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.report_song))
            .setView(input)
            .setPositiveButton(getString(R.string.report)) { _, _ ->
                viewModel.reportSong(
                    song = song,
                    reason = input.text.toString()
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
        private const val MENU_REPORT = 3
    }
}