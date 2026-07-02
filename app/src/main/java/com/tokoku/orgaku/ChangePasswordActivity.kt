package com.tokoku.orgaku

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var edtCurrentPass: EditText
    private lateinit var edtNewPass: EditText
    private lateinit var edtConfirmPass: EditText
    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnBack: ImageButton
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        edtCurrentPass = findViewById(R.id.edt_current_password)
        edtNewPass = findViewById(R.id.edt_new_password)
        edtConfirmPass = findViewById(R.id.edt_confirm_password)
        btnUpdate = findViewById(R.id.btn_update_password)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        btnUpdate.setOnClickListener {
            validateAndUpdate()
        }
    }

    private fun validateAndUpdate() {
        val currentPass = edtCurrentPass.text.toString()
        val newPass = edtNewPass.text.toString()
        val confirmPass = edtConfirmPass.text.toString()

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            showTopSnackbar(getString(R.string.error_fill_all))
            return
        }

        if (newPass.length < 6) {
            showTopSnackbar(getString(R.string.error_new_password_length))
            return
        }

        if (newPass != confirmPass) {
            showTopSnackbar(getString(R.string.error_password_mismatch))
            return
        }

        updatePassword(currentPass, newPass)
    }

    private fun updatePassword(currentPass: String, newPass: String) {
        val user = auth.currentUser
        val credential = EmailAuthProvider.getCredential(user?.email!!, currentPass)

        btnUpdate.isEnabled = false
        btnUpdate.text = getString(R.string.processing)

        // Re-authenticate user before sensitive operation
        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        showTopSnackbar(getString(R.string.password_updated))
                        Handler(Looper.getMainLooper()).postDelayed({
                            finish()
                        }, 1000)
                    } else {
                        btnUpdate.isEnabled = true
                        btnUpdate.text = getString(R.string.btn_update_password)
                        showTopSnackbar("Gagal: ${updateTask.exception?.message}")
                    }
                }
            } else {
                btnUpdate.isEnabled = true
                btnUpdate.text = getString(R.string.btn_update_password)
                showTopSnackbar(getString(R.string.error_wrong_current_password))
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
