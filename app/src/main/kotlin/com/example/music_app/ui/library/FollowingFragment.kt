package com.example.music_app.ui.library

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.music_app.R
import com.example.music_app.databinding.FragmentFollowingBinding

class FollowingFragment : Fragment(R.layout.fragment_following) {

    private var _binding: FragmentFollowingBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentFollowingBinding.bind(view)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}