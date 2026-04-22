package com.example.music_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // layout login của bạn

        auth = FirebaseAuth.getInstance()

        val emailField = findViewById<EditText>(R.id.emailField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailField.text.toString()
            val password = passwordField.text.toString()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("LOGIN", "Đăng nhập thành công")
                        Toast.makeText(this, "Login OK", Toast.LENGTH_SHORT).show()

                        // Chuyển sang HomeActivity
                        val intent = Intent(this, HomeActivity::class.java)
                        startActivity(intent)
                        finish() // đóng LoginActivity để không quay lại khi bấm back
                    } else {
                        Log.e("LOGIN", "Lỗi: ${task.exception?.message}")
                        Toast.makeText(this, "Login thất bại", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
