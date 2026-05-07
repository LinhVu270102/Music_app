package com.example.music_app.ui.auth

import android.content.Intent
import androidx.activity.viewModels
import com.example.music_app.databinding.ActivityLoginBinding
import com.example.music_app.base.BaseActivity
import com.example.music_app.main.MainActivity
import com.example.music_app.ui.auth.AuthViewModel

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun initListeners() {
        // Nút đăng nhập
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            viewModel.login(email, password)
        }
        // Link sang RegisterActivity
        binding.signupLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun initObservers() {
        // Quan sát kết quả đăng nhập
        viewModel.authSuccess.observe(this) { success ->
            if (success) {
                // Lưu trạng thái đăng nhập
                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("isLoggedIn", true).apply()

                // Mở MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        // Quan sát lỗi từ BaseViewModel
        viewModel.errorMessage.observe(this) { msg ->
            msg?.let { showToast(it) }
        }

        // Quan sát trạng thái loading từ BaseViewModel
        viewModel.isLoading.observe(this) { loading ->
            if (loading) showLoading(binding.progressBar)
            else hideLoading(binding.progressBar)
        }
    }
}
