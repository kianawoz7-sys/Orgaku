package com.tokoku.orgaku

data class Task(
    val title: String,
    val priority: String,
    val deadline: String,
    val userImage: Int,
    val status: String
)

data class Document(
    var id: String = "",
    val title: String = "",
    val category: String = "",
    val driveUrl: String = "",
    val createdAt: Any? = null,
    val organisasiId: String = ""
)

data class ScannedMember(
    val userId: String,
    val name: String,
    val nim: String,
    val time: String,
    val avatar: String,
    val isPresent: Boolean = false,
    val status: String = "BELUM"
)

data class MeetingSchedule(
    val id: String = "",
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val location: String,
    val startTime: String = "",
    val endTime: String = ""
)

data class AttendanceHistory(
    val title: String,
    val date: String,
    val time: String,
    val status: String,
    val scanTime: String? = null
)
