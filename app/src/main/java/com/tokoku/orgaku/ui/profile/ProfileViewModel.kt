package com.tokoku.orgaku.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.model.UserProfile
import com.tokoku.orgaku.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    data class Success(val user: UserProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
    object LogoutSuccess : ProfileState()
}

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState

    fun fetchProfileData() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val token = sessionManager.getToken().first()
                if (token != null) {
                    val response = repository.getProfile(token)
                    if (response.isSuccessful && response.body() != null) {
                        _profileState.value = ProfileState.Success(response.body()!!.user)
                    } else {
                        _profileState.value = ProfileState.Error("Gagal memuat profil")
                    }
                } else {
                    _profileState.value = ProfileState.Error("Sesi berakhir")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _profileState.value = ProfileState.LogoutSuccess
        }
    }
}
