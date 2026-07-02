package com.tokoku.orgaku.data.repository

import com.tokoku.orgaku.data.network.ProfileApi

class ProfileRepository(private val api: ProfileApi) {
    suspend fun getProfile(token: String) = api.getProfile("Bearer $token")
}
