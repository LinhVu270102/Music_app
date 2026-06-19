package com.example.music_app.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.music_app.R
import com.example.music_app.data.model.User
import com.example.music_app.databinding.FragmentEditProfileBinding

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentEditProfileBinding.bind(view)

        setupClickListeners()
        observeViewModel()

        viewModel.loadCurrentUser()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun observeViewModel() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            user?.let {
                bindUser(it)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility =
                if (isLoading) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            binding.btnSaveProfile.isEnabled = !isLoading
        }

        viewModel.updateSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.update_profile_success),
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.clearMessage()
                parentFragmentManager.popBackStack()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.clearMessage()
            }
        }
    }

    private fun bindUser(user: User) {
        binding.edtFullName.setText(user.fullName)
        binding.edtDisplayName.setText(user.displayName)
        binding.edtUsername.setText(user.username)
        binding.edtBio.setText(user.bio)
        binding.edtPhoneNumber.setText(user.phoneNumber)
        binding.edtGender.setText(user.gender)
        binding.edtCountry.setText(user.country)
        binding.edtFavoriteGenres.setText(user.favoriteGenres.joinToString(", "))
        binding.edtMoodTags.setText(user.musicMoodTags.joinToString(", "))
    }

    private fun saveProfile() {
        val fullName = binding.edtFullName.text.toString().trim()
        val displayName = binding.edtDisplayName.text.toString().trim()
        val username = binding.edtUsername.text.toString().trim()
        val bio = binding.edtBio.text.toString().trim()
        val phoneNumber = binding.edtPhoneNumber.text.toString().trim()
        val gender = binding.edtGender.text.toString().trim()
        val country = binding.edtCountry.text.toString().trim()

        val favoriteGenres = binding.edtFavoriteGenres.text
            .toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val moodTags = binding.edtMoodTags.text
            .toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (displayName.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.display_name_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewModel.updateProfile(
            fullName = fullName,
            displayName = displayName,
            username = username,
            bio = bio,
            phoneNumber = phoneNumber,
            gender = gender,
            country = country,
            favoriteGenres = favoriteGenres,
            musicMoodTags = moodTags
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}