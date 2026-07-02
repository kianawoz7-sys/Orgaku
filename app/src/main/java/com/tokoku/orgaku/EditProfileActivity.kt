package com.tokoku.orgaku

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tokoku.orgaku.databinding.ActivityEditProfileBinding
import com.tokoku.orgaku.util.SessionManager

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager
    private var selectedAvatarId: String = "avatar_1"

    // Disimpan saat fetchCurrentData() agar saveChanges() tidak perlu re-fetch Firestore
    private var userRole: String = "anggota"
    private var organisasiId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionManager = SessionManager(this)

        fetchCurrentData()
        setupClickListeners()
    }

    // ─────────────────────────────────────────────────────────────────────
    // FETCH: Load data user dari Firestore dan bind ke UI
    // ─────────────────────────────────────────────────────────────────────
    private fun fetchCurrentData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Simpan ke class-level variable agar saveChanges() bisa pakai tanpa re-fetch
                    userRole = document.getString("role") ?: "anggota"
                    organisasiId = document.getString("organisasiId") ?: ""

                    binding.apply {
                        edtName.setText(document.getString("nama") ?: document.getString("name"))
                        edtEmail.setText(document.getString("email") ?: auth.currentUser?.email)
                        edtNim.setText(document.getString("nim"))
                        edtPhone.setText(document.getString("no_hp") ?: document.getString("phone"))

                        if (userRole.equals("anggota", ignoreCase = true)) {
                            // ── ANGGOTA: Org field di-lock, tidak bisa diedit ──
                            edtOrganization.isFocusable = false
                            edtOrganization.isFocusableInTouchMode = false
                            edtOrganization.isClickable = true
                            edtOrganization.alpha = 0.5f
                            edtOrganization.setOnClickListener {
                                Toast.makeText(
                                    this@EditProfileActivity,
                                    "Hanya Ketua yang dapat mengubah nama organisasi.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            // Tampilkan nama org dari collection "organizations"
                            if (organisasiId.isNotEmpty()) {
                                db.collection("organizations").document(organisasiId).get()
                                    .addOnSuccessListener { orgDoc ->
                                        val realOrgName = orgDoc?.getString("name")
                                            ?: orgDoc?.getString("nama")
                                            ?: ""
                                        edtOrganization.setText(realOrgName)
                                    }
                                    .addOnFailureListener { edtOrganization.setText("") }
                            } else {
                                edtOrganization.setText("")
                            }

                        } else {
                            // ── KETUA: Org field aktif, bisa diedit ──
                            edtOrganization.isFocusable = true
                            edtOrganization.isFocusableInTouchMode = true
                            edtOrganization.isClickable = true
                            edtOrganization.alpha = 1.0f
                            edtOrganization.setOnClickListener(null)
                            edtOrganization.setText(
                                document.getString("organization") ?: document.getString("organisasi") ?: ""
                            )
                        }

                        val avatarId = document.getString("avatar_id") ?: "avatar_1"
                        updateAvatarUI(avatarId)
                    }
                }
            }
            .addOnFailureListener {
                showTopSnackbar(getString(R.string.error_fetch_failed))
            }
    }

    private fun updateAvatarUI(avatarId: String) {
        selectedAvatarId = avatarId
        val resId = resources.getIdentifier(avatarId, "drawable", packageName)
        binding.ivProfile.setImageResource(if (resId != 0) resId else R.drawable.avatar_1)
    }

    // ─────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener { saveChanges() }
        binding.btnBack.setOnClickListener { finish() }

        val avatarClickAction = View.OnClickListener { showAvatarSelectionSheet() }
        binding.ivProfile.setOnClickListener(avatarClickAction)
        binding.btnChangeAvatar.setOnClickListener(avatarClickAction)
    }

    private fun showAvatarSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_avatar_selection, null)
        val rvAvatars = view.findViewById<RecyclerView>(R.id.rv_avatars)

        val avatars = (1..10).map { "avatar_$it" }
        rvAvatars.layoutManager = GridLayoutManager(this, 3)
        rvAvatars.adapter = AvatarAdapter(avatars) { avatarId ->
            updateAvatarUI(avatarId)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // SAVE: Branching berdasarkan role agar field sensitif TIDAK PERNAH terhapus
    // ─────────────────────────────────────────────────────────────────────
    private fun saveChanges() {
        val userId = auth.currentUser?.uid ?: return
        val nama = binding.edtName.text.toString().trim()
        val nim = binding.edtNim.text.toString().trim()
        val noHp = binding.edtPhone.text.toString().trim()
        val organization = binding.edtOrganization.text.toString().trim()

        if (nama.isEmpty()) {
            showTopSnackbar(getString(R.string.error_name_empty))
            return
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = getString(R.string.saving)

        val isKetua = userRole.equals("ketua", ignoreCase = true) ||
                      userRole.equals("admin", ignoreCase = true)

        if (isKetua && organisasiId.isNotEmpty()) {
            // ─────────────────────────────────────────────────────────────
            // KETUA — Batch UPDATE (bukan set!) ke DUA dokumen sekaligus:
            //   1. users/{uid}              → profil + nama org
            //   2. organizations/{orgId}    → name & nama org (untuk semua anggota)
            //
            // Menggunakan .update() BUKAN .set() → field role, email,
            // createdAt, organisasiId DIJAMIN tidak tersentuh.
            // ─────────────────────────────────────────────────────────────
            val batch = db.batch()

            val userRef = db.collection("users").document(userId)
            batch.update(userRef, mapOf(
                "nama"         to nama,
                "nim"          to nim,
                "no_hp"        to noHp,
                "organization" to organization,
                "organisasi"   to organization,   // alias field
                "avatar_id"    to selectedAvatarId
                // role, email, createdAt, organisasiId → TIDAK DISENTUH
            ))

            val orgRef = db.collection("organizations").document(organisasiId)
            batch.update(orgRef, mapOf(
                "name" to organization,
                "nama" to organization
            ))

            batch.commit()
                .addOnSuccessListener {
                    val firstName = nama.trim().split(" ")[0]
                    sessionManager.saveUserProfile(firstName, userRole, selectedAvatarId, organization)
                    showTopSnackbar(getString(R.string.profile_updated))
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1000)
                }
                .addOnFailureListener { e ->
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = getString(R.string.btn_save_changes)
                    showTopSnackbar("Gagal menyimpan: ${e.message}")
                }

        } else {
            // ─────────────────────────────────────────────────────────────
            // ANGGOTA — Single UPDATE hanya ke users/{uid}.
            // Field "organization" sengaja dihilangkan dari map karena
            // field tersebut di-lock di UI dan tidak boleh diubah anggota.
            // role, email, createdAt, organisasiId → AMAN 100%.
            // ─────────────────────────────────────────────────────────────
            db.collection("users").document(userId)
                .update(mapOf(
                    "nama"      to nama,
                    "nim"       to nim,
                    "no_hp"     to noHp,
                    "avatar_id" to selectedAvatarId
                ))
                .addOnSuccessListener {
                    val firstName = nama.trim().split(" ")[0]
                    sessionManager.saveUserProfile(
                        firstName,
                        userRole,
                        selectedAvatarId,
                        sessionManager.getUserOrg() // tetap pakai org lama dari cache
                    )
                    showTopSnackbar(getString(R.string.profile_updated))
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1000)
                }
                .addOnFailureListener { e ->
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = getString(R.string.btn_save_changes)
                    showTopSnackbar("Gagal: ${e.message}")
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SNACKBAR HELPER
    // ─────────────────────────────────────────────────────────────────────
    private fun showTopSnackbar(message: String) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        val snackView = snack.view
        val params = snackView.layoutParams
        when (params) {
            is FrameLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.topMargin = 60
                params.leftMargin = 40
                params.rightMargin = 40
                snackView.layoutParams = params
            }
            is CoordinatorLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.topMargin = 60
                params.leftMargin = 40
                params.rightMargin = 40
                snackView.layoutParams = params
            }
        }
        snackView.setBackgroundResource(R.drawable.bg_snackbar)
        snackView.elevation = 10f
        snack.setTextColor(Color.WHITE)
        snack.show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // AVATAR ADAPTER (inner class — harus berada di dalam EditProfileActivity)
    // ─────────────────────────────────────────────────────────────────────
    private inner class AvatarAdapter(
        private val avatarList: List<String>,
        private val onAvatarSelected: (String) -> Unit
    ) : RecyclerView.Adapter<AvatarAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.iv_avatar_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_avatar, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val avatarId = avatarList[position]
            val resId = holder.itemView.context.resources.getIdentifier(
                avatarId, "drawable", holder.itemView.context.packageName
            )
            holder.ivAvatar.setImageResource(resId)
            holder.itemView.setOnClickListener { onAvatarSelected(avatarId) }
        }

        override fun getItemCount(): Int = avatarList.size
    }
}
