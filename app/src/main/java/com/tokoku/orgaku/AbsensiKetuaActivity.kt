package com.tokoku.orgaku

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
// SUDAH DIGANTI KE PACKAGE KAMU
import com.tokoku.orgaku.databinding.ActivityDetailTugasBinding

class AbsensiKetuaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailTugasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailTugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadTaskData()
    }

    private fun setupToolbar() {
        // Pastikan di activity_detail_tugas.xml ada ID bernama btnBack
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadTaskData() {
        val title = intent.getStringExtra("TASK_TITLE") ?: "Draft Proposal Sponsorship"
        binding.tvTaskTitle.text = title
    }
}