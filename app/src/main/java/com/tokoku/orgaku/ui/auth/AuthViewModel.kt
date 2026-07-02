package com.tokoku.orgaku.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokoku.orgaku.data.model.LoginRequest
import com.tokoku.orgaku.data.model.RegisterRequest
import com.tokoku.orgaku.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState<out T> {
    object Idle : AuthUiState<Nothing>()
    object Loading : AuthUiState<Nothing>()
    data class Success<T>(val data: T) : AuthUiState<T>()
    data class Error(val message: String) : AuthUiState<Nothing>()
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthUiState<String>>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState<String>> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<AuthUiState<String>>(AuthUiState.Idle)
    val registerState: StateFlow<AuthUiState<String>> = _registerState.asStateFlow()

    fun login(request: LoginRequest) {
        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            try {
                val response = repository.login(request)
                if (response.isSuccessful) {
                    val token = response.body()?.token
                    if (token != null) {
                        _loginState.value = AuthUiState.Success(token)
                    } else {
                        val errorMessage = response.body()?.message ?: response.body()?.error ?: "Login gagal"
                        _loginState.value = AuthUiState.Error(errorMessage)
                    }
                } else {
                    _loginState.value = AuthUiState.Error("Email atau password salah")
                }
            } catch (e: Exception) {
                _loginState.value = AuthUiState.Error("Gagal terhubung ke server: ${e.message}")
            }
        }
    }

    fun register(request: RegisterRequest) {
        viewModelScope.launch {
            _registerState.value = AuthUiState.Loading
            try {
                val response = repository.register(request)
                if (response.isSuccessful) {
                    _registerState.value = AuthUiState.Success(response.body()?.message ?: "Registrasi Berhasil")
                } else {
                    val errorMessage = response.body()?.error ?: response.body()?.message ?: "Registrasi gagal"
                    _registerState.value = AuthUiState.Error(errorMessage)
                }
            } catch (e: Exception) {
                _registerState.value = AuthUiState.Error("Gagal terhubung ke server: ${e.message}")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = AuthUiState.Idle
    }

    fun resetRegisterState() {
        _registerState.value = AuthUiState.Idle
    }
}
