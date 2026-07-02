package com.tokoku.orgaku

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button
    private var currentPage = 0
    private val totalSlides = 3

    private val slides = listOf(
        OnboardingSlide(
            "Kelola Organisasi Mudah",
            "Atur jadwal, tugas, dan anggota organisasi kamu dalam satu aplikasi.",
            R.drawable.onboarding_1
        ),
        OnboardingSlide(
            "Pantau Kehadiran Real-time",
            "Absensi digital dengan QR Code, cepat dan akurat tanpa kertas.",
            R.drawable.onboarding_2
        ),
        OnboardingSlide(
            "Kolaborasi Lebih Produktif",
            "Bagikan dokumen dan koordinasi tim jadi lebih efisien.",
            R.drawable.onboarding_3
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager_onboarding)
        dotsContainer = findViewById(R.id.dots_container)
        btnNext = findViewById(R.id.btn_next)
        btnSkip = findViewById(R.id.btn_skip)

        val adapter = OnboardingAdapter(slides)
        viewPager.adapter = adapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateUI()
            }
        })

        btnNext.setOnClickListener {
            if (currentPage < totalSlides - 1) {
                viewPager.currentItem = currentPage + 1
            } else {
                completeOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            completeOnboarding()
        }

        updateUI()
    }

    private fun updateUI() {
        btnNext.text = if (currentPage == totalSlides - 1) {
            getString(R.string.start)
        } else {
            getString(R.string.next)
        }
        updateDots()
    }

    private fun updateDots() {
        dotsContainer.removeAllViews()
        for (i in 0 until totalSlides) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(
                if (i == currentPage) 32 else 8,
                6
            )
            params.marginEnd = 8
            dot.layoutParams = params
            dot.setBackgroundResource(
                if (i == currentPage) R.drawable.dot_active else R.drawable.dot_inactive
            )
            dotsContainer.addView(dot)
        }
    }

    private fun completeOnboarding() {
        val sharedPref = getSharedPreferences("orgaku_prefs", MODE_PRIVATE)
        sharedPref.edit().putBoolean("has_seen_onboarding", true).apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}