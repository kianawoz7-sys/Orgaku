package com.tokoku.orgaku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tokoku.orgaku.databinding.ActivityDetailMeetingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class DetailMeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailMeetingBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailMeetingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val meetingId = intent.getStringExtra("EXTRA_MEETING_ID")
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "No Title"
        val dateStr = intent.getStringExtra("EXTRA_DATE") ?: ""
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "Tidak ada deskripsi rapat."
        val location = intent.getStringExtra("EXTRA_LOCATION") ?: "-"
        
        // Time logic: Combine startTime and endTime if available separately, 
        // or check if it's already combined in intent
        val startTime = intent.getStringExtra("EXTRA_START_TIME") ?: ""
        val endTime = intent.getStringExtra("EXTRA_END_TIME") ?: ""
        val fullTime = if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            "$startTime - $endTime WIB"
        } else {
            intent.getStringExtra("EXTRA_TIME") ?: "Jam belum ditentukan"
        }

        binding.apply {
            tvTitle.text = title
            tvFullTime.text = fullTime
            tvDesc.text = if (desc.isBlank() || desc == "p") "Tidak ada deskripsi rapat." else desc
            tvLocation.text = location

            // Date Parsing
            if (dateStr.isNotEmpty()) {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id"))
                    val date = inputFormat.parse(dateStr)
                    tvFullDate.text = if (date != null) outputFormat.format(date) else dateStr
                } catch (e: Exception) {
                    tvFullDate.text = dateStr
                }
            } else {
                tvFullDate.text = "Tanggal belum ditentukan"
            }

            btnBack.setOnClickListener {
                finish()
            }

            layoutLocation.setOnClickListener {
                if (location != "-" && location.isNotEmpty()) {
                    openGoogleMaps(location)
                }
            }

            btnOpenAbsensi.setOnClickListener {
                val intent = Intent(this@DetailMeetingActivity, GenerateQRActivity::class.java)
                intent.putExtra("meetingId", meetingId)
                startActivity(intent)
            }
        }

        checkUserRole()
    }

    private fun openGoogleMaps(location: String) {
        val locationQuery = Uri.encode(location)
        val mapUri = Uri.parse("geo:0,0?q=$locationQuery")
        val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val webUri = Uri.parse("https://maps.google.com/?q=$locationQuery")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role") ?: "anggota"
                    
                    if (role.equals("ketua", ignoreCase = true) || 
                        role.equals("admin", ignoreCase = true) ||
                        role.equals("chairman", ignoreCase = true)) {
                        binding.cardAttendanceControl.visibility = View.VISIBLE
                    } else {
                        binding.cardAttendanceControl.visibility = View.GONE
                    }
                }
            }
            .addOnFailureListener {
                binding.cardAttendanceControl.visibility = View.GONE
            }
    }
}
