package com.tokoku.orgaku

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tokoku.orgaku.databinding.ActivityQrGenerateBinding
import java.util.Date

class GenerateQRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrGenerateBinding
    private val db = FirebaseFirestore.getInstance()
    private var meetingId: String? = null
    private var attendanceListener: ListenerRegistration? = null
    
    private var allAnggota = mutableListOf<ScannedMember>()
    private var presentUserIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meetingId = intent.getStringExtra("meetingId")
        if (meetingId == null) {
            Toast.makeText(this, "ID Rapat tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        
        binding.btnShowQR.setOnClickListener {
            val intent = Intent(this, SimplifiedQRDisplayActivity::class.java)
            intent.putExtra("meetingId", meetingId)
            startActivity(intent)
        }

        fetchAllAnggotaThenListen()
    }

    private fun fetchAllAnggotaThenListen() {
        val id = meetingId ?: return

        // 1. Fetch the meeting first to get its organisasiId
        db.collection("meetings").document(id).get()
            .addOnSuccessListener { meetingDoc ->
                val organisasiId = meetingDoc.getString("organisasiId")
                if (organisasiId.isNullOrEmpty()) {
                    Toast.makeText(this, "Data rapat tidak lengkap", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // 2. Fetch ALL members scoped to this organisation only
                db.collection("users")
                    .whereEqualTo("organisasiId", organisasiId)
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        allAnggota = usersSnapshot.documents.map { doc ->
                            ScannedMember(
                                userId = doc.id,
                                name = doc.getString("nama") ?: "User",
                                nim = doc.getString("npm") ?: doc.getString("nim") ?: "000000",
                                avatar = doc.getString("avatar_id") ?: "avatar_1",
                                time = "-",
                                isPresent = false,
                                status = "BELUM"
                            )
                        }.toMutableList()

                        // 3. Start listening to attendance
                        listenToAttendance()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal mengambil daftar anggota", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data rapat", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun listenToAttendance() {
        val id = meetingId ?: return
        
        attendanceListener = db.collection("meetings").document(id)
            .collection("attendance")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("GenerateQRActivity", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    presentUserIds = snapshot.documents.mapNotNull { it.id }.toMutableSet()
                    updateDashboardUI(snapshot.size())
                }
            }
    }

    private fun updateDashboardUI(presentCount: Int) {
        val totalCount = allAnggota.size
        val belumCount = totalCount - presentCount
        val rate = if (totalCount > 0) (presentCount * 100 / totalCount) else 0

        binding.tvTotalHadir.text = presentCount.toString()
        binding.tvTotalBelum.text = belumCount.toString()
        binding.tvAttendanceRate.text = "$rate%"
        binding.tvScanTitle.text = "Daftar Anggota ($totalCount)"
        
        val mergedList = allAnggota.map { member ->
            if (presentUserIds.contains(member.userId)) {
                member.copy(isPresent = true, status = "HADIR")
            } else {
                member.copy(isPresent = false, status = "BELUM")
            }
        }.sortedBy { !it.isPresent }

        binding.rvScannedMembers.adapter = ScannedMemberAdapter(
            mergedList,
            onManualHadirClick = { member -> markAttendanceManual(member) },
            onBatalkanClick = { member -> batalkanAttendance(member) }
        )
    }

    private fun markAttendanceManual(member: ScannedMember) {
        val id = meetingId ?: return
        val attendanceData = hashMapOf(
            "userId" to member.userId,
            "nama" to member.name,
            "timestamp" to Date(),
            "status" to "Hadir (Manual)"
        )

        db.collection("meetings").document(id)
            .collection("attendance").document(member.userId)
            .set(attendanceData)
            .addOnSuccessListener {
                Toast.makeText(this, "Berhasil menambahkan ${member.name} secara manual", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menambahkan absensi manual", Toast.LENGTH_SHORT).show()
            }
    }

    private fun batalkanAttendance(member: ScannedMember) {
        val id = meetingId ?: return
        
        // Task 2: Delete user document from attendance subcollection
        db.collection("meetings").document(id)
            .collection("attendance").document(member.userId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Kehadiran ${member.name} dibatalkan", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal membatalkan kehadiran", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        binding.rvScannedMembers.layoutManager = LinearLayoutManager(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        attendanceListener?.remove()
    }
}
