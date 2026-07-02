package com.tokoku.orgaku.data.network

import com.tokoku.orgaku.data.model.CreateTaskRequest
import com.tokoku.orgaku.data.model.TaskItem
import com.tokoku.orgaku.data.model.TaskListResponse
import com.tokoku.orgaku.data.model.UpdateTaskStatusRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface TaskApi {
    @GET("tasks")
    suspend fun getTasks(
        @Header("Authorization") token: String
    ): Response<TaskListResponse>

    @POST("tasks")
    suspend fun createTask(
        @Header("Authorization") token: String,
        @Body request: CreateTaskRequest
    ): Response<TaskItem>

    @PATCH("tasks/{id}/status")
    suspend fun updateTaskStatus(
        @Header("Authorization") token: String,
        @Path("id") taskId: String,
        @Body request: UpdateTaskStatusRequest
    ): Response<TaskItem>

    @GET("tasks/{id}")
    suspend fun getTaskDetail(
        @Header("Authorization") token: String,
        @Path("id") taskId: String
    ): Response<TaskItem>
}
