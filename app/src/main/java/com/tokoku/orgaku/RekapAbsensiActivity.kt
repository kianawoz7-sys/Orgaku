package com.tokoku.orgaku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tokoku.orgaku.databinding.ActivityRekapAbsensiBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class RekapAbsensiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRekapAbsensiBinding
    private lateinit var adapter: ScannedMemberAdapter
    private val db = FirebaseFirestore.getInstance()
    private var meetingId: String? = null
    private var meetingTitle: String = "Rapat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRekapAbsensiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meetingId = intent.getStringExtra("meetingId")
        
        setupListeners()
        setupRecyclerView()

        if (meetingId == null) {
            // ANGGOTA FALLBACK: Fetch the latest meeting automatically
            fetchLatestMeeting()
        } else {
            fetchMeetingDetail()
            fetchAttendanceData()
        }
    }

    private fun fetchLatestMeeting() {
        db.collection("meetings")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    meetingId = doc.id
                    meetingTitle = doc.getString("title") ?: "Rapat Terbaru"
                    fetchAttendanceData()
                } else {
                    Toast.makeText(this, "Belum ada data rapat", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data rapat", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun fetchMeetingDetail() {
        meetingId?.let { id ->
            db.collection("meetings").document(id).get()
                .addOnSuccessListener { doc ->
                    meetingTitle = doc.getString("title") ?: "Rapat"
                }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnDownload.setOnClickListener {
            generateAndShareCSV()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScannedMemberAdapter(emptyList(), isReadOnly = true)
        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = adapter
    }

    private fun fetchAttendanceData() {
        val mid = meetingId ?: return

        // Step 1: Get the meeting's organisasiId so we scope the user fetch to this org only
        db.collection("meetings").document(mid).get()
            .addOnSuccessListener { meetingDoc ->
                val organisasiId = meetingDoc.getString("organisasiId") ?: ""

                if (organisasiId.isEmpty()) {
                    Toast.makeText(this, "Data organisasi rapat tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Step 2: Fetch ALL org members (no role filter — leader is included)
                db.collection("users")
                    .whereEqualTo("organisasiId", organisasiId)
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        // Step 3: Fetch attendance subcollection
                        db.collection("meetings").document(mid)
                            .collection("attendance")
                            .get()
                            .addOnSuccessListener { attendanceSnapshot ->
                                val presentData = attendanceSnapshot.documents.associateBy({ it.id }, {
                                    it.getTimestamp("timestamp")?.toDate()
                                })

                                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val fullMemberList = mutableListOf<ScannedMember>()
                                var presentCount = 0

                                for (userDoc in usersSnapshot.documents) {
                                    val uid = userDoc.id
                                    val name = userDoc.getString("nama") ?: "User"
                                    val nim = userDoc.getString("npm") ?: userDoc.getString("nim") ?: "000000"
                                    val avatar = userDoc.getString("avatar_id") ?: "avatar_1"

                                    val scanTimeDate = presentData[uid]
                                    val isPresent = scanTimeDate != null
                                    val status = if (isPresent) "HADIR" else "BELUM"
                                    val timeText = scanTimeDate?.let { sdf.format(it) } ?: "-"

                                    if (isPresent) presentCount++

                                    fullMemberList.add(ScannedMember(
                                        userId = uid,
                                        name = name,
                                        nim = nim,
                                        time = timeText,
                                        avatar = avatar,
                                        isPresent = isPresent,
                                        status = status
                                    ))
                                }

                                updateUI(fullMemberList, usersSnapshot.size(), presentCount)
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal memuat data anggota", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data rapat", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(members: List<ScannedMember>, total: Int, present: Int) {
        val absentCount = total - present
        val rate = if (total > 0) (present * 100 / total) else 0

        binding.tvTotalHadir.text = present.toString()
        binding.tvTotalAbsen.text = absentCount.toString()
        binding.tvAttendanceRate.text = "$rate%"
        
        // Update Circular Chart
        binding.chartAttendance.progress = rate
        binding.tvCircularPercent.text = "$rate%"

        if (members.isEmpty()) {
            binding.rvMembers.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvMembers.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
            
            adapter.updateData(members, readOnly = true)
        }
    }

    private fun generateAndShareCSV() {
        val mid = meetingId ?: return
        Toast.makeText(this, "Menyiapkan laporan CSV...", Toast.LENGTH_SHORT).show()

        // Step 1: Get the meeting's organisasiId
        db.collection("meetings").document(mid).get()
            .addOnSuccessListener { meetingDoc ->
                val organisasiId = meetingDoc.getString("organisasiId") ?: ""

                if (organisasiId.isEmpty()) {
                    Toast.makeText(this, "Data organisasi rapat tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Step 2: Fetch ALL org members (no role filter — leader is included)
                db.collection("users")
                    .whereEqualTo("organisasiId", organisasiId)
                    .get()
                    .addOnSuccessListener { usersSnapshot ->
                        // Step 3: Fetch attendance subcollection
                        db.collection("meetings").document(mid)
                            .collection("attendance")
                            .get()
                            .addOnSuccessListener { attendanceSnapshot ->
                                val presentUserIds = attendanceSnapshot.documents.map { it.id }.toSet()

                                val csvData = StringBuilder()
                                csvData.append("NIM,Nama Anggota,Status\n")

                                for (doc in usersSnapshot.documents) {
                                    val name = doc.getString("nama") ?: "User"
                                    val nim = doc.getString("npm") ?: doc.getString("nim") ?: "000000"
                                    val isPresent = presentUserIds.contains(doc.id)
                                    val status = if (isPresent) "HADIR" else "BELUM"

                                    csvData.append("$nim,$name,$status\n")
                                }

                                try {
                                    val fileName = "Rekap_Absensi_${meetingTitle.replace(" ", "_")}.csv"
                                    val file = File(cacheDir, fileName)
                                    file.writeText(csvData.toString())

                                    val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "Laporan Absensi")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(intent, "Bagikan Rekap Absensi"))
                                } catch (e: Exception) {
                                    Log.e("RekapAbsensi", "Error saving CSV", e)
                                    Toast.makeText(this, "Gagal menyimpan file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal memuat data anggota", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data rapat", Toast.LENGTH_SHORT).show()
            }
    }
}
