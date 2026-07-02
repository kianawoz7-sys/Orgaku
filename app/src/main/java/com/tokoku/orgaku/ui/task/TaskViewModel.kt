package com.tokoku.orgaku.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokoku.orgaku.data.local.SessionManager
import com.tokoku.orgaku.data.model.CreateTaskRequest
import com.tokoku.orgaku.data.model.TaskItem
import com.tokoku.orgaku.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class TaskState<out T> {
    object Idle : TaskState<Nothing>()
    object Loading : TaskState<Nothing>()
    data class Success<T>(val data: T) : TaskState<T>()
    data class Error(val message: String) : TaskState<Nothing>()
}

class TaskViewModel(
    private val repository: TaskRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _taskListState = MutableStateFlow<TaskState<List<TaskItem>>>(TaskState.Idle)
    val taskListState: StateFlow<TaskState<List<TaskItem>>> = _taskListState

    private val _createTaskState = MutableStateFlow<TaskState<TaskItem>>(TaskState.Idle)
    val createTaskState: StateFlow<TaskState<TaskItem>> = _createTaskState

    private val _updateStatusState = MutableStateFlow<TaskState<TaskItem>>(TaskState.Idle)
    val updateStatusState: StateFlow<TaskState<TaskItem>> = _updateStatusState

    fun fetchTasks() {
        viewModelScope.launch {
            _taskListState.value = TaskState.Loading
            try {
                val token = sessionManager.getToken().first()
                if (token != null) {
                    val response = repository.getTasks(token)
                    if (response.isSuccessful && response.body() != null) {
                        _taskListState.value = TaskState.Success(response.body()!!.tasks)
                    } else {
                        _taskListState.value = TaskState.Error("Gagal mengambil daftar tugas")
                    }
                } else {
                    _taskListState.value = TaskState.Error("Sesi berakhir")
                }
            } catch (e: Exception) {
                _taskListState.value = TaskState.Error(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    fun createTask(request: CreateTaskRequest) {
        viewModelScope.launch {
            _createTaskState.value = TaskState.Loading
            try {
                val token = sessionManager.getToken().first()
                if (token != null) {
                    val response = repository.createTask(token, request)
                    if (response.isSuccessful && response.body() != null) {
                        _createTaskState.value = TaskState.Success(response.body()!!)
                        fetchTasks() // Refresh list
                    }
                }
            } catch (e: Exception) {
                _createTaskState.value = TaskState.Error(e.message ?: "Gagal membuat tugas")
            }
        }
    }

    fun updateTaskStatus(taskId: String, newStatus: String) {
        viewModelScope.launch {
            _updateStatusState.value = TaskState.Loading
            try {
                val token = sessionManager.getToken().first()
                if (token != null) {
                    val response = repository.updateTaskStatus(token, taskId, newStatus)
                    if (response.isSuccessful && response.body() != null) {
                        _updateStatusState.value = TaskState.Success(response.body()!!)
                        fetchTasks() // Refresh list
                    }
                }
            } catch (e: Exception) {
                _updateStatusState.value = TaskState.Error(e.message ?: "Gagal mengubah status")
            }
        }
    }
}
