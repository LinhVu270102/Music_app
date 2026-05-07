package com.example.music_app.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.music_app.R
import com.example.music_app.databinding.FragmentLibraryBinding
import com.example.music_app.ui.settings.SettingFragment

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnSetting.setOnClickListener {
            viewModel.onSettingClicked()
        }

        binding.btnYourLikes.setOnClickListener {
            showToast("Bạn chọn Your Likes")
        }

        binding.btnPlaylists.setOnClickListener {
            showToast("Bạn chọn Playlists")
        }

        binding.btnAlbums.setOnClickListener {
            showToast("Bạn chọn Albums")
        }

        binding.btnFollowing.setOnClickListener {
            showToast("Bạn chọn Following")
        }

        binding.btnYourUpload.setOnClickListener {
            showToast("Bạn chọn Your Upload")
        }
    }

    private fun observeViewModel() {
        viewModel.navigateEvent.observe(viewLifecycleOwner) { event ->
            if (event == "setting") {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, SettingFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}