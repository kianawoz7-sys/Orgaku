package com.tokoku.orgaku.data.repository

import com.tokoku.orgaku.data.model.CreateTaskRequest
import com.tokoku.orgaku.data.model.UpdateTaskStatusRequest
import com.tokoku.orgaku.data.network.TaskApi

class TaskRepository(private val api: TaskApi) {
    suspend fun getTasks(token: String) = api.getTasks("Bearer $token")

    suspend fun createTask(token: String, request: CreateTaskRequest) =
        api.createTask("Bearer $token", request)

    suspend fun updateTaskStatus(token: String, taskId: String, status: String) =
        api.updateTaskStatus("Bearer $token", taskId, UpdateTaskStatusRequest(status))

    suspend fun getTaskDetail(token: String, taskId: String) =
        api.getTaskDetail("Bearer $token", taskId)
}
