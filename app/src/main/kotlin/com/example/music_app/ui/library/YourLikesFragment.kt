package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.FragmentYourLikesBinding

class YourLikesFragment : Fragment(R.layout.fragment_your_likes) {

    private var _binding: FragmentYourLikesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentYourLikesBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}