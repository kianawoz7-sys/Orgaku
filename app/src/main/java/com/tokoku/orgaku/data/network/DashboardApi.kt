package com.tokoku.orgaku.data.network

import com.tokoku.orgaku.data.model.DashboardResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface DashboardApi {
    @GET("dashboard")
    suspend fun getDashboardData(
        @Header("Authorization") token: String
    ): Response<DashboardResponse>
}
