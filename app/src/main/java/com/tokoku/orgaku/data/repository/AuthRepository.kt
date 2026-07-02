package com.tokoku.orgaku.data.repository

import com.tokoku.orgaku.data.model.LoginRequest
import com.tokoku.orgaku.data.model.RegisterRequest
import com.tokoku.orgaku.data.network.AuthApi

class AuthRepository(private val api: AuthApi) {
    suspend fun login(request: LoginRequest) = api.login(request)
    suspend fun register(request: RegisterRequest) = api.register(request)
}
