package com.tokoku.orgaku.data.model

import com.tokoku.orgaku.ScannedMember

data class AbsensiRequest(
    val token: String,
    val userId: String,
    val timestamp: Long
)

data class AbsensiResponse(
    val success: Boolean,
    val message: String
)

data class RekapAbsensiResponse(
    val totalMembers: Int,
    val presentCount: Int,
    val members: List<ScannedMember>
)

data class RiwayatAbsensiResponse(
    val history: List<AbsensiHistoryItem>
)

data class AbsensiHistoryItem(
    val date: String,
    val time: String,
    val status: String,
    val meetingTitle: String
)
