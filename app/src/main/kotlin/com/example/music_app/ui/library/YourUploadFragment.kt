package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.FragmentYourUploadBinding

class YourUploadFragment : Fragment(R.layout.fragment_your_upload) {

    private var _binding: FragmentYourUploadBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentYourUploadBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}