package com.tokoku.orgaku.ui.meeting

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.tokoku.orgaku.AddMeetingActivity
import com.tokoku.orgaku.JadwalAdapter
import com.tokoku.orgaku.MeetingSchedule
import com.tokoku.orgaku.databinding.FragmentJadwalBinding
import com.tokoku.orgaku.util.SessionManager

class JadwalFragment : Fragment() {

    private var _binding: FragmentJadwalBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: JadwalAdapter
    private lateinit var sessionManager: SessionManager

    private var currentUserRole: String = "anggota"
    private var latestMeetingList = listOf<MeetingSchedule>()

    // GUARD: Listener dipasang hanya SEKALI. Hide/Show tidak akan re-attach.
    private var dataLoaded = false

    // Simpan referensi listener agar bisa di-detach saat onDestroyView()
    // dan mencegah memory leak / callback ke view yang sudah null.
    private var meetingsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJadwalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(requireContext())

        setupRecyclerView()

        if (!dataLoaded) {
            checkUserRole()
            // Step 1: Flash data dari cache lokal agar UI langsung terisi (anti-flicker)
            loadCachedMeetings()
            // Step 2: Pasang real-time listener — akan otomatis update RecyclerView
            // setiap kali ada perubahan di Firestore (tambah/hapus/edit meeting)
            attachMeetingsListener()
            dataLoaded = true
        } else {
            // Fragment di-show kembali dari hide — data & listener masih aktif,
            // cukup re-render dengan data terakhir yang ada di memori.
            if (latestMeetingList.isNotEmpty()) {
                updateUI(latestMeetingList)
            }
        }

        binding.fabAddMeeting.setOnClickListener {
            startActivity(Intent(requireContext(), AddMeetingActivity::class.java))
        }
    }

    // Dipanggil saat tab di-switch via Hide/Show.
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && dataLoaded && _binding != null) {
            if (latestMeetingList.isNotEmpty()) {
                updateUI(latestMeetingList)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // REAL-TIME LISTENER — dipasang sekali, auto-update setiap perubahan
    // ─────────────────────────────────────────────────────────────────────
    private fun attachMeetingsListener() {
        // Lepas listener lama jika ada (safety guard)
        meetingsListener?.remove()

        val uid = auth.currentUser?.uid ?: run {
            latestMeetingList = emptyList()
            updateUI(latestMeetingList)
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[attachMeetingsListener] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    latestMeetingList = emptyList()
                    updateUI(latestMeetingList)
                    return@addOnSuccessListener
                }

                meetingsListener = db.collection("meetings")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("date", Query.Direction.ASCENDING)
                    .orderBy("startTime", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshots, error ->
                        if (_binding == null) return@addSnapshotListener

                        if (error != null) {
                            Log.e("JadwalFragment", "Snapshot listener error: ${error.message}")
                            return@addSnapshotListener
                        }

                        if (snapshots == null) return@addSnapshotListener

                        val meetingList = snapshots.documents.mapNotNull { doc ->
                            MeetingSchedule(
                                id          = doc.id,
                                title       = doc.getString("title") ?: "",
                                description = doc.getString("description") ?: "",
                                date        = doc.getString("date") ?: "",
                                time        = "${doc.getString("startTime")} - ${doc.getString("endTime")}",
                                location    = doc.getString("location") ?: ""
                            )
                        }

                        latestMeetingList = meetingList
                        updateUI(meetingList)

                        Log.d("DEBUG_ORG", "[attachMeetingsListener] Real-time update: ${meetingList.size} meetings for org $organisasiId")
                    }
            }
            .addOnFailureListener {
                latestMeetingList = emptyList()
                updateUI(latestMeetingList)
            }
    }

    private fun loadCachedMeetings() {
        val uid = auth.currentUser?.uid ?: run {
            latestMeetingList = emptyList()
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.rvJadwal.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[loadCachedMeetings] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    latestMeetingList = emptyList()
                    binding.shimmerView.stopShimmer()
                    binding.shimmerView.visibility = View.GONE
                    binding.rvJadwal.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                db.collection("meetings")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("date", Query.Direction.ASCENDING)
                    .orderBy("startTime", Query.Direction.ASCENDING)
                    .get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        if (!snapshot.isEmpty) {
                            val meetingList = snapshot.documents.mapNotNull { doc ->
                                MeetingSchedule(
                                    id          = doc.id,
                                    title       = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    date        = doc.getString("date") ?: "",
                                    time        = "${doc.getString("startTime")} - ${doc.getString("endTime")}",
                                    location    = doc.getString("location") ?: ""
                                )
                            }
                            latestMeetingList = meetingList

                            // ANTI-FLICKER: Langsung tampil dari cache, shimmer dilewati
                            binding.shimmerView.stopShimmer()
                            binding.shimmerView.visibility = View.GONE
                            binding.rvJadwal.visibility = View.VISIBLE
                            updateUI(meetingList)
                        } else {
                            // Tidak ada cache → tampilkan shimmer sambil tunggu listener
                            binding.shimmerView.visibility = View.VISIBLE
                            binding.shimmerView.startShimmer()
                            binding.rvJadwal.visibility = View.GONE
                        }
                    }
                    .addOnFailureListener {
                        if (_binding != null) {
                            binding.shimmerView.visibility = View.VISIBLE
                            binding.shimmerView.startShimmer()
                        }
                    }
            }
            .addOnFailureListener {
                if (_binding != null) {
                    binding.shimmerView.visibility = View.VISIBLE
                    binding.shimmerView.startShimmer()
                }
            }
    }

    private fun setupRecyclerView() {
        adapter = JadwalAdapter(emptyList(), currentUserRole) { meeting ->
            deleteMeeting(meeting)
        }
        binding.rvJadwal.layoutManager = LinearLayoutManager(requireContext())
        binding.rvJadwal.adapter = adapter
    }

    private fun checkUserRole() {
        val user = auth.currentUser
        if (user == null) {
            binding.fabAddMeeting.visibility = View.GONE
            return
        }

        db.collection("users").document(user.uid).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (_binding == null) return@addOnSuccessListener
                val role = document.getString("role") ?: "anggota"
                currentUserRole = role
                Log.d("JadwalFragment", "Role updated to '$role'")

                if (role.equals("ketua", ignoreCase = true) ||
                    role.equals("admin", ignoreCase = true) ||
                    role.equals("chairman", ignoreCase = true)
                ) {
                    binding.fabAddMeeting.visibility = View.VISIBLE
                } else {
                    binding.fabAddMeeting.visibility = View.GONE
                }

                // Refresh adapter dengan role terbaru
                if (latestMeetingList.isNotEmpty()) {
                    updateUI(latestMeetingList)
                }
            }
            .addOnFailureListener {
                if (_binding != null) {
                    binding.fabAddMeeting.visibility = View.GONE
                }
            }
    }

    private fun updateUI(meetings: List<MeetingSchedule>) {
        if (_binding == null) return

        binding.shimmerView.stopShimmer()
        binding.shimmerView.visibility = View.GONE

        Log.d("JadwalFragment", "Updating UI: ${meetings.size} meetings, role: '$currentUserRole'")

        if (meetings.isEmpty()) {
            binding.rvJadwal.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.rvJadwal.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
            adapter.updateData(meetings, currentUserRole)
        }
    }

    private fun deleteMeeting(meeting: MeetingSchedule) {
        if (!(currentUserRole.equals("ketua", ignoreCase = true) ||
                    currentUserRole.equals("admin", ignoreCase = true) ||
                    currentUserRole.equals("chairman", ignoreCase = true))
        ) {
            Toast.makeText(
                requireContext(),
                "Anda tidak memiliki akses untuk menghapus jadwal",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah Anda yakin ingin menghapus jadwal rapat '${meeting.title}'?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("meetings").document(meeting.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(
                            requireContext(),
                            "Jadwal berhasil dihapus",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Tidak perlu manual refresh — snapshot listener otomatis update UI
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            "Gagal menghapus jadwal: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // CRITICAL: Detach Firestore listener saat view di-destroy
        // agar tidak ada callback ke _binding yang sudah null → mencegah crash & memory leak
        meetingsListener?.remove()
        meetingsListener = null
        _binding = null
    }
}
