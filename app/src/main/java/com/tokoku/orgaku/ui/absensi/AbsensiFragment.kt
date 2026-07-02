package com.tokoku.orgaku.ui.absensi

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tokoku.orgaku.*
import com.tokoku.orgaku.databinding.ActivityAbsensiHomeBinding
import com.tokoku.orgaku.databinding.BottomSheetMeetingSelectionBinding
import com.tokoku.orgaku.util.SessionManager

class AbsensiFragment : Fragment() {

    private var _binding: ActivityAbsensiHomeBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager

    // GUARD: Fetch profil hanya sekali, setup role tidak perlu diulang
    private var dataLoaded = false

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Scan dibatalkan", Toast.LENGTH_SHORT).show()
        } else {
            validateAndProcessAttendance(result.contents)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityAbsensiHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        // Hide internal bottom navigation and FAB as it's already in MainActivity
        binding.bottomNav.visibility = View.GONE
        binding.fabQr.visibility = View.GONE

        if (!dataLoaded) {
            // Single async fetch drives everything: profile, role UI, and listeners
            fetchProfileAndSetup()
            dataLoaded = true
        }
    }

    // Dipanggil saat tab di-switch via Hide/Show.
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // When coming back to this tab, re-run the full async setup
        // in case profile/role changed since last visit.
        if (!hidden && _binding != null) {
            fetchProfileAndSetup()
        }
    }

    /**
     * Single async entry point: fetches the user doc once, then drives
     * profile display + role-based UI + button listeners from the live data.
     */
    private fun fetchProfileAndSetup() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (_binding == null) return@addOnSuccessListener
                if (document == null || !document.exists()) return@addOnSuccessListener

                // ── Profile ───────────────────────────────────────────────────
                val name = document.getString("nama") ?: "User"
                val avatarId = document.getString("avatar_id") ?: "avatar_1"
                val nim = document.getString("npm") ?: document.getString("nim") ?: "2023XXXX"
                val role = (document.getString("role") ?: "anggota").lowercase()
                val organisasiId = document.getString("organisasiId") ?: ""

                binding.tvUserName.text = name
                binding.tvUserId.text = nim

                if (avatarId.startsWith("http")) {
                    Glide.with(this).load(avatarId).circleCrop().into(binding.ivUser)
                } else {
                    val resId = resources.getIdentifier(avatarId, "drawable", requireContext().packageName)
                    binding.ivUser.setImageResource(if (resId != 0) resId else R.drawable.avatar_1)
                }

                // ── Role-based UI ─────────────────────────────────────────────
                val isLeader = role == "ketua" || role == "admin" || role == "chairman"
                if (isLeader) {
                    binding.layoutMemberActions.visibility = View.GONE
                    binding.layoutLeaderActions.visibility = View.VISIBLE
                    binding.badgeRole.text = role.uppercase()
                } else {
                    binding.layoutMemberActions.visibility = View.VISIBLE
                    binding.layoutLeaderActions.visibility = View.GONE
                    binding.badgeRole.text = "Anggota"
                }

                // ── Listeners ─────────────────────────────────────────────────
                if (!isLeader) {
                    // --- ANGGOTA (MEMBER) ACTIONS ---
                    binding.btnScanQr.setOnClickListener {
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        options.setPrompt("Arahkan kamera ke QR Code Ketua")
                        options.setCameraId(0)
                        options.setBeepEnabled(true)
                        options.setBarcodeImageEnabled(true)
                        options.setOrientationLocked(false)
                        options.setCaptureActivity(CustomScannerActivity::class.java)
                        barcodeLauncher.launch(options)
                    }

                    binding.btnHistory.setOnClickListener {
                        startActivity(Intent(requireContext(), RiwayatAbsensiActivity::class.java))
                    }
                } else {
                    // --- KETUA (LEADER) ACTIONS ---
                    binding.btnShowQr.setOnClickListener {
                        showMeetingSelectionBottomSheet(organisasiId) { meeting ->
                            val intent = Intent(requireContext(), GenerateQRActivity::class.java)
                            intent.putExtra("meetingId", meeting.id)
                            startActivity(intent)
                        }
                    }

                    binding.btnRecap.setOnClickListener {
                        showMeetingSelectionBottomSheet(organisasiId) { meeting ->
                            val intent = Intent(requireContext(), RekapAbsensiActivity::class.java)
                            intent.putExtra("meetingId", meeting.id)
                            startActivity(intent)
                        }
                    }
                }
            }
    }

    private fun validateAndProcessAttendance(scannedToken: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("meetings")
            .whereEqualTo("current_token", scannedToken)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                if (!documents.isEmpty) {
                    val meetingDoc = documents.documents[0]
                    val meetingId = meetingDoc.id
                    
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { userDoc ->
                            if (_binding == null) return@addOnSuccessListener
                            val nama = userDoc.getString("nama") ?: userDoc.getString("name") ?: "Anggota"
                            
                            val attendanceData = hashMapOf(
                                "userId" to userId,
                                "nama" to nama,
                                "timestamp" to FieldValue.serverTimestamp(),
                                "status" to "Hadir"
                            )

                            db.collection("meetings").document(meetingId)
                                .collection("attendance").document(userId)
                                .set(attendanceData)
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Berhasil Absen!", Toast.LENGTH_LONG).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(requireContext(), "Gagal mencatat absen: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                } else {
                    Toast.makeText(requireContext(), "QR Code Tidak Valid! Silakan scan ulang layar Ketua.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal memproses absensi", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showMeetingSelectionBottomSheet(organisasiId: String, onSelected: (MeetingSchedule) -> Unit) {
        if (organisasiId.isBlank() || organisasiId == "-") {
            Toast.makeText(
                requireContext(),
                "Anda belum bergabung ke organisasi manapun.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = BottomSheetMeetingSelectionBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val adapter = MeetingSelectionAdapter(emptyList()) { meeting ->
            dialog.dismiss()
            onSelected(meeting)
        }
        dialogBinding.rvMeetings.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvMeetings.adapter = adapter
        dialogBinding.progressBar.visibility = View.VISIBLE

        // Query meetings scoped to this org
        db.collection("meetings")
            .whereEqualTo("organisasiId", organisasiId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                dialogBinding.progressBar.visibility = View.GONE
                if (documents.isEmpty) {
                    Toast.makeText(requireContext(), "Belum ada jadwal rapat", Toast.LENGTH_SHORT).show()
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

                adapter.updateData(meetingList)
            }
            .addOnFailureListener { e ->
                dialogBinding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat rapat: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
