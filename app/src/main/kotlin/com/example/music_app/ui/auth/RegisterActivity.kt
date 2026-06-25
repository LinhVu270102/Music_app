package com.example.music_app.ui.auth

import android.content.Intent
import android.util.Patterns
import androidx.activity.viewModels
import com.example.music_app.R
import com.example.music_app.base.BaseActivity
import com.example.music_app.databinding.ActivityRegisterBinding
import com.example.music_app.main.MainActivity

class RegisterActivity : BaseActivity<ActivityRegisterBinding>() {

    private val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(): ActivityRegisterBinding {
        return ActivityRegisterBinding.inflate(layoutInflater)
    }

    override fun initListeners() {
        binding.registerButton.setOnClickListener {
            val displayName = binding.regDisplayName.text.toString().trim()
            val fullName = binding.regFullName.text.toString().trim()
            val username = binding.regUsername.text.toString().trim()
            val email = binding.regEmail.text.toString().trim()
            val phoneNumber = binding.regPhoneNumber.text.toString().trim()
            val gender = binding.regGender.text.toString().trim()
            val country = binding.regCountry.text.toString().trim()
            val favoriteGenres = parseCommaSeparatedValues(
                binding.regFavoriteGenres.text.toString()
            )
            val musicMoodTags = parseCommaSeparatedValues(
                binding.regMoodTags.text.toString()
            )
            val password = binding.regPassword.text.toString()
            val confirmPassword = binding.regConfirmPassword.text.toString()

            if (displayName.isBlank()) {
                showToast(getString(R.string.empty_display_name))
                return@setOnClickListener
            }

            if (fullName.isBlank()) {
                showToast(getString(R.string.empty_full_name))
                return@setOnClickListener
            }

            if (username.isBlank()) {
                showToast(getString(R.string.empty_username))
                return@setOnClickListener
            }

            if (email.isBlank()) {
                showToast(getString(R.string.empty_email))
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast(getString(R.string.invalid_email))
                return@setOnClickListener
            }

            if (password.length < 6) {
                showToast(getString(R.string.password_too_short))
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showToast(getString(R.string.passwords_do_not_match))
                return@setOnClickListener
            }

            viewModel.register(
                displayName = displayName,
                fullName = fullName,
                username = username,
                email = email,
                password = password,
                phoneNumber = phoneNumber,
                gender = gender,
                country = country,
                favoriteGenres = favoriteGenres,
                musicMoodTags = musicMoodTags
            )
        }

        binding.btnReturn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun initObservers() {
        viewModel.authSuccess.observe(this) { success ->
            if (success) {
                showToast(getString(R.string.register_success))
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        viewModel.errorMessageResId.observe(this) { messageResId ->
            messageResId?.let {
                showToast(getString(it))
                viewModel.clearErrorMessage()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                showLoading(binding.progressBar)
            } else {
                hideLoading(binding.progressBar)
            }
        }
    }

    private fun parseCommaSeparatedValues(value: String): List<String> {
        return value.split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
    }
}
