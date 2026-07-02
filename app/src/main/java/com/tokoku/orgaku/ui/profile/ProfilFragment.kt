package com.tokoku.orgaku.ui.profile

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tokoku.orgaku.CustomScannerActivity
import com.tokoku.orgaku.DocumentOrgActivity
import com.tokoku.orgaku.EditProfileActivity
import com.tokoku.orgaku.LoginActivity
import com.tokoku.orgaku.MainActivity
import com.tokoku.orgaku.R
import com.tokoku.orgaku.SettingsActivity
import com.tokoku.orgaku.databinding.FragmentProfilBinding
import com.tokoku.orgaku.util.SessionManager

class ProfilFragment : Fragment() {

    private var _binding: FragmentProfilBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager

    // ─────────────────────────────────────────────────────────────────────
    // ZXing QR Scanner launcher — terdaftar sekali di Fragment, aman re-use
    // ─────────────────────────────────────────────────────────────────────
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val orgId = extractOrgIdFromUrl(result.contents)
            if (orgId.isNotEmpty()) {
                processJoinOrganization(orgId)
            } else {
                Toast.makeText(
                    requireContext(),
                    "QR Code tidak valid untuk Orgaku",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        fetchUserData()
    }

    // Dipanggil saat tab di-switch via Hide/Show.
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            fetchUserData()
        }
    }

    private fun fetchUserData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (isAdded && document != null && document.exists()) {
                    binding.shimmerView.stopShimmer()
                    binding.shimmerView.visibility = View.GONE
                    binding.mainContent.visibility = View.VISIBLE

                    val nama = document.getString("nama") ?: document.getString("name") ?: "-"
                    val email = document.getString("email") ?: auth.currentUser?.email ?: "-"
                    val nim = document.getString("nim")
                    val noHp = document.getString("no_hp") ?: document.getString("phone")
                    val orgId = document.getString("organisasiId")
                    val fallbackOrg = document.getString("organization")
                    val role = document.getString("role") ?: "anggota"
                    val avatarId = document.getString("avatar_id") ?: "avatar_1"
                    val createdAt = document.get("createdAt")

                    val displayNim = if (nim.isNullOrEmpty()) "-" else nim
                    val displayNoHp = if (noHp.isNullOrEmpty()) "-" else noHp
                    val fallbackDisplay = if (fallbackOrg.isNullOrEmpty()) "-" else fallbackOrg

                    val updateUI = { displayOrg: String ->
                        if (isAdded) {
                            val firstName = nama.trim().split(" ")[0]
                            sessionManager.saveUserProfile(firstName, role, avatarId, displayOrg)

                            binding.apply {
                                tvName.text = nama
                                tvNimLabel.text = "NIM: $displayNim"
                                tvFullNameDetail.text = nama
                                tvEmailDetail.text = email
                                tvNimDetail.text = displayNim
                                tvPhoneDetail.text = displayNoHp
                                tvOrgDetail.text = displayOrg
                                tvRoleDetail.text = role.uppercase()

                                (roleContainer.getChildAt(0) as? TextView)?.text = role.uppercase()
                                (roleContainer.getChildAt(1) as? TextView)?.text = displayOrg

                                if (avatarId.startsWith("http")) {
                                    Glide.with(this@ProfilFragment)
                                        .load(avatarId)
                                        .placeholder(R.drawable.avatar_1)
                                        .circleCrop()
                                        .into(ivProfile)
                                } else {
                                    val resId = resources.getIdentifier(
                                        avatarId,
                                        "drawable",
                                        requireContext().packageName
                                    )
                                    ivProfile.setImageResource(if (resId != 0) resId else R.drawable.avatar_1)
                                }

                                if (role.equals("ketua", ignoreCase = true)) {
                                    btnMoreOptions.visibility = View.GONE
                                } else {
                                    btnMoreOptions.visibility = View.VISIBLE
                                }

                                fetchDynamicStats(role, userId, createdAt)
                            }
                        }
                    }

                    if (!orgId.isNullOrEmpty()) {
                        db.collection("organizations").document(orgId).get()
                            .addOnSuccessListener { orgDoc ->
                                val realOrgName = if (orgDoc != null && orgDoc.exists()) {
                                    orgDoc.getString("name") ?: orgDoc.getString("nama") ?: "Nama Organisasi"
                                } else {
                                    fallbackDisplay
                                }
                                updateUI(realOrgName)
                            }
                            .addOnFailureListener {
                                updateUI(fallbackDisplay)
                            }
                    } else {
                        updateUI(fallbackDisplay)
                    }
                }
            }
            .addOnFailureListener {
                if (isAdded) showTopSnackbar("Gagal mengambil data profil")
            }
    }

    private fun fetchDynamicStats(role: String, userId: String, createdAt: Any?) {
        if (!isAdded) return

        val isKetua =
            role.equals("ketua", ignoreCase = true) || role.equals("admin", ignoreCase = true)

        binding.apply {
            if (isKetua) {
                tvLabel1.text = getString(R.string.stat_rapat_dibuat)
                tvLabel2.text = getString(R.string.stat_tugas)
                tvLabel3.text = "Masa Bakti"
            } else {
                tvLabel1.text = getString(R.string.stat_kehadiran)
                tvLabel2.text = getString(R.string.stat_tugas)
                tvLabel3.text = getString(R.string.stat_gabung)
            }
        }

        if (isKetua) {
            db.collection("meetings").whereEqualTo("userId", userId).get()
                .addOnSuccessListener {
                    if (isAdded) binding.tvAttendanceStat.text = it.size().toString()
                }
        } else {
            db.collection("meetings").get()
                .addOnSuccessListener { meetingDocs ->
                    if (!isAdded) return@addOnSuccessListener
                    val totalMeetings = meetingDocs.size()
                    if (totalMeetings == 0) {
                        binding.tvAttendanceStat.text = "0%"
                        return@addOnSuccessListener
                    }
                    var completedQueries = 0
                    var totalHadir = 0
                    for (meetingDoc in meetingDocs) {
                        db.collection("meetings").document(meetingDoc.id)
                            .collection("attendance").document(userId).get()
                            .addOnCompleteListener { task ->
                                if (!isAdded) return@addOnCompleteListener
                                if (task.isSuccessful && task.result?.exists() == true) totalHadir++
                                completedQueries++
                                if (completedQueries == totalMeetings) {
                                    val percentage = if (totalMeetings > 0)
                                        ((totalHadir.toFloat() / totalMeetings.toFloat()) * 100).toInt()
                                    else 0
                                    if (isAdded) binding.tvAttendanceStat.text = "$percentage%"
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    if (isAdded) binding.tvAttendanceStat.text = "0%"
                }
        }

        val taskQuery = if (isKetua) {
            db.collection("tasks").whereEqualTo("status", "done")
        } else {
            db.collection("tasks").whereEqualTo("assigneeId", userId).whereEqualTo("status", "done")
        }
        taskQuery.get().addOnSuccessListener {
            if (isAdded) binding.tvTasksStat.text = it.size().toString()
        }

        val joinedDate = when (createdAt) {
            is com.google.firebase.Timestamp -> createdAt.toDate()
            is String -> try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .parse(createdAt) ?: java.text.SimpleDateFormat(
                    "yyyy-MM-dd",
                    java.util.Locale.getDefault()
                ).parse(createdAt)
            } catch (_: Exception) {
                null
            }

            else -> null
        }

        if (joinedDate != null) {
            val diff = java.util.Date().time - joinedDate.time
            val days = diff / (1000 * 60 * 60 * 24)
            val months = days / 30
            binding.tvJoinedStat.text = when {
                months > 0 -> "$months bln"
                days > 0 -> "$days hr"
                else -> "Baru"
            }
        } else {
            binding.tvJoinedStat.text = "Baru"
        }
    }

    private fun setupActions() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.btnMoreOptions.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(requireContext(), view)
            popup.menuInflater.inflate(R.menu.menu_join_org, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_scan_qr -> {
                        launchQrScanner()
                        true
                    }

                    R.id.action_input_link -> {
                        showInputLinkDialog()
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.btnEditProfileTop.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.btnDocumentOrg.setOnClickListener {
            startActivity(Intent(requireContext(), DocumentOrgActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("LOGOUT_SUCCESS", true)
            startActivity(intent)
            requireActivity().finish()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ACTION 1: Input Link / Kode Manual
    // ─────────────────────────────────────────────────────────────────────
    private fun showInputLinkDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "https://orgaku.app/join/XYZ123 atau XYZ123"
            setPadding(48, 32, 48, 16)
            isSingleLine = true
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Masukkan Kode / Tautan")
            .setMessage("Tempel tautan undangan atau ketik ID organisasi secara langsung.")
            .setView(editText)
            .setPositiveButton("Bergabung") { _: DialogInterface, _ ->
                val input = editText.text.toString().trim()
                if (input.isEmpty()) {
                    Toast.makeText(requireContext(), "Input tidak boleh kosong", Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }
                val orgId = extractOrgIdFromUrl(input)
                if (orgId.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Kode atau tautan tidak valid",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                processJoinOrganization(orgId)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // ACTION 2: Scan QR Code via ZXing
    // ─────────────────────────────────────────────────────────────────────
    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("Arahkan kamera ke QR Code undangan organisasi")
            setBeepEnabled(true)
            setOrientationLocked(true) // Lock the orientation
            setCaptureActivity(CustomScannerActivity::class.java) // Force it to use the portrait custom scanner
            setDesiredBarcodeFormats(ScanOptions.QR_CODE) // Optimize for QR codes only
        }
        qrScanLauncher.launch(options)
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPER: Ekstrak orgId dari URL atau input murni
    //   https://orgaku.app/join/XYZ123 → "XYZ123"
    //   XYZ123                         → "XYZ123"
    // ─────────────────────────────────────────────────────────────────────
    private fun extractOrgIdFromUrl(input: String): String {
        return if (input.contains("orgaku.app/join/")) {
            input.substringAfterLast("/").trim()
        } else {
            input.trim()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CORE: Instant Join (Bypass Approval & Auto-Create Doc)
    // ─────────────────────────────────────────────────────────────────────
    private fun processJoinOrganization(orgId: String) {
        val uid = auth.currentUser?.uid ?: return
        showTopSnackbar("Sedang bergabung ke organisasi...")

        val userRef = db.collection("users").document(uid)
        val orgRef = db.collection("organizations").document(orgId)

        // Bikin struktur data untuk disuntik ke Firebase
        val orgData = hashMapOf(
            "members" to FieldValue.arrayUnion(uid)
        )

        // Pakai .set dengan SetOptions.merge() biar otomatis bikin dokumen kalau belum ada!
        orgRef.set(orgData, com.google.firebase.firestore.SetOptions.merge())
            .continueWithTask {
                // Setelah sukses suntik ke organisasi, baru update profil user
                userRef.update("organisasiId", orgId)
            }
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(
                    requireContext(),
                    "Berhasil bergabung dengan organisasi! \uD83C\uDF89",
                    Toast.LENGTH_LONG
                ).show()
                restartMainActivity()
            }
            .addOnFailureListener { e ->
                if (isAdded) showTopSnackbar("Gagal bergabung: ${e.message}")
            }
    }

    private fun restartMainActivity() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun showTopSnackbar(message: String) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        val snackView = snack.view
        val params = snackView.layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        } else if (params is CoordinatorLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        }
        snackView.setBackgroundResource(R.drawable.bg_snackbar)
        snackView.elevation = 10f
        snack.setTextColor(Color.WHITE)
        snack.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
