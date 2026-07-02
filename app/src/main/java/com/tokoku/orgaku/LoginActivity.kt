package com.tokoku.orgaku

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.onesignal.OneSignal
import com.tokoku.orgaku.util.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var pbLogin: ProgressBar
    private lateinit var btnGoogleLogin: MaterialButton
    private lateinit var btnForgotPassword: View
    private lateinit var btnRegister: View
    private lateinit var errorContainer: View
    private lateinit var txtError: android.widget.TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var sessionManager: SessionManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken)
            } catch (e: ApiException) {
                showTopSnackbar("Google Sign-In Gagal: ${e.message}")
                resetLoginButtonState()
            }
        } else {
            resetLoginButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        initializeViews()
        setupClickListeners()

        if (intent.getBooleanExtra("LOGOUT_SUCCESS", false)) {
            showTopSnackbar(getString(R.string.msg_logout_success))
        }
    }

    private fun initializeViews() {
        tilEmail = findViewById(R.id.til_email)
        tilPassword = findViewById(R.id.til_password)
        edtEmail = findViewById(R.id.edt_email)
        edtPassword = findViewById(R.id.edt_password)
        btnLogin = findViewById(R.id.btn_login)
        pbLogin = findViewById(R.id.pb_login)
        btnGoogleLogin = findViewById(R.id.btn_google_login)
        btnForgotPassword = findViewById(R.id.btn_forgot_password)
        btnRegister = findViewById(R.id.btn_register)
        errorContainer = findViewById(R.id.error_container)
        txtError = findViewById(R.id.txt_error)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()
            if (validateInputs(email, password)) {
                handleEmailLogin(email, password)
            }
        }

        btnGoogleLogin.setOnClickListener {
            signInWithGoogle()
        }

        btnForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true
        tilEmail.error = null
        tilPassword.error = null
        hideError()

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

    private fun handleEmailLogin(email: String, password: String) {
        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        db.collection("users").document(userId).get(Source.SERVER)
                            .addOnCompleteListener { dbTask ->
                                if (dbTask.isSuccessful) {
                                    val document = dbTask.result
                                    if (document != null && document.exists()) {
                                        val name =
                                            document.getString("nama") ?: document.getString("name")
                                            ?: ""
                                        val role = document.getString("role") ?: "anggota"
                                        val avatar = document.getString("avatar_id") ?: "avatar_1"
                                        val org = document.getString("organisasi")
                                            ?: document.getString("organization") ?: "-"
                                        val orgId = document.getString("organisasiId") ?: ""

                                        sessionManager.saveUserProfile(name, role, avatar, org, orgId)

                                        try {
                                            OneSignal.login(userId)
                                        } catch (e: Exception) {
                                            Log.e("LoginActivity", "OneSignal error: ${e.message}")
                                        }
                                        showTopSnackbar("Login Berhasil!")
                                    } else {
                                        createNewUserInFirestore(userId, "", email)
                                        try {
                                            OneSignal.login(userId)
                                        } catch (e: Exception) {
                                            Log.e("LoginActivity", "OneSignal error: ${e.message}")
                                        }
                                        showTopSnackbar("Login Berhasil (Profil Dibuat)")
                                    }
                                } else {
                                    Log.e(
                                        "LoginActivity",
                                        "Firestore fetch failed: ${dbTask.exception?.message}"
                                    )
                                    showTopSnackbar("Login Berhasil (Offline Mode)")
                                }

                                Handler(Looper.getMainLooper()).postDelayed({
                                    navigateToDashboard()
                                }, 800)
                            }
                    }
                } else {
                    setLoading(false)
                    val errorMsg = when (val exception = task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Email atau kata sandi salah"
                        is FirebaseAuthInvalidUserException -> "Akun tidak ditemukan atau telah dinonaktifkan"
                        is FirebaseNetworkException -> "Koneksi internet bermasalah, silakan coba lagi"
                        else -> "Terjadi kesalahan saat login: ${exception?.localizedMessage}"
                    }
                    showError(errorMsg)
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        btnLogin.isEnabled = !isLoading
        btnLogin.text = if (isLoading) "" else getString(R.string.login)
        pbLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnGoogleLogin.isEnabled = !isLoading
    }

    private fun createNewUserInFirestore(userId: String, name: String, email: String) {
        val userMap = hashMapOf(
            "nama" to name,
            "email" to email,
            "role" to "anggota",
            "avatar_id" to "avatar_1",
            "nim" to "",
            "no_hp" to ""
        )
        db.collection("users").document(userId).set(userMap)
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Error creating user: ${e.message}")
            }
    }

    private fun signInWithGoogle() {
        setLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String?) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid
                    if (userId != null) {
                        db.collection("users").document(userId).get(Source.SERVER)
                            .addOnCompleteListener { dbTask ->
                                if (dbTask.isSuccessful) {
                                    val document = dbTask.result
                                    if (document == null || !document.exists()) {
                                        createNewUserInFirestore(
                                            userId,
                                            user.displayName ?: "",
                                            user.email ?: ""
                                        )
                                        sessionManager.saveUserProfile(
                                            user.displayName ?: "",
                                            "anggota",
                                            "avatar_1",
                                            "-"
                                        )
                                    } else {
                                        val name =
                                            document.getString("nama") ?: document.getString("name")
                                            ?: ""
                                        val role = document.getString("role") ?: "anggota"
                                        val avatar = document.getString("avatar_id") ?: "avatar_1"
                                        val org = document.getString("organisasi")
                                            ?: document.getString("organization") ?: "-"
                                        val orgId = document.getString("organisasiId") ?: ""
                                        sessionManager.saveUserProfile(name, role, avatar, org, orgId)
                                    }
                                    try {
                                        OneSignal.login(userId)
                                    } catch (e: Exception) {
                                        Log.e("LoginActivity", "OneSignal error: ${e.message}")
                                    }
                                    showTopSnackbar("Login Google Berhasil!")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        navigateToDashboard()
                                    }, 1000)
                                } else {
                                    Log.e(
                                        "LoginActivity",
                                        "Gagal cek user via Google: ${dbTask.exception?.message}"
                                    )
                                    navigateToDashboard()
                                }
                            }
                    }
                } else {
                    showTopSnackbar("Autentikasi Firebase Gagal.")
                    resetLoginButtonState()
                }
            }
    }

    private fun showError(message: String) {
        errorContainer.visibility = View.VISIBLE
        txtError.text = message
    }

    private fun hideError() {
        errorContainer.visibility = View.GONE
    }

    private fun resetLoginButtonState() {
        setLoading(false)
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

    private fun showTopSnackbar(message: String) {
        val snack =
            Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)
        val snackView = snack.view

        val params = snackView.layoutParams
        if (params is CoordinatorLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        } else if (params is FrameLayout.LayoutParams) {
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
