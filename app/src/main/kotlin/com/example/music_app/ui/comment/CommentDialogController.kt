package com.example.music_app.ui.comment

import android.app.AlertDialog
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.data.model.Comment
import com.example.music_app.databinding.DialogCommentOptionsBinding
import com.example.music_app.databinding.DialogConfirmActionBinding
import com.example.music_app.databinding.DialogReportCommentBinding
import com.example.music_app.ui.common.showCustomDialog

/**
 * Renders comment-specific dialogs and delegates the selected action to the caller.
 *
 * Keeping these dialogs here lets [CommentFragment] focus on the comment screen state.
 */
class CommentDialogController(
    private val fragment: Fragment,
    private val currentUserId: () -> String,
    private val songOwnerId: () -> String,
    private val onReportComment: (Comment, String, String) -> Unit,
    private val onHideComment: (Comment) -> Unit,
    private val showMessage: (String) -> Unit
) {

    fun showOptions(comment: Comment) {
        val binding = DialogCommentOptionsBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtCommentPreview.text = comment.content.ifBlank {
            fragment.getString(R.string.no_report_description)
        }
        binding.actionHideComment.visibility = if (canHide(comment)) View.VISIBLE else View.GONE

        binding.actionReportComment.setOnClickListener {
            dialog.dismiss()
            showReportDialog(comment)
        }
        binding.actionHideComment.setOnClickListener {
            dialog.dismiss()
            showHideConfirmation(comment)
        }

        dialog.showCustomDialog()
    }

    private fun canHide(comment: Comment): Boolean {
        val viewerId = currentUserId()
        return comment.userId == viewerId || songOwnerId() == viewerId
    }

    private fun showReportDialog(comment: Comment) {
        val binding = DialogReportCommentBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtReportedCommentPreview.text = fragment.getString(
            R.string.report_comment_content_format,
            comment.displayName.ifBlank { fragment.getString(R.string.unknown_user) },
            comment.content
        )
        binding.radioCommentSpam.isChecked = true
        binding.btnCancelCommentReport.setOnClickListener { dialog.dismiss() }
        binding.btnSubmitCommentReport.setOnClickListener {
            val reason = when (binding.radioCommentReportReasons.checkedRadioButtonId) {
                R.id.radioCommentSpam -> fragment.getString(R.string.report_reason_spam)
                R.id.radioCommentOffensive -> fragment.getString(R.string.report_reason_offensive)
                R.id.radioCommentHarassment -> fragment.getString(R.string.report_reason_harassment)
                R.id.radioCommentOther -> fragment.getString(R.string.report_reason_other)
                else -> ""
            }

            if (reason.isBlank()) {
                showMessage(fragment.getString(R.string.report_reason_empty))
                return@setOnClickListener
            }

            val description = binding.edtCommentReportDescription.text
                ?.toString()
                ?.trim()
                .orEmpty()

            dialog.dismiss()
            onReportComment(comment, reason, description)
        }

        dialog.showCustomDialog()
    }

    private fun showHideConfirmation(comment: Comment) {
        val binding = DialogConfirmActionBinding.inflate(fragment.layoutInflater)
        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(binding.root)
            .create()

        binding.txtDialogTitle.text = fragment.getString(R.string.hide_comment)
        binding.txtDialogMessage.text = fragment.getString(R.string.hide_comment_confirm)
        binding.btnConfirm.text = fragment.getString(R.string.hide)
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            onHideComment(comment)
        }

        dialog.showCustomDialog()
    }
}
