package com.tokoku.orgaku

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tokoku.orgaku.data.model.Notification

object OneSignalHelper {
    private const val ONESIGNAL_URL = "https://onesignal.com/api/v1/notifications"
    private const val APP_ID = "893ddfc6-a37a-48ed-94d7-4eb71cc0684d"
    private const val REST_API_KEY = BuildConfig.ONESIGNAL_REST_API_KEY

    private val client = OkHttpClient()

    fun sendNotification(
        title: String,
        message: String,
        itemId: String? = null,
        itemType: String? = null,
        targetUserId: String? = null,
        // senderUid: UID pengirim notifikasi, dipakai oleh NotificationActivity
        // untuk enrich nama & organisasi pengirim dengan data real dari Firestore.
        senderUid: String? = FirebaseAuth.getInstance().currentUser?.uid
    ) {
        // Simpan ke Firestore dulu (termasuk senderUid)
        if (targetUserId != null) {
            saveNotificationToFirestore(
                title, message, targetUserId,
                itemType ?: "", itemId ?: "",
                senderUid ?: ""
            )
        }

        val jsonPayload = JSONObject().apply {
            put("app_id", APP_ID)

            if (targetUserId != null) {
                val targets = org.json.JSONArray().put(targetUserId)

                FirebaseAuth.getInstance().currentUser?.uid?.let { currentUid ->
                    if (currentUid != targetUserId) {
                        targets.put(currentUid)
                    }
                }

                put("include_external_user_ids", targets)
            } else {
                put("included_segments", org.json.JSONArray().put("Total Subscriptions"))
            }

            put("headings", JSONObject().put("en", title))
            put("contents", JSONObject().put("en", message))

            if (itemId != null && itemType != null) {
                put("data", JSONObject().apply {
                    put("item_id", itemId)
                    put("item_type", itemType)
                })
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(ONESIGNAL_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Basic $REST_API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OneSignalHelper", "Failed to send notification: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("OneSignalHelper", "Notification sent successfully")
                } else {
                    Log.e(
                        "OneSignalHelper",
                        "Error response: ${response.code} ${response.body?.string()}"
                    )
                }
            }
        })
    }

    private fun saveNotificationToFirestore(
        title: String,
        message: String,
        targetUserId: String,
        type: String,
        itemId: String,
        senderUid: String = ""
    ) {
        val db = FirebaseFirestore.getInstance()
        val notification = Notification(
            title = title,
            message = message,
            targetUserId = targetUserId,
            timestamp = System.currentTimeMillis(),
            type = type,
            itemId = itemId,
            senderUid = senderUid  // disimpan agar bisa di-enrich saat tampil
        )
        db.collection("notifications").add(notification)
            .addOnSuccessListener {
                Log.d("OneSignalHelper", "Notification saved to Firestore (senderUid: $senderUid)")
            }
            .addOnFailureListener { e ->
                Log.e("OneSignalHelper", "Failed to save notification: ${e.message}")
            }
    }
}
