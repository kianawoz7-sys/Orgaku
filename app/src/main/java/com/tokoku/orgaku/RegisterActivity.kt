package com.tokoku.orgaku

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.tokoku.orgaku.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var edtName: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtPassword: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var pbRegister: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        tilName = findViewById(R.id.til_name)
        tilEmail = findViewById(R.id.til_email)
        tilPassword = findViewById(R.id.til_password)
        edtName = findViewById(R.id.edt_name)
        edtEmail = findViewById(R.id.edt_email)
        edtPassword = findViewById(R.id.edt_password)
        btnRegister = findViewById(R.id.btn_register)
        pbRegister = findViewById(R.id.pb_register)
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            val name = edtName.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (validateInputs(name, email, password)) {
                handleRegister(name, email, password)
            }
        }

        findViewById<View>(R.id.btn_back_to_login).setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(name: String, email: String, password: String): Boolean {
        var isValid = true
        tilName.error = null
        tilEmail.error = null
        tilPassword.error = null
        hideError()

        if (name.isEmpty()) {
            tilName.error = "Nama lengkap tidak boleh kosong"
            isValid = false
        }

        if (email.isEmpty()) {
            tilEmail.error = "Email tidak boleh kosong"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Format email tidak valid"
            isValid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = "Password tidak boleh kosong"
            isValid = false
        } else if (password.length < 6) {
            tilPassword.error = "Password minimal 6 karakter"
            isValid = false
        }

        return isValid
    }

    private fun handleRegister(name: String, email: String, password: String) {
        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val userMap = hashMapOf(
                            "nama" to name,
                            "email" to email,
                            "role" to "anggota",
                            "avatar_id" to "avatar_1",
                            "nim" to "",
                            "no_hp" to "",
                            "app_language" to "id",
                            "app_theme" to "light"
                        )

                        db.collection("users").document(userId)
                            .set(userMap)
                            .addOnCompleteListener { firestoreTask ->
                                if (firestoreTask.isSuccessful) {
                                    showTopSnackbar("Registrasi Berhasil!")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        navigateToDashboard()
                                    }, 1000)
                                } else {
                                    setLoading(false)
                                    showTopSnackbar("Gagal menyimpan profil: ${firestoreTask.exception?.message}")
                                }
                            }
                    }
                } else {
                    setLoading(false)
                    val errorMsg = when (val exception = authTask.exception) {
                        is FirebaseAuthUserCollisionException -> "Email sudah terdaftar, silakan gunakan email lain"
                        is FirebaseAuthWeakPasswordException -> "Kata sandi terlalu lemah, gunakan minimal 6 karakter"
                        is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid"
                        is FirebaseNetworkException -> "Koneksi internet bermasalah, silakan coba lagi"
                        else -> "Terjadi kesalahan saat mendaftar: ${exception?.localizedMessage}"
                    }
                    showError(errorMsg)
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        btnRegister.isEnabled = !isLoading
        btnRegister.text = if (isLoading) "" else getString(R.string.register)
        pbRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun navigateToDashboard() {
        val sharedPref = getSharedPreferences("orgaku_prefs", MODE_PRIVATE)
        val hasSeenOnboarding = sharedPref.getBoolean("has_seen_onboarding", false)

        val intent = if (!hasSeenOnboarding) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.errorContainer.visibility = View.VISIBLE
        binding.txtError.text = message
    }

    private fun hideError() {
        binding.errorContainer.visibility = View.GONE
    }

    private fun showTopSnackbar(message: String) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
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
