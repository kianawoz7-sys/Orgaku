package com.tokoku.orgaku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tokoku.orgaku.databinding.ActivityInviteMemberBinding

class InviteMemberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInviteMemberBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // URL invite yang akan di-generate
    private var inviteUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInviteMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Ambil org name dari Intent extra (dikirim dari Dashboard untuk hemat Firestore read)
        val organisasiFromIntent = intent.getStringExtra(EXTRA_ORGANISASI)
        val orgIdFromIntent = intent.getStringExtra(EXTRA_ORGANISASI_ID)

        if (!organisasiFromIntent.isNullOrEmpty() && !orgIdFromIntent.isNullOrEmpty()) {
            // Data sudah tersedia dari Intent — tidak perlu Firestore call
            setupInviteScreen(orgName = organisasiFromIntent, orgId = orgIdFromIntent)
        } else {
            // Fallback: fetch dari Firestore jika tidak ada Intent extra
            fetchOrgDataFromFirestore()
        }
    }

    private fun fetchOrgDataFromFirestore() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Sesi tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val orgName = doc.getString("organisasi") ?: doc.getString("organization") ?: "Organisasi"
                val orgId = doc.getString("organisasiId") ?: doc.getString("organizationId") ?: uid
                setupInviteScreen(orgName = orgName, orgId = orgId)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data organisasi", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Setup seluruh UI setelah data organisasi tersedia:
     * - Generate deep link URL
     * - Generate QR Code Bitmap dari URL
     * - Bind semua views
     * - Setup click listeners
     */
    private fun setupInviteScreen(orgName: String, orgId: String) {
        // Buat deep link format: https://orgaku.app/join/{orgId}
        inviteUrl = "https://orgaku.app/join/$orgId"

        // Bind text views
        binding.tvOrgName.text = orgName
        binding.tvInviteLink.text = "orgaku.app/join/$orgId"

        // Generate QR Code
        val qrBitmap = generateQrCode(inviteUrl)
        if (qrBitmap != null) {
            binding.ivQrCode.setImageBitmap(qrBitmap)
        }

        // Setup button click listeners
        binding.btnCopyLink.setOnClickListener { copyLinkToClipboard() }
        binding.btnShare.setOnClickListener { shareInviteLink(orgName) }
    }

    /**
     * Generate QR Code Bitmap menggunakan ZXing (sudah ada di project).
     * Ukuran: 512x512 px untuk ketajaman maksimal.
     */
    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copy invite URL ke clipboard sistem Android.
     */
    private fun copyLinkToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Orgaku Invite Link", inviteUrl)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Tautan undangan disalin! 📋", Toast.LENGTH_SHORT).show()
    }

    /**
     * Buka Android Share Sheet dengan pesan undangan yang sudah diformat.
     * Bisa langsung dikirim via WhatsApp, Telegram, Line, dll.
     */
    private fun shareInviteLink(orgName: String) {
        val shareMessage = "Halo! Kamu diundang untuk bergabung ke *$orgName* di aplikasi Orgaku.\n\n" +
                "📱 Klik link berikut untuk bergabung:\n$inviteUrl\n\n" +
                "Atau scan QR Code yang dikirimkan secara langsung. Sampai jumpa di sana! 🎉"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            putExtra(Intent.EXTRA_SUBJECT, "Undangan Bergabung ke $orgName - Orgaku")
        }

        startActivity(Intent.createChooser(shareIntent, "Bagikan undangan via..."))
    }

    companion object {
        const val EXTRA_ORGANISASI = "extra_organisasi"
        const val EXTRA_ORGANISASI_ID = "extra_organisasi_id"
    }
}
