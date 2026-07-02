package com.tokoku.orgaku.data.network

import com.tokoku.orgaku.data.model.AbsensiRequest
import com.tokoku.orgaku.data.model.AbsensiResponse
import com.tokoku.orgaku.data.model.RekapAbsensiResponse
import com.tokoku.orgaku.data.model.RiwayatAbsensiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AbsensiApi {
    @POST("absensi/submit")
    suspend fun submitAbsensi(
        @Header("Authorization") token: String,
        @Body request: AbsensiRequest
    ): Response<AbsensiResponse>

    @GET("absensi/rekap")
    suspend fun getRekapAbsensi(
        @Header("Authorization") token: String
    ): Response<RekapAbsensiResponse>

    @GET("absensi/history")
    suspend fun getRiwayatAbsensi(
        @Header("Authorization") token: String
    ): Response<RiwayatAbsensiResponse>
}
