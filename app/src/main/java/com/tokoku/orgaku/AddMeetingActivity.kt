package com.tokoku.orgaku

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tokoku.orgaku.databinding.ActivityAddMeetingBinding
import java.text.SimpleDateFormat
import java.util.*

class AddMeetingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMeetingBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMeetingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPickers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveMeeting()
        }
    }

    private fun setupPickers() {
        // Date Picker
        binding.edtDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                binding.edtDate.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Start Time Picker
        binding.edtTimeStart.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                val time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                binding.edtTimeStart.setText(time)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        // End Time Picker
        binding.edtTimeEnd.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                val time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                binding.edtTimeEnd.setText(time)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }
    }

    private fun saveMeeting() {
        val title = binding.edtTitle.text.toString().trim()
        val date = binding.edtDate.text.toString().trim()
        val startTime = binding.edtTimeStart.text.toString().trim()
        val endTime = binding.edtTimeEnd.text.toString().trim()
        val location = binding.edtLocation.text.toString().trim()
        val description = binding.edtDesc.text.toString().trim()
        val userId = auth.currentUser?.uid ?: return

        if (title.isEmpty() || date.isEmpty() || startTime.isEmpty()) {
            Toast.makeText(this, "Judul, Tanggal, dan Waktu wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button immediately to prevent double-tap while we fetch org data
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Menyimpan..."

        // Fetch the real organisasiId (master key) from Firestore — never use the name from SessionManager
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val organisasiId = userDoc.getString("organisasiId") ?: ""
                val organisasiName = userDoc.getString("organisasi")
                    ?: userDoc.getString("organization") ?: ""

                if (organisasiId.isEmpty()) {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Simpan Jadwal"
                    Toast.makeText(this, "Gagal: Anda belum terdaftar di organisasi manapun", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                android.util.Log.d("DEBUG_ORG", "[AddMeeting] Saving with organisasiId: $organisasiId")

                val meetingData = hashMapOf(
                    "title"       to title,
                    "date"        to date,
                    "startTime"   to startTime,
                    "endTime"     to endTime,
                    "location"    to location,
                    "description" to description,
                    "reminder"    to "15 minutes before",
                    "userId"      to userId,
                    "organisasiId" to organisasiId,      // ← master key for fragment queries
                    "organisasi"   to organisasiName,    // ← display name (legacy compat)
                    "createdAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                db.collection("meetings")
                    .add(meetingData)
                    .addOnSuccessListener { docRef ->
                        notifyUsers(title, docRef.id)
                        Toast.makeText(this, "Rapat berhasil dijadwalkan", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        binding.btnSave.isEnabled = true
                        binding.btnSave.text = "Simpan Jadwal"
                        Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Simpan Jadwal"
                Toast.makeText(this, "Gagal memuat data organisasi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun notifyUsers(meetingTitle: String, meetingId: String) {
        OneSignalHelper.sendNotification(
            "Rapat Baru: $meetingTitle",
            "Cek aplikasi Orgaku sekarang!",
            meetingId,
            "meeting"
        )
    }
}
