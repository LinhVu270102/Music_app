package com.example.music_app.ui.auth

import android.content.Intent
import androidx.activity.viewModels
import androidx.core.content.edit
import com.example.music_app.base.BaseActivity
import com.example.music_app.databinding.ActivityLoginBinding
import com.example.music_app.main.MainActivity

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun initListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()

            viewModel.login(email, password)
        }

        binding.signupLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun initObservers() {
        viewModel.authSuccess.observe(this) { success ->
            if (success) {
                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                prefs.edit {
                    putBoolean("isLoggedIn", true)
                }

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
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
}