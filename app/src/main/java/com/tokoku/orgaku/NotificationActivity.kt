package com.tokoku.orgaku

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tokoku.orgaku.data.model.Notification
import com.tokoku.orgaku.databinding.ActivityNotificationBinding

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        fetchNotifications()
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
    }

    private fun fetchNotifications() {
        val currentUid = auth.currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("targetUserId", currentUid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("NotificationActivity", "Error fetching notifications", error)
                    return@addSnapshotListener
                }

                val rawList = mutableListOf<Notification>()
                value?.forEach { doc ->
                    val notif = doc.toObject(Notification::class.java)
                    notif.id = doc.id
                    rawList.add(notif)
                }

                if (rawList.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.rvNotifications.visibility = View.VISIBLE
                    // Enrich sender info before passing to Adapter
                    enrichAndDisplay(rawList)
                }
            }
    }

    /**
     * Untuk setiap notifikasi yang memiliki senderUid, fetch nama pengirim
     * dan nama organisasi mereka (via organisasiId → collection "organizations").
     *
     * Menggunakan counter atomik untuk menunggu semua async fetch selesai,
     * lalu baru tampilkan list yang sudah ter-enrich ke RecyclerView.
     */
    private fun enrichAndDisplay(list: MutableList<Notification>) {
        // Notifikasi tanpa senderUid tidak perlu di-enrich
        val needEnrich = list.filter { it.senderUid.isNotEmpty() }

        if (needEnrich.isEmpty()) {
            displayList(list)
            return
        }

        var pending = needEnrich.size

        for (notif in needEnrich) {
            db.collection("users").document(notif.senderUid).get()
                .addOnSuccessListener { userDoc ->
                    if (userDoc != null && userDoc.exists()) {
                        notif.senderName = userDoc.getString("nama")
                            ?: userDoc.getString("name")
                            ?: "Pengguna"

                        val orgId = userDoc.getString("organisasiId") ?: ""

                        if (orgId.isNotEmpty()) {
                            // Fetch nama organisasi nyata dari collection "organizations"
                            db.collection("organizations").document(orgId).get()
                                .addOnSuccessListener { orgDoc ->
                                    notif.senderOrg = orgDoc?.getString("name")
                                        ?: orgDoc?.getString("nama")
                                        ?: userDoc.getString("organization")
                                        ?: ""
                                }
                                .addOnCompleteListener {
                                    // Decrement counter terlepas dari sukses/gagal
                                    pending--
                                    if (pending <= 0) displayList(list)
                                }
                        } else {
                            // Tidak ada organisasiId → gunakan string lama sebagai fallback
                            notif.senderOrg = userDoc.getString("organization")
                                ?: userDoc.getString("organisasi")
                                ?: ""
                            pending--
                            if (pending <= 0) displayList(list)
                        }
                    } else {
                        pending--
                        if (pending <= 0) displayList(list)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("NotificationActivity", "Gagal enrich sender ${notif.senderUid}: ${e.message}")
                    pending--
                    if (pending <= 0) displayList(list)
                }
        }
    }

    private fun displayList(list: List<Notification>) {
        binding.rvNotifications.adapter = NotificationAdapter(list) { notification ->
            if ((notification.type == "task" || notification.type == "comment") &&
                notification.itemId.isNotEmpty()
            ) {
                val intent = android.content.Intent(this, DetailTugasActivity::class.java)
                intent.putExtra("TASK_ID", notification.itemId)
                startActivity(intent)
            }
        }
    }
}
