package com.tokoku.orgaku

import android.app.Application
import android.content.Intent
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OrgakuApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable Firestore Offline Persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        try {
            // OneSignal Initialization
            OneSignal.Debug.logLevel = com.onesignal.debug.LogLevel.VERBOSE
            OneSignal.initWithContext(this, "893ddfc6-a37a-48ed-94d7-4eb71cc0684d")

            // Link User ID
            FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
                OneSignal.login(userId)
            }

            // Notification Permission
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    OneSignal.Notifications.requestPermission(true)
                } catch (e: Exception) {
                    Log.e("OrgakuApp", "Notification permission error: ${e.message}")
                }
            }

            // Notification Click Listener
            OneSignal.Notifications.addClickListener(object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    try {
                        val data = event.notification.additionalData
                        if (data != null && data.has("item_id") && data.has("item_type")) {
                            val itemId = data.optString("item_id")
                            val itemType = data.optString("item_type")

                            val intent = when (itemType) {
                                "task" -> Intent(this@OrgakuApp, DetailTugasActivity::class.java).apply {
                                    putExtra("TASK_ID", itemId)
                                }
                                "meeting" -> Intent(this@OrgakuApp, DetailMeetingActivity::class.java).apply {
                                    putExtra("EXTRA_MEETING_ID", itemId)
                                }
                                else -> null
                            }

                            intent?.let {
                                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OrgakuApp", "Error handling notification click: ${e.message}")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("OrgakuApp", "OneSignal Init Error: ${e.message}")
        }
    }
}
