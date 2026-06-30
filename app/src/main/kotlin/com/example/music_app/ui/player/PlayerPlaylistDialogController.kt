package com.example.music_app.ui.player

import android.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.DialogCurrentPlaylistBinding
import com.example.music_app.databinding.DialogInputActionBinding
import com.example.music_app.databinding.DialogSelectPlaylistBinding
import com.example.music_app.player.PlayerManager
import com.example.music_app.ui.common.showCustomDialog

/** Owns playlist-related dialogs for the full player; data operations remain in PlayerViewModel. */
class PlayerPlaylistDialogController(
    private val fragment: Fragment,
    private val viewModel: PlayerViewModel,
    private val showMessage: (String) -> Unit
) {

    fun showCurrentPlaylist() {
        val songs = PlayerManager.playlistSongs.value.orEmpty()
        val currentIndex = PlayerManager.currentIndex.value ?: -1
        val currentSong = PlayerManager.currentSong.value

        if (songs.isEmpty()) {
            if (currentSong == null) {
                showMessage(fragment.getString(R.string.no_songs_in_playlist))
            } else {
                viewModel.requestPlaylistPicker(currentSong)
            }
            return
        }

        val binding = DialogCurrentPlaylistBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtDialogTitle.text = fragment.getString(R.string.current_playlist)
        binding.txtDialogSubtitle.text = fragment.getString(
            R.string.playlist_song_count_format,
            songs.size
        )

        val adapter = CurrentPlaylistAdapter { index, _ ->
            PlayerManager.playSongAt(index)
            dialog.dismiss()
        }
        binding.rvCurrentPlaylist.layoutManager = LinearLayoutManager(fragment.requireContext())
        binding.rvCurrentPlaylist.adapter = adapter
        adapter.setData(songs, currentIndex)

        if (currentIndex >= 0) {
            binding.rvCurrentPlaylist.scrollToPosition(currentIndex)
        }

        binding.btnAddToPlaylist.setOnClickListener {
            val song = PlayerManager.currentSong.value
            if (song == null) {
                showMessage(fragment.getString(R.string.no_song_playing))
                return@setOnClickListener
            }

            dialog.dismiss()
            viewModel.requestPlaylistPicker(song)
        }
        binding.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.showCustomDialog()
    }

    fun showPicker(state: PlayerPlaylistPickerState) {
        if (state.options.isEmpty()) {
            showCreatePlaylist(state.song)
            return
        }

        val pickerItems = state.options.map { option ->
            PlaylistPickerItem(
                id = option.id,
                name = option.name,
                subtitle = fragment.getString(R.string.playlist_songs_count, option.songsCount)
            )
        }

        showPickerDialog(
            title = fragment.getString(R.string.add_to_playlist),
            subtitle = fragment.getString(R.string.add_current_song_to_playlist),
            items = pickerItems,
            onCreateClick = { showCreatePlaylist(state.song) },
            onPlaylistClick = { item ->
                viewModel.addSongToPlaylist(item.id, state.song)
            }
        )
    }

    private fun showCreatePlaylist(song: Song) {
        val binding = DialogInputActionBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtDialogTitle.text = fragment.getString(R.string.create_playlist)
        binding.txtDialogMessage.text = fragment.getString(R.string.create_playlist_message)
        binding.edtDialogInput.hint = fragment.getString(R.string.playlist_name_hint)
        binding.btnConfirm.text = fragment.getString(R.string.create)
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            val name = binding.edtDialogInput.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                showMessage(fragment.getString(R.string.playlist_name_empty))
                return@setOnClickListener
            }

            viewModel.createPlaylistAndAddSong(name, song)
            dialog.dismiss()
        }

        dialog.showCustomDialog()
    }

    private fun showPickerDialog(
        title: String,
        subtitle: String,
        items: List<PlaylistPickerItem>,
        onCreateClick: () -> Unit,
        onPlaylistClick: (PlaylistPickerItem) -> Unit
    ) {
        val binding = DialogSelectPlaylistBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtDialogTitle.text = title
        binding.txtDialogSubtitle.text = subtitle

        binding.rvPlaylists.layoutManager = LinearLayoutManager(fragment.requireContext())
        binding.rvPlaylists.adapter = PlaylistPickerAdapter { item ->
            dialog.dismiss()
            onPlaylistClick(item)
        }.also { adapter ->
            adapter.setData(items)
        }

        binding.btnCreatePlaylist.setOnClickListener {
            dialog.dismiss()
            onCreateClick()
        }
        binding.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.showCustomDialog()
    }
}
