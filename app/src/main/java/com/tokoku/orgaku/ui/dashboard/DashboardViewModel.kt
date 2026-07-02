package com.tokoku.orgaku.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.model.DashboardResponse
import com.tokoku.orgaku.data.repository.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class DashboardState {
    object Idle : DashboardState()
    object Loading : DashboardState()
    data class Success(val data: DashboardResponse) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

class DashboardViewModel(
    private val repository: DashboardRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardState>(DashboardState.Idle)
    val uiState: StateFlow<DashboardState> = _uiState

    fun fetchDashboardData() {
        viewModelScope.launch {
            _uiState.value = DashboardState.Loading
            try {
                val token = sessionManager.getToken().first()
                if (token != null) {
                    val response = repository.getDashboardData(token)
                    if (response.isSuccessful && response.body() != null) {
                        _uiState.value = DashboardState.Success(response.body()!!)
                    } else {
                        _uiState.value = DashboardState.Error("Gagal memuat data")
                    }
                } else {
                    _uiState.value = DashboardState.Error("Sesi berakhir")
                }
            } catch (e: Exception) {
                _uiState.value = DashboardState.Error(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    fun refresh() {
        fetchDashboardData()
    }
}
