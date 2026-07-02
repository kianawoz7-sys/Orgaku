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
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tokoku.orgaku.databinding.ActivityKetuaAbsensiBinding
import com.tokoku.orgaku.databinding.BottomSheetMeetingSelectionBinding
import java.io.File

class KetuaAbsensiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKetuaAbsensiBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var sourceIndex = 2 // Default to center

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKetuaAbsensiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceIndex = intent.getIntExtra("SOURCE_INDEX", 2)

        fetchProfileData()
        setupListeners()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_qr
        binding.bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_beranda -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_jadwal -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("START_TAB", R.id.nav_jadwal)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_qr -> true
                R.id.nav_tugas -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("START_TAB", R.id.nav_tugas)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                R.id.nav_profil -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("START_TAB", R.id.nav_profil)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun fetchProfileData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("nama") ?: "Chairman"
                    val role = document.getString("role") ?: "Ketua"
                    val avatarId = document.getString("avatar_id") ?: "avatar_1"

                    binding.tvName.text = name
                    binding.tvRole.text = role.uppercase()

                    if (avatarId.startsWith("http")) {
                        Glide.with(this).load(avatarId).circleCrop().into(binding.ivProfile)
                    } else {
                        val resId = resources.getIdentifier(avatarId, "drawable", packageName)
                        if (resId != 0) {
                            binding.ivProfile.setImageResource(resId)
                        } else {
                            binding.ivProfile.setImageResource(R.drawable.avatar_1)
                        }
                    }
                }
            }
    }

    private fun setupListeners() {
        binding.btnShowQr.setOnClickListener {
            showMeetingSelectionBottomSheet { meeting ->
                val intent = Intent(this, GenerateQRActivity::class.java)
                intent.putExtra("meetingId", meeting.id)
                startActivity(intent)
            }
        }

        binding.btnRekap.setOnClickListener {
            showMeetingSelectionBottomSheet { meeting ->
                val intent = Intent(this, RekapAbsensiActivity::class.java)
                intent.putExtra("meetingId", meeting.id)
                startActivity(intent)
            }
        }
    }

    private fun showMeetingSelectionBottomSheet(onSelected: (MeetingSchedule) -> Unit) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = BottomSheetMeetingSelectionBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.rvMeetings.layoutManager = LinearLayoutManager(this)
        dialogBinding.progressBar.visibility = View.VISIBLE

        db.collection("meetings")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                dialogBinding.progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    Toast.makeText(this, "Belum ada jadwal rapat", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@addOnSuccessListener
                }

                val meetingList = documents.map { doc ->
                    MeetingSchedule(
                        id = doc.id,
                        title = doc.getString("title") ?: "Rapat Tanpa Judul",
                        description = doc.getString("description") ?: "",
                        date = doc.getString("date") ?: "",
                        time = "${doc.getString("startTime")} - ${doc.getString("endTime")}",
                        location = doc.getString("location") ?: ""
                    )
                }

                dialogBinding.rvMeetings.adapter = MeetingSelectionAdapter(meetingList) { meeting ->
                    dialog.dismiss()
                    onSelected(meeting)
                }
            }
            .addOnFailureListener { e ->
                dialogBinding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Gagal memuat rapat: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

        dialog.show()
    }

    private fun generateAndShareCSV(meeting: MeetingSchedule) {
        Toast.makeText(this, "Menyiapkan laporan CSV...", Toast.LENGTH_SHORT).show()

        db.collection("users")
            .whereEqualTo("role", "anggota")
            .get()
            .addOnSuccessListener { usersSnapshot ->
                db.collection("meetings").document(meeting.id)
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
                            val fileName = "Rekap_Absensi_${meeting.title.replace(" ", "_")}.csv"
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
                            Log.e("KetuaAbsensi", "Error saving CSV", e)
                            Toast.makeText(this, "Gagal menyimpan file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data anggota", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (sourceIndex < 2) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
