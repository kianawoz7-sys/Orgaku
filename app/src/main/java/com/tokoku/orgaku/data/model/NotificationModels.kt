package com.tokoku.orgaku.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Notification(
    var id: String = "",
    val title: String = "",
    val message: String = "",
    val targetUserId: String = "",
    val timestamp: Long = 0L,
    val type: String = "",      // "task", "comment", "meeting", "absensi"
    val itemId: String = "",    // taskId, meetingId, dll
    val senderUid: String = "", // UID pengirim notifikasi (untuk di-enrich)

    // Field di bawah TIDAK disimpan di Firestore (diisi secara lokal setelah enrich)
    @field:com.google.firebase.firestore.Exclude
    @get:com.google.firebase.firestore.Exclude
    var senderName: String = "",

    @field:com.google.firebase.firestore.Exclude
    @get:com.google.firebase.firestore.Exclude
    var senderOrg: String = ""
)
