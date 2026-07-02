package com.tokoku.orgaku

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var btnReset: MaterialButton
    private lateinit var btnBack: MaterialButton
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()
        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        edtEmail = findViewById(R.id.edt_forgot_email)
        btnReset = findViewById(R.id.btn_reset_password)
        btnBack = findViewById(R.id.btn_back_to_login)
    }

    private fun setupClickListeners() {
        btnReset.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            if (email.isEmpty()) {
                showTopSnackbar("Masukkan email Anda")
                return@setOnClickListener
            }

            sendPasswordReset(email)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun sendPasswordReset(email: String) {
        btnReset.isEnabled = false
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showTopSnackbar("Tautan reset telah dikirim")

                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1000)
                } else {
                    btnReset.isEnabled = true
                    val errorMessage = task.exception?.message ?: "Gagal mengirim email reset"
                    showTopSnackbar(errorMessage)
                }
            }
    }

    private fun showTopSnackbar(message: String) {
        val snack =
            Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)
        val snackView = snack.view

        val params = snackView.layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        } else if (params is CoordinatorLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        }

        snackView.setBackgroundResource(R.drawable.bg_snackbar)
        snackView.elevation = 10f
        snack.setTextColor(Color.WHITE)
        snack.show()
    }
}
