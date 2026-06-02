package com.example.music_app.ui.auth

import android.content.Intent
import androidx.activity.viewModels
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
            val email = binding.regEmail.text.toString().trim()
            val password = binding.regPassword.text.toString()
            val confirmPassword = binding.regConfirmPassword.text.toString()

            if (displayName.isBlank()) {
                showToast("Vui lòng nhập tên hiển thị")
                return@setOnClickListener
            }

            if (email.isBlank()) {
                showToast("Vui lòng nhập email")
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast("Email không hợp lệ")
                return@setOnClickListener
            }

            if (password.length < 6) {
                showToast("Mật khẩu phải có ít nhất 6 ký tự")
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showToast("Mật khẩu không khớp")
                return@setOnClickListener
            }

            viewModel.register(
                displayName = displayName,
                email = email,
                password = password
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
                showToast("Đăng ký thành công!")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { msg ->
            msg?.let { showToast(it) }
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