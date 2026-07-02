package com.tokoku.orgaku.data.model

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val nim: String,
    val phone: String,
    val organization: String,
    val role: String,
    val stats: ProfileStats
)

data class ProfileStats(
    val attendance: String,
    val completedTasks: Int,
    val durationJoined: String
)

data class ProfileResponse(
    val user: UserProfile
)
