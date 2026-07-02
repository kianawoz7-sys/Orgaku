package com.tokoku.orgaku

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.network.RetrofitClient
import com.tokoku.orgaku.data.repository.AbsensiRepository
import com.tokoku.orgaku.databinding.ActivityScanQrBinding
import com.tokoku.orgaku.ui.absensi.AbsensiState
import com.tokoku.orgaku.ui.absensi.AbsensiViewModel
import com.tokoku.orgaku.ui.absensi.AbsensiViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScanQrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanQrBinding
    private lateinit var viewModel: AbsensiViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        observeViewModel()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnOpenCamera.setOnClickListener {
            startQrScanner()
        }
    }

    private fun setupViewModel() {
        val repository = AbsensiRepository(RetrofitClient.absensiInstance)
        val sessionManager = SessionManager(this)
        val factory = AbsensiViewModelFactory(repository, sessionManager)
        viewModel = ViewModelProvider(this, factory)[AbsensiViewModel::class.java]
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.scanResult.collectLatest { state ->
                when (state) {
                    is AbsensiState.Loading -> {
                        binding.btnOpenCamera.isEnabled = false
                        binding.btnOpenCamera.text = "Memproses..."
                    }
                    is AbsensiState.Success -> {
                        Toast.makeText(this@ScanQrActivity, state.data, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    is AbsensiState.Error -> {
                        binding.btnOpenCamera.isEnabled = true
                        binding.btnOpenCamera.text = "Buka Kamera & Scan"
                        Toast.makeText(this@ScanQrActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startQrScanner() {
        val scanner = GmsBarcodeScanning.getClient(this)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue != null) {
                    viewModel.submitScanResult(rawValue)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scan dibatalkan atau gagal", Toast.LENGTH_SHORT).show()
            }
    }
}
