package com.example.music_app.ui.admin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.FragmentAdminDashboardBinding
import android.content.Intent
import com.example.music_app.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAdminDashboardBinding.bind(view)

        setupListeners()
        setupCommunityRules()
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        requireActivity().finish()
    }
    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            logout()
        }

        binding.btnOpenModeration.setOnClickListener {
            // Tạm thời chưa có AdminFragment kiểm duyệt
            // Sau này sẽ mở AdminFragment tại đây
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}