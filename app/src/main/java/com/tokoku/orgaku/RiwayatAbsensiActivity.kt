package com.tokoku.orgaku

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tokoku.orgaku.databinding.ActivityRiwayatAbsensiBinding
import com.tokoku.orgaku.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Locale

class RiwayatAbsensiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiwayatAbsensiBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatAbsensiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        sessionManager = SessionManager(this)

        setupRecyclerView()
        fetchAttendanceHistory()
    }

    private fun setupRecyclerView() {
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
    }

    private fun fetchAttendanceHistory() {
        val userId = auth.currentUser?.uid ?: return

        // Coba ambil dari cache lokal terlebih dahulu (cepat)
        val cachedOrgId = sessionManager.getUserOrgId()

        if (!cachedOrgId.isNullOrBlank() && cachedOrgId != "-") {
            // Cache tersedia → langsung fetch
            startLoading()
            fetchHistoryForOrg(userId, cachedOrgId)
        } else {
            // Cache kosong (user login sebelum fix) → fallback ke Firestore
            startLoading()
            db.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val orgId = userDoc.getString("organisasiId") ?: ""
                    if (orgId.isBlank() || orgId == "-") {
                        // Benar-benar belum bergabung ke organisasi
                        updateStats(0, 0)
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvHistory.adapter = null
                        stopLoading()
                    } else {
                        // Simpan ke cache agar next time tidak perlu ke Firestore
                        val name = sessionManager.getUserName()
                        val role = sessionManager.getUserRole()
                        val photo = sessionManager.getUserPhoto()
                        val org = sessionManager.getUserOrg()
                        sessionManager.saveUserProfile(name, role, photo, org, orgId)

                        fetchHistoryForOrg(userId, orgId)
                    }
                }
                .addOnFailureListener { e ->
                    stopLoading()
                    Toast.makeText(this, "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchHistoryForOrg(userId: String, organisasiId: String) {
        // STRICT QUERY: only fetch meetings that belong to this user's organisation.
        db.collection("meetings")
            .whereEqualTo("organisasiId", organisasiId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { meetingDocs ->
                val tasks = meetingDocs.map { meetingDoc ->
                    db.collection("meetings").document(meetingDoc.id)
                        .collection("attendance").document(userId)
                        .get()
                        .continueWith { task ->
                            val attendanceDoc = task.result
                            val isPresent = attendanceDoc?.exists() == true
                            val title = meetingDoc.getString("title") ?: "Rapat"
                            val date = meetingDoc.getString("date") ?: ""
                            val time = meetingDoc.getString("startTime") ?: ""
                            val status = if (isPresent) "Hadir" else "Tidak Hadir"
                            
                            var scanTime: String? = null
                            if (isPresent) {
                                val timestamp = attendanceDoc?.getTimestamp("timestamp")
                                scanTime = timestamp?.let { formatTimestamp(it) }
                            }
                            
                            AttendanceHistory(title, date, time, status, scanTime)
                        }
                }

                Tasks.whenAllSuccess<AttendanceHistory>(tasks)
                    .addOnSuccessListener { results ->
                        if (results.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            updateStats(0, 0)
                        } else {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.rvHistory.adapter = AttendanceHistoryAdapter(results)
                            
                            val hadir = results.count { it.status == "Hadir" }
                            val total = results.size
                            updateStats(hadir, total)
                        }
                        stopLoading()
                    }
                    .addOnFailureListener { e ->
                        stopLoading()
                        Log.e("RiwayatAbsensi", "Error fetching attendance: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                stopLoading()
                Toast.makeText(this, "Gagal memuat riwayat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startLoading() {
        binding.shimmerView.alpha = 1f
        binding.shimmerView.visibility = View.VISIBLE
        binding.shimmerView.startShimmer()
        binding.mainContentContainer.visibility = View.GONE
    }

    private fun stopLoading() {
        if (binding.shimmerView.visibility == View.VISIBLE) {
            binding.shimmerView.stopShimmer()

            binding.mainContentContainer.alpha = 0f
            binding.mainContentContainer.visibility = View.VISIBLE

            val fadeOutSkeleton = ObjectAnimator.ofFloat(binding.shimmerView, "alpha", 1f, 0f)
                .apply { duration = 250 }
            val fadeInContent = ObjectAnimator.ofFloat(binding.mainContentContainer, "alpha", 0f, 1f)
                .apply { duration = 300 }

            AnimatorSet().apply {
                playTogether(fadeOutSkeleton, fadeInContent)
                start()
            }

            binding.shimmerView.visibility = View.GONE
        } else {
            binding.mainContentContainer.visibility = View.VISIBLE
        }
    }

    private fun formatTimestamp(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("HH.mm 'WIB'", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }

    private fun updateStats(hadir: Int, total: Int) {
        val persen = if (total > 0) (hadir * 100) / total else 0
        val absen = total - hadir
        
        binding.tvPercent.text = "$persen%"
        binding.tvStats.text = "$hadir Hadir | $absen Absen"
        binding.progressIndicator.progress = persen
    }
}
