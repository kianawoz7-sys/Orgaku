package com.tokoku.orgaku.data.network

import com.tokoku.orgaku.data.model.LoginRequest
import com.tokoku.orgaku.data.model.LoginResponse
import com.tokoku.orgaku.data.model.RegisterRequest
import com.tokoku.orgaku.data.model.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>
}
