package com.tokoku.orgaku.data.repository

import com.tokoku.orgaku.data.model.AbsensiRequest
import com.tokoku.orgaku.data.network.AbsensiApi

class AbsensiRepository(private val api: AbsensiApi) {
    suspend fun submitAbsensi(token: String, request: AbsensiRequest) = 
        api.submitAbsensi("Bearer $token", request)

    suspend fun getRekapAbsensi(token: String) = 
        api.getRekapAbsensi("Bearer $token")

    suspend fun getRiwayatAbsensi(token: String) = 
        api.getRiwayatAbsensi("Bearer $token")
}
