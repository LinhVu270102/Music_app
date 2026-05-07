package com.example.music_app.base

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    abstract fun getViewBinding(): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = getViewBinding()
        setContentView(binding.root)

        initView()
        initListeners()
        initObservers()
    }

    open fun initView() {}

    open fun initListeners() {}

    open fun initObservers() {}

    protected fun showLoading(view: View) {
        view.visibility = View.VISIBLE
    }

    protected fun hideLoading(view: View) {
        view.visibility = View.GONE
    }

    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}