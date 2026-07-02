package com.tokoku.orgaku

import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.tokoku.orgaku.databinding.ActivitySimplifiedQrDisplayBinding
import java.util.Locale

class SimplifiedQRDisplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimplifiedQrDisplayBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var countDownTimer: CountDownTimer? = null
    private var meetingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimplifiedQrDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        meetingId = intent.getStringExtra("meetingId")
        if (meetingId == null) {
            Toast.makeText(this, "ID Rapat tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchChairmanProfile()
        startRotatingToken()

        binding.btnRotate.setOnClickListener {
            startRotatingToken()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun fetchChairmanProfile() {
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

    private fun startRotatingToken() {
        countDownTimer?.cancel()
        generateAndUpdateToken()

        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                binding.tvTimer.text = String.format(Locale.getDefault(), "%ds", secondsRemaining)
            }

            override fun onFinish() {
                startRotatingToken()
            }
        }.start()
    }

    private fun generateAndUpdateToken() {
        val currentToken = generateRandomAlphanumeric(10)
        
        // Update the TextView with the new token
        binding.tvManualToken.text = "# $currentToken"
        
        meetingId?.let { id ->
            db.collection("meetings").document(id)
                .update("current_token", currentToken)
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal sinkronisasi token", Toast.LENGTH_SHORT).show()
                }
        }

        generateQRCode(currentToken)
    }

    private fun generateRandomAlphanumeric(length: Int): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { charset.random() }
            .joinToString("")
    }

    private fun generateQRCode(content: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 800, 800)
            binding.ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
