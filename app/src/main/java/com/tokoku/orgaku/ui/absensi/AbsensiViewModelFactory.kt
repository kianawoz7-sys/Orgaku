package com.tokoku.orgaku.ui.absensi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.repository.AbsensiRepository

class AbsensiViewModelFactory(
    private val repository: AbsensiRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AbsensiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AbsensiViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
