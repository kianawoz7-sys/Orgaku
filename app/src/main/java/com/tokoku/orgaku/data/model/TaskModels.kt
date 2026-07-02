package com.tokoku.orgaku.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID

@IgnoreExtraProperties
data class Task(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var priority: String = "Medium", // Low, Medium, High
    var deadline: String = "", // e.g., "2026-05-01"
    var status: String = "todo", // "todo", "in_progress", "done"
    var authorId: String = "",
    var assigneeId: String = "",
    var userId: String? = null,
    var organisasiId: String = "", // Master key — used by fragment queries
    var checklist: List<SubTask> = emptyList(),
    var createdAt: Any? = null
)

@IgnoreExtraProperties
data class SubTask(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var isCompleted: Boolean = false
)

@IgnoreExtraProperties
data class Comment(
    var id: String = "",
    var userId: String = "",
    var userName: String = "",
    var userAvatar: String = "",
    var body: String = "",
    var timestamp: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String,
    val title: String,
    val description: String,
    val priority: String,
    val deadline: String,
    val status: String,
    val assigneeName: String?,
    val assigneeImage: String?
)

data class CreateTaskRequest(
    val title: String,
    val description: String,
    val priority: String,
    val deadline: String,
    val assigneeId: String?
)

data class UpdateTaskStatusRequest(
    val status: String
)

data class TaskListResponse(
    val tasks: List<TaskItem>
)
