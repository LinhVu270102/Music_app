package com.example.music_app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.example.music_app.R
import com.example.music_app.data.model.AdminDashboardStats
import com.example.music_app.databinding.FragmentAdminDashboardBinding
import com.example.music_app.main.MainActivity
import com.example.music_app.ui.auth.LoginActivity
import com.example.music_app.ui.home.HomeFragment
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminDashboardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAdminDashboardBinding.bind(view)

        setupListeners()
        setupCommunityRules()
        observeViewModel()

        viewModel.loadDashboard()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            logout()
        }

        binding.btnOpenModeration.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AdminModerationFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnOpenReports.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AdminReportFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observeViewModel() {
        viewModel.isAdmin.observe(viewLifecycleOwner) { isAdmin ->
            if (!isAdmin) {
                openHomeForNonAdmin()
            }
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            renderStats(stats)
            renderNotification(stats)
        }

        viewModel.errorMessageResId.observe(viewLifecycleOwner) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun renderStats(stats: AdminDashboardStats) {
        binding.txtPendingValue.text = stats.pendingSongs.toString()
        binding.txtApprovedValue.text = stats.approvedSongs.toString()
        binding.txtRejectedValue.text = stats.rejectedSongs.toString()
        binding.txtHiddenValue.text = stats.hiddenSongs.toString()
    }

    private fun renderNotification(stats: AdminDashboardStats) {
        val hasNotification =
            stats.pendingSongs > 0 ||
                    stats.pendingReports > 0 ||
                    stats.reportedSongs > 0

        binding.txtAdminNotificationMessage.text =
            if (hasNotification) {
                getString(
                    R.string.admin_notification_summary,
                    stats.pendingSongs,
                    stats.pendingReports,
                    stats.reportedSongs
                )
            } else {
                getString(R.string.no_admin_notifications)
            }
    }

    private fun openHomeForNonAdmin() {
        parentFragmentManager.commit {
            replace(R.id.fragmentContainer, HomeFragment())
        }

        (requireActivity() as? MainActivity)?.updateMainChromeVisibility()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        requireActivity().finish()
    }

    private fun setupCommunityRules() {
        binding.ruleOffensiveContent.txtRuleTitle.text =
            getString(R.string.rule_offensive_content_title)
        binding.ruleOffensiveContent.txtRuleDescription.text =
            getString(R.string.rule_offensive_content_description)
        binding.ruleOffensiveContent.txtRuleIcon.text = "1"

        binding.ruleViolence.txtRuleTitle.text =
            getString(R.string.rule_violence_title)
        binding.ruleViolence.txtRuleDescription.text =
            getString(R.string.rule_violence_description)
        binding.ruleViolence.txtRuleIcon.text = "2"

        binding.ruleCopyright.txtRuleTitle.text =
            getString(R.string.rule_copyright_title)
        binding.ruleCopyright.txtRuleDescription.text =
            getString(R.string.rule_copyright_description)
        binding.ruleCopyright.txtRuleIcon.text = "3"

        binding.ruleSpam.txtRuleTitle.text =
            getString(R.string.rule_spam_title)
        binding.ruleSpam.txtRuleDescription.text =
            getString(R.string.rule_spam_description)
        binding.ruleSpam.txtRuleIcon.text = "4"

        binding.ruleInappropriateImage.txtRuleTitle.text =
            getString(R.string.rule_inappropriate_image_title)
        binding.ruleInappropriateImage.txtRuleDescription.text =
            getString(R.string.rule_inappropriate_image_description)
        binding.ruleInappropriateImage.txtRuleIcon.text = "5"
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}