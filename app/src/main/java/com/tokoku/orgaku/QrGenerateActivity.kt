package com.tokoku.orgaku

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.network.RetrofitClient
import com.tokoku.orgaku.data.repository.AbsensiRepository
import com.tokoku.orgaku.databinding.ActivityQrGenerateBinding
import com.tokoku.orgaku.ui.absensi.AbsensiViewModel
import com.tokoku.orgaku.ui.absensi.AbsensiViewModelFactory

class QrGenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrGenerateBinding
    private lateinit var viewModel: AbsensiViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupToolbar()
        setupRecyclerView()
    }

    private fun setupViewModel() {
        val repository = AbsensiRepository(RetrofitClient.absensiInstance)
        val sessionManager = SessionManager(this)
        val factory = AbsensiViewModelFactory(repository, sessionManager)
        viewModel = ViewModelProvider(this, factory)[AbsensiViewModel::class.java]
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        // Updated Mock data to match new ScannedMember constructor
        val members = listOf(
            ScannedMember("1", "Budi Santoso", "20231002", "16.10 WIB", "avatar_1", true, "HADIR"),
            ScannedMember("2", "Citra Dewi", "20231003", "-", "avatar_2", false, "BELUM"),
            ScannedMember("3", "Dian Pratama", "20231004", "16.12 WIB", "avatar_3", true, "HADIR"),
            ScannedMember("4", "Eko Wahyudi", "20231005", "-", "avatar_4", false, "BELUM"),
            ScannedMember("5", "Fahri Ramadan", "20231006", "-", "avatar_5", false, "BELUM")
        )

        binding.rvScannedMembers.layoutManager = LinearLayoutManager(this)
        binding.rvScannedMembers.adapter = ScannedMemberAdapter(
            members,
            onManualHadirClick = { member -> markAttendanceManual(member) },
            onBatalkanClick = { /* Do nothing */ }
        )
    }

    private fun markAttendanceManual(member: ScannedMember) {
        // Logic for manual attendance (Placeholder for now)
        Toast.makeText(
            this,
            "Berhasil menambahkan ${member.name} secara manual",
            Toast.LENGTH_SHORT
        ).show()
    }
}
