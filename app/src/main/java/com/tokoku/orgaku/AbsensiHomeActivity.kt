package com.tokoku.orgaku

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tokoku.orgaku.databinding.ActivityAbsensiHomeBinding

class AbsensiHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAbsensiHomeBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var sourceIndex = 2 // Default to center

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan dibatalkan", Toast.LENGTH_SHORT).show()
        } else {
            val scannedToken = result.contents
            validateAndProcessAttendance(scannedToken)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAbsensiHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceIndex = intent.getIntExtra("SOURCE_INDEX", 2)

        val role = intent.getStringExtra("ROLE") ?: "MEMBER"
        setupUI(role)
        setupBottomNav()
    }

    private fun validateAndProcessAttendance(scannedToken: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("meetings")
            .whereEqualTo("current_token", scannedToken)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val meetingDoc = documents.documents[0]
                    val meetingId = meetingDoc.id
                    
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { userDoc ->
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
                                    Toast.makeText(this, "Berhasil Absen!", Toast.LENGTH_LONG).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Gagal mencatat absen: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                } else {
                    Toast.makeText(this, "QR Code Tidak Valid atau Sudah Kadaluarsa! Silakan scan ulang layar Ketua.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Kesalahan sistem: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupUI(role: String) {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val realName = document.getString("nama") ?: document.getString("name") ?: "User"
                    val nim = document.getString("nim") ?: document.getString("userId") ?: "No NIM"
                    val avatarId = document.getString("avatar_id") ?: "avatar_1"

                    binding.tvUserName.text = realName
                    binding.tvUserId.text = nim

                    if (avatarId.startsWith("http")) {
                        Glide.with(this@AbsensiHomeActivity)
                            .load(avatarId)
                            .placeholder(R.drawable.avatar_1)
                            .circleCrop()
                            .into(binding.ivUser)
                    } else {
                        val resId = resources.getIdentifier(avatarId, "drawable", packageName)
                        if (resId != 0) {
                            binding.ivUser.setImageResource(resId)
                        } else {
                            binding.ivUser.setImageResource(R.drawable.avatar_1)
                        }
                    }
                }
            }

        if (role == "LEADER") {
            binding.btnShowQr.setOnClickListener {
                startActivity(Intent(this, GenerateQRActivity::class.java))
            }
            binding.btnRecap.setOnClickListener {
                startActivity(Intent(this, RekapAbsensiActivity::class.java))
            }
        } else {
            binding.badgeRole.text = "Anggota"
            binding.layoutMemberActions.visibility = View.VISIBLE
            binding.layoutLeaderActions.visibility = View.GONE
            
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
                startActivity(Intent(this, RiwayatAbsensiActivity::class.java))
            }
        }
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

    override fun onBackPressed() {
        super.onBackPressed()
        if (sourceIndex < 2) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
