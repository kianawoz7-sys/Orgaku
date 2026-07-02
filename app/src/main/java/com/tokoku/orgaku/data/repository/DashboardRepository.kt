package com.tokoku.orgaku.data.repository

import com.tokoku.orgaku.data.network.DashboardApi

class DashboardRepository(private val api: DashboardApi) {
    suspend fun getDashboardData(token: String) = api.getDashboardData("Bearer $token")
}
