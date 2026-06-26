package com.example.music_app.ui.player

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.data.model.Song
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.DialogReportSongBinding
import com.example.music_app.databinding.DialogSongOptionsBinding
import com.example.music_app.ui.common.showCustomDialog

/** Owns song-option dialogs while PlayerViewModel performs the selected action. */
class PlayerSongOptionsDialogController(
    private val fragment: Fragment,
    private val viewModel: PlayerViewModel,
    private val canManageSong: (Song) -> Boolean,
    private val showMessage: (String) -> Unit
) {

    fun show(song: Song) {
        val binding = DialogSongOptionsBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtOptionsSongTitle.text = song.title
        binding.txtOptionsSongArtist.text = song.artist

        val isOwner = canManageSong(song)
        binding.actionDeleteSong.visibility = if (isOwner) View.VISIBLE else View.GONE
        binding.actionToggleComments.visibility = if (isOwner) View.VISIBLE else View.GONE
        binding.txtToggleCommentsAction.text = fragment.getString(
            if (song.allowComments) R.string.lock_comments else R.string.unlock_comments
        )

        binding.actionReportSong.setOnClickListener {
            dialog.dismiss()
            showReportDialog(song)
        }
        binding.actionToggleComments.setOnClickListener {
            dialog.dismiss()
            viewModel.toggleSongComments(song)
        }
        binding.actionDeleteSong.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(song)
        }

        dialog.showCustomDialog()
    }

    private fun showReportDialog(song: Song) {
        val binding = DialogReportSongBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtReportSongName.text = fragment.getString(
            R.string.report_song_name_format,
            song.title,
            song.artist
        )
        binding.radioSpam.isChecked = true
        binding.btnCancelReport.setOnClickListener { dialog.dismiss() }
        binding.btnSubmitReport.setOnClickListener {
            val reason = when (binding.radioReportReasons.checkedRadioButtonId) {
                R.id.radioSpam -> fragment.getString(R.string.report_reason_spam)
                R.id.radioOffensive -> fragment.getString(R.string.report_reason_offensive)
                R.id.radioCopyright -> fragment.getString(R.string.report_reason_copyright)
                R.id.radioOther -> fragment.getString(R.string.report_reason_other)
                else -> ""
            }

            if (reason.isBlank()) {
                showMessage(fragment.getString(R.string.report_reason_empty))
                return@setOnClickListener
            }

            viewModel.reportSong(
                songId = song.id,
                reason = reason,
                description = binding.edtReportDescription.text?.toString()?.trim().orEmpty()
            )
            dialog.dismiss()
        }

        dialog.showCustomDialog()
    }

    private fun showDeleteConfirmation(song: Song) {
        val binding = DialogConfirmActionBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtDialogTitle.text = fragment.getString(R.string.delete_song)
        binding.txtDialogMessage.text = fragment.getString(
            R.string.delete_song_confirm_with_name,
            song.title
        )
        binding.btnConfirm.text = fragment.getString(R.string.delete)
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            viewModel.deleteSong(song.id)
        }

        dialog.showCustomDialog()
    }
}
