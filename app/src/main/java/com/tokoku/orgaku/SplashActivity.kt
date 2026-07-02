package com.tokoku.orgaku

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Delay 1 second (1000ms) for a snappy experience
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 1500)
    }

    private fun checkLoginStatus() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // No user, go to Login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        } else {
            // User exists, check onboarding
            val sharedPref = getSharedPreferences("orgaku_prefs", MODE_PRIVATE)
            val hasSeenOnboarding = sharedPref.getBoolean("has_seen_onboarding", false)

            if (!hasSeenOnboarding) {
                val intent = Intent(this, OnboardingActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}