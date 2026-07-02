package com.tokoku.orgaku

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tokoku.orgaku.data.model.SubTask
import com.tokoku.orgaku.data.model.Task
import com.tokoku.orgaku.databinding.ActivityAddTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var selectedPriority = "Medium"
    private val calendar = Calendar.getInstance()

    private val userList = mutableListOf<Pair<String, String>>() // Pair(Name, UID)
    private var selectedAssigneeId: String = ""
    // Cached from Firestore during fetchUsers() — stamped onto every new task
    private var currentOrgId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupPrioritySelection()
        setupDatePicker()
        setupSubTaskLogic()
        fetchUsers()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            saveTaskToFirestore()
        }
    }

    private fun fetchUsers() {
        val currentUid = auth.currentUser?.uid ?: return

        // Step 1: Ambil data Ketua untuk mendapatkan organisasiId dan fallback org string
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener { currentUserDoc ->
                val leaderOrgId = currentUserDoc.getString("organisasiId") ?: ""
                val fallbackOrgString = currentUserDoc.getString("organization")
                    ?: currentUserDoc.getString("organisasi") ?: ""

                Log.d("AddTask", "Leader organisasiId: '$leaderOrgId' | fallback: '$fallbackOrgString'")

                // Cache the master key so saveTaskToFirestore() can stamp it on the document
                currentOrgId = leaderOrgId

                if (leaderOrgId.isNotEmpty()) {
                    // ─────────────────────────────────────────────────────
                    // PRIMARY: Query akurat menggunakan organisasiId
                    // Mencakup semua anggota yang join via link/QR maupun
                    // yang didaftarkan manual, tanpa masalah typo nama org.
                    // ─────────────────────────────────────────────────────
                    db.collection("users")
                        .whereEqualTo("organisasiId", leaderOrgId)
                        .whereEqualTo("role", "anggota")
                        .get()
                        .addOnSuccessListener { snapshot ->
                            Log.d("AddTask", "organisasiId query → ${snapshot.size()} anggota ditemukan")
                            populateMemberDropdown(snapshot.documents, currentUid)
                        }
                        .addOnFailureListener { e ->
                            Log.w("AddTask", "organisasiId query gagal: ${e.message}. Coba fallback...")
                            fetchMembersByOrgString(fallbackOrgString, currentUid)
                        }
                } else {
                    // ─────────────────────────────────────────────────────
                    // FALLBACK: organisasiId belum ada → pakai string org lama
                    // ─────────────────────────────────────────────────────
                    Log.w("AddTask", "organisasiId kosong, menggunakan fallback string org")
                    fetchMembersByOrgString(fallbackOrgString, currentUid)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengambil data organisasi", Toast.LENGTH_SHORT).show()
            }
    }

    /** Fallback: query lama berdasarkan string nama organisasi */
    private fun fetchMembersByOrgString(orgString: String, currentUid: String) {
        if (orgString.isEmpty()) {
            Toast.makeText(this, "Data organisasi tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users")
            .whereEqualTo("organization", orgString)
            .whereEqualTo("role", "anggota")
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d("AddTask", "Fallback org-string query → ${snapshot.size()} anggota ditemukan")
                populateMemberDropdown(snapshot.documents, currentUid)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat daftar anggota", Toast.LENGTH_SHORT).show()
            }
    }

    /** Bind hasil query ke AutoComplete dropdown dan userList */
    private fun populateMemberDropdown(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        currentUid: String
    ) {
        userList.clear()
        val userNames = mutableListOf<String>()

        for (doc in docs) {
            val uid = doc.id
            if (uid == currentUid) continue  // Jangan tampilkan diri sendiri

            val name = doc.getString("nama") ?: doc.getString("name") ?: "User"
            Log.d("AddTask", ">> Menambahkan anggota ke dropdown: $name ($uid)")
            userList.add(name to uid)
            userNames.add(name)
        }

        if (userNames.isEmpty()) {
            Log.w("AddTask", "Tidak ada anggota ditemukan untuk organisasi ini")
            Toast.makeText(this, "Belum ada anggota dalam organisasi ini", Toast.LENGTH_SHORT).show()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, userNames)
        binding.autoCompleteAssignee.setAdapter(adapter)

        binding.autoCompleteAssignee.setOnItemClickListener { _, _, position, _ ->
            selectedAssigneeId = userList[position].second
            Log.d("AddTask", "Assignee dipilih: ${userList[position].first} (${userList[position].second})")
        }
    }

    private fun setupDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            binding.edtDeadline.setText(sdf.format(calendar.time))
        }

        binding.edtDeadline.setOnClickListener {
            DatePickerDialog(
                this,
                dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupSubTaskLogic() {
        binding.btnAddSubTask.setOnClickListener {
            val editText = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                hint = "Ketik sub-tugas..."
                background = getDrawable(R.drawable.bg_edit_text)
                setPadding(40, 30, 40, 30)
                textSize = 14f
            }
            binding.containerSubTasks.addView(editText)
        }
    }

    private fun saveTaskToFirestore() {
        val title = binding.edtTitle.text.toString().trim()
        val description = binding.edtDesc.text.toString().trim()
        val deadline = binding.edtDeadline.text.toString().trim()
        val authorId = auth.currentUser?.uid ?: ""

        if (title.isEmpty()) {
            Toast.makeText(this, "Judul tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        if (deadline.isEmpty()) {
            Toast.makeText(this, "Deadline tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedAssigneeId.isEmpty()) {
            Toast.makeText(this, "Silakan pilih anggota untuk ditugaskan", Toast.LENGTH_SHORT).show()
            return
        }

        // Guard: organisasiId must have been resolved by fetchUsers() before we can save
        if (currentOrgId.isEmpty()) {
            Toast.makeText(this, "Data organisasi belum siap. Coba lagi sebentar.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("DEBUG_ORG", "[AddTask] Saving with organisasiId: $currentOrgId")

        // Gather Sub-tasks
        val subTasks = mutableListOf<SubTask>()
        for (view in binding.containerSubTasks.children) {
            if (view is EditText) {
                val subTitle = view.text.toString().trim()
                if (subTitle.isNotEmpty()) {
                    subTasks.add(SubTask(id = UUID.randomUUID().toString(), title = subTitle, isCompleted = false))
                }
            }
        }

        val newTask = Task(
            title = title,
            description = description,
            priority = selectedPriority,
            deadline = deadline,
            status = "todo",
            authorId = authorId,
            assigneeId = selectedAssigneeId,
            organisasiId = currentOrgId,   // ← master key stamped here
            checklist = subTasks,
            createdAt = System.currentTimeMillis()
        )

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Menyimpan..."

        db.collection("tasks").add(newTask)
            .addOnSuccessListener { docRef ->
                notifyUsers(title, docRef.id)
                Toast.makeText(this, "Tugas berhasil disimpan!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Simpan Tugas"
                Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun notifyUsers(taskTitle: String, taskId: String) {
        OneSignalHelper.sendNotification(
            "Tugas Baru: $taskTitle",
            "Cek aplikasi Orgaku sekarang!",
            taskId,
            "task",
            selectedAssigneeId
        )
    }

    private fun setupPrioritySelection() {
        binding.btnPriorityLow.setOnClickListener {
            selectedPriority = "Low"
            updatePriorityUI()
        }
        binding.btnPriorityMedium.setOnClickListener {
            selectedPriority = "Medium"
            updatePriorityUI()
        }
        binding.btnPriorityHigh.setOnClickListener {
            selectedPriority = "High"
            updatePriorityUI()
        }
        updatePriorityUI() // Initial state
    }

    private fun updatePriorityUI() {
        // Low
        binding.btnPriorityLow.setBackgroundResource(if (selectedPriority == "Low") R.drawable.bg_tab_indicator else 0)
        binding.btnPriorityLow.setTextColor(if (selectedPriority == "Low") getColor(R.color.white) else getColor(R.color.text_muted))
        
        // Medium
        binding.btnPriorityMedium.setBackgroundResource(if (selectedPriority == "Medium") R.drawable.bg_tab_indicator else 0)
        binding.btnPriorityMedium.setTextColor(if (selectedPriority == "Medium") getColor(R.color.white) else getColor(R.color.text_muted))
        
        // High
        binding.btnPriorityHigh.setBackgroundResource(if (selectedPriority == "High") R.drawable.bg_tab_indicator else 0)
        binding.btnPriorityHigh.setTextColor(if (selectedPriority == "High") getColor(R.color.white) else getColor(R.color.text_muted))
    }
}
