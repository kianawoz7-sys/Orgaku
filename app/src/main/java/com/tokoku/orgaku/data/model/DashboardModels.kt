package com.tokoku.orgaku.data.model

import com.tokoku.orgaku.Document

data class DashboardResponse(
    val stats: DashboardStats,
    val nextMeeting: NextMeeting?,
    val latestTasks: List<TaskItem>,
    val latestDocs: List<Document>
)

data class DashboardStats(
    val pendingTasksCount: Int,
    val attendancePercentage: String,
    val upcomingMeetingsCount: Int,
    val activeMembersCount: Int
)

data class NextMeeting(
    val title: String,
    val date: String,
    val month: String,
    val time: String,
    val location: String
)
