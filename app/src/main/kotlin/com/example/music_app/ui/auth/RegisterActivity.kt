package com.example.music_app.ui.auth

import android.content.Intent
import androidx.activity.viewModels
import com.example.music_app.databinding.ActivityRegisterBinding
import com.example.music_app.base.BaseActivity
import com.example.music_app.main.MainActivity
import com.example.music_app.ui.auth.AuthViewModel

class RegisterActivity : BaseActivity<ActivityRegisterBinding>() {

    private val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(): ActivityRegisterBinding {
        return ActivityRegisterBinding.inflate(layoutInflater)
    }

    override fun initListeners() {
        // Nút đăng ký
        binding.registerButton.setOnClickListener {
            val email = binding.regEmail.text.toString()
            val password = binding.regPassword.text.toString()
            val confirmPassword = binding.regConfirmPassword.text.toString()

            if (password != confirmPassword) {
                showToast("Mật khẩu không khớp")
                return@setOnClickListener
            }

            viewModel.register(email, password)   // sửa lại đúng tên hàm
        }
    }

    override fun initObservers() {
        // Quan sát kết quả đăng ký
        viewModel.authSuccess.observe(this) { success ->   // sửa lại đúng tên LiveData
            if (success) {
                showToast("Đăng ký thành công!")
                startActivity(Intent(this, MainActivity::class.java))
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
        binding.btnReturn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
}
