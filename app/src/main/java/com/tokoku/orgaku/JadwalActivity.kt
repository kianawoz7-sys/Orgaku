package com.tokoku.orgaku

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tokoku.orgaku.databinding.ActivityJadwalBinding

class JadwalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJadwalBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: JadwalAdapter
    private var currentUserRole: String = "anggota"
    private var latestMeetingList = listOf<MeetingSchedule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJadwalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkUserRole()
        fetchUpcomingMeetings()
        setupBottomNav()
    }

    private fun setupRecyclerView() {
        adapter = JadwalAdapter(emptyList(), currentUserRole) { meeting ->
            deleteMeeting(meeting)
        }
        binding.rvJadwal.layoutManager = LinearLayoutManager(this)
        binding.rvJadwal.adapter = adapter
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "anggota"
                currentUserRole = role
                Log.d("DEBUG_DELETE", "JadwalActivity: Role updated to '$role'. Refreshing UI...")

                // Show/hide FAB based on role
                if (role.equals("ketua", ignoreCase = true) || 
                    role.equals("admin", ignoreCase = true) ||
                    role.equals("chairman", ignoreCase = true)
                ) {
                    binding.fabAddMeeting.visibility = View.VISIBLE
                } else {
                    binding.fabAddMeeting.visibility = View.GONE
                }

                // FORCE REFRESH ADAPTER WITH NEW ROLE
                if (latestMeetingList.isNotEmpty()) {
                    updateUI(latestMeetingList)
                }
            }
    }

    private fun fetchUpcomingMeetings() {
        // 1. Fetch from Firestore & 2. Sorting by date and startTime
        db.collection("meetings")
            .orderBy("date", Query.Direction.ASCENDING)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("JadwalActivity", "Error fetching meetings: ${error.message}")
                    return@addSnapshotListener
                }

                // 3. Data Mapping
                val meetingList = mutableListOf<MeetingSchedule>()
                value?.forEach { doc ->
                    val meeting = MeetingSchedule(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        date = doc.getString("date") ?: "",
                        time = "${doc.getString("startTime")} - ${doc.getString("endTime")}",
                        location = doc.getString("location") ?: "",
                        startTime = doc.getString("startTime") ?: "",
                        endTime = doc.getString("endTime") ?: ""
                    )
                    meetingList.add(meeting)
                }

                // 4. Update UI
                latestMeetingList = meetingList
                updateUI(meetingList)
            }
    }

    private fun updateUI(meetings: List<MeetingSchedule>) {
        Log.d("DEBUG_RBAC", "JadwalActivity: Updating UI, Role: '$currentUserRole'")
        if (meetings.isEmpty()) {
            binding.rvJadwal.visibility = View.GONE
        } else {
            binding.rvJadwal.visibility = View.VISIBLE
            adapter.updateData(meetings, currentUserRole)
        }
    }

    private fun deleteMeeting(meeting: MeetingSchedule) {
        if (!(currentUserRole.equals("ketua", ignoreCase = true) || 
              currentUserRole.equals("admin", ignoreCase = true) ||
              currentUserRole.equals("chairman", ignoreCase = true))
        ) {
            Toast.makeText(this, "Anda tidak memiliki akses untuk menghapus jadwal", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah Anda yakin ingin menghapus jadwal rapat '${meeting.title}'?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("meetings").document(meeting.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Jadwal berhasil dihapus", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menghapus jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_jadwal
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_beranda -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.nav_jadwal -> true
                R.id.nav_tugas -> {
                    startActivity(Intent(this, TugasActivity::class.java))
                    finish()
                    true
                }

                R.id.nav_profil -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("START_TAB", R.id.nav_profil)
                    startActivity(intent)
                    finish()
                    true
                }

                else -> false
            }
        }

        binding.fabAddMeeting.setOnClickListener {
            startActivity(Intent(this, AddMeetingActivity::class.java))
        }

        binding.fabQr.setOnClickListener {
            startActivity(Intent(this, AbsensiHomeActivity::class.java))
        }
    }

}
