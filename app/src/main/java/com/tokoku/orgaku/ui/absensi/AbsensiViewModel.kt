package com.tokoku.orgaku.ui.absensi

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.model.AbsensiRequest
import com.tokoku.orgaku.data.model.RekapAbsensiResponse
import com.tokoku.orgaku.data.model.RiwayatAbsensiResponse
import com.tokoku.orgaku.data.repository.AbsensiRepository
import com.tokoku.orgaku.util.QrHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class AbsensiState<out T> {
    object Idle : AbsensiState<Nothing>()
    object Loading : AbsensiState<Nothing>()
    data class Success<T>(val data: T) : AbsensiState<T>()
    data class Error(val message: String) : AbsensiState<Nothing>()
}

class AbsensiViewModel(
    private val repository: AbsensiRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // QR Generator State (Ketua)
    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap

    private val _timerSeconds = MutableStateFlow(30)
    val timerSeconds: StateFlow<Int> = _timerSeconds

    private val _currentToken = MutableStateFlow("")
    val currentToken: StateFlow<String> = _currentToken

    private var timerJob: Job? = null

    // Scan State (Anggota)
    private val _scanResult = MutableStateFlow<AbsensiState<String>>(AbsensiState.Idle)
    val scanResult: StateFlow<AbsensiState<String>> = _scanResult

    // Rekap/Riwayat State
    private val _rekapState = MutableStateFlow<AbsensiState<RekapAbsensiResponse>>(AbsensiState.Idle)
    val rekapState: StateFlow<AbsensiState<RekapAbsensiResponse>> = _rekapState

    private val _riwayatState = MutableStateFlow<AbsensiState<RiwayatAbsensiResponse>>(AbsensiState.Idle)
    val riwayatState: StateFlow<AbsensiState<RiwayatAbsensiResponse>> = _riwayatState

    fun startQrRotation() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val newToken = generateRandomToken()
                _currentToken.value = newToken
                _qrBitmap.value = QrHelper.generateQrCode(newToken)
                
                for (i in 30 downTo 1) {
                    _timerSeconds.value = i
                    delay(1000)
                }
            }
        }
    }

    private fun generateRandomToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }

    fun submitScanResult(scannedToken: String) {
        viewModelScope.launch {
            _scanResult.value = AbsensiState.Loading
            try {
                val userToken = sessionManager.getToken().first()
                if (userToken != null) {
                    val request = AbsensiRequest(scannedToken, "USER_ID", System.currentTimeMillis())
                    val response = repository.submitAbsensi(userToken, request)
                    if (response.isSuccessful) {
                        _scanResult.value = AbsensiState.Success("Absensi Berhasil!")
                    } else {
                        _scanResult.value = AbsensiState.Error("Token QR tidak valid atau sudah kedaluwarsa")
                    }
                } else {
                    _scanResult.value = AbsensiState.Error("Sesi berakhir")
                }
            } catch (e: Exception) {
                _scanResult.value = AbsensiState.Error(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    fun fetchRekap() {
        viewModelScope.launch {
            _rekapState.value = AbsensiState.Loading
            try {
                val userToken = sessionManager.getToken().first()
                if (userToken != null) {
                    val response = repository.getRekapAbsensi(userToken)
                    if (response.isSuccessful && response.body() != null) {
                        _rekapState.value = AbsensiState.Success(response.body()!!)
                    }
                }
            } catch (e: Exception) {
                _rekapState.value = AbsensiState.Error(e.message ?: "Gagal memuat rekap")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
