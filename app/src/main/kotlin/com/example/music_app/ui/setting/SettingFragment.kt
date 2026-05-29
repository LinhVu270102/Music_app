package com.example.music_app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.media3.common.util.UnstableApi
import com.example.music_app.R
import com.example.music_app.databinding.FragmentSettingBinding
import com.example.music_app.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.music_app.player.PlayerManager
import com.example.music_app.service.MusicService

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        observeViewModel()
        setupListeners()
    }

    private fun observeViewModel() {
        viewModel.selectedLanguage.observe(viewLifecycleOwner) {
        }
    }

    private fun setupListeners() {
        binding.btnReturn.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.languageSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedLang = parent?.getItemAtPosition(position).toString()
                    viewModel.setLanguage(selectedLang)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.clearDataButton.setOnClickListener {
            viewModel.clearData()
            showToast("Dữ liệu đã được xoá")
        }

        binding.accountHeader.setOnClickListener {
            toggleAccountOptions()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }

        binding.deleteAccountButton.setOnClickListener {
            viewModel.deleteAccount()
            showToast("Tài khoản đã bị xoá")
        }
    }

    private fun toggleAccountOptions() {
        val isVisible = binding.accountOptions.visibility == View.VISIBLE

        binding.accountOptions.visibility = if (isVisible) {
            View.GONE
        } else {
            View.VISIBLE
        }

        val icon = if (isVisible) {
            R.drawable.ic_arrow_right
        } else {
            R.drawable.ic_arrow_down
        }

        binding.accountHeader.setCompoundDrawablesWithIntrinsicBounds(
            0,
            0,
            icon,
            0
        )
    }

    @OptIn(UnstableApi::class)
    private fun logout() {
        FirebaseAuth.getInstance().signOut()

        PlayerManager.release()

        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().stopService(serviceIntent)

        val prefs = requireContext()
            .getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean("isLoggedIn", false)
            .apply()

        showToast("Đăng xuất thành công")

        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}