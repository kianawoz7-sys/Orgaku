package com.tokoku.orgaku

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.tokoku.orgaku.data.model.Task
import com.tokoku.orgaku.databinding.ActivityTugasBinding

class TugasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTugasBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: TaskCardAdapter
    private var allTasks = listOf<Task>()
    private var currentUserRole: String = "anggota"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupStatusToggle()
        setupNavigation()
        checkUserRole()
        fetchTasksFromFirestore()
    }

    private fun setupRecyclerView() {
        adapter = TaskCardAdapter(
            emptyList(),
            currentUserRole,
            onItemClick = { task ->
                val intent = Intent(this, DetailTugasActivity::class.java)
                intent.putExtra("TASK_ID", task.id)
                startActivity(intent)
            },
            onMaju = { task ->
                val newStatus = when (task.status.lowercase()) {
                    "todo" -> "in_progress"
                    "in_progress" -> "done"
                    else -> task.status
                }
                updateTaskStatus(task.id, newStatus)
            },
            onMundur = { task ->
                val newStatus = when (task.status.lowercase()) {
                    "done" -> "in_progress"
                    "in_progress" -> "todo"
                    else -> task.status
                }
                updateTaskStatus(task.id, newStatus)
            },
            onDelete = { task ->
                showDeleteDialog(task)
            }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = adapter
    }

    private fun setupStatusToggle() {
        binding.toggleGroupStatus.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                filterTasks(checkedId)
            }
        }
    }

    private fun checkUserRole() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "anggota"
                currentUserRole = role

                if (role.equals("ketua", ignoreCase = true) || role.equals("admin", ignoreCase = true)) {
                    binding.fabAddTask.visibility = View.VISIBLE
                } else {
                    binding.fabAddTask.visibility = View.GONE
                }
                
                // Refresh list to apply role-based visibility in adapter
                filterTasks(binding.toggleGroupStatus.checkedButtonId)
            }
    }

    private fun fetchTasksFromFirestore() {
        db.collection("tasks")
            .orderBy("deadline", Query.Direction.ASCENDING)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("TugasActivity", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val taskList = mutableListOf<Task>()
                value?.forEach { doc ->
                    try {
                        val task = doc.toObject(Task::class.java)
                        task.id = doc.id
                        taskList.add(task)
                    } catch (e: Exception) {
                        Log.e("TugasActivity", "Error mapping task ${doc.id}: ${e.message}")
                    }
                }
                
                allTasks = taskList
                filterTasks(binding.toggleGroupStatus.checkedButtonId)
            }
    }

    private fun filterTasks(checkedId: Int) {
        val statusFilter = when (checkedId) {
            R.id.btnTodo -> "todo"
            R.id.btnInProgress -> "in_progress"
            R.id.btnDone -> "done"
            else -> "todo"
        }

        val filtered = allTasks.filter { it.status.lowercase() == statusFilter }

        if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvTasks.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvTasks.visibility = View.VISIBLE
            adapter.updateData(filtered, currentUserRole)
        }
    }

    private fun updateTaskStatus(taskId: String, newStatus: String) {
        db.collection("tasks").document(taskId)
            .update("status", newStatus)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Tugas")
            .setMessage("Yakin ingin menghapus tugas ini?")
            .setPositiveButton("Ya") { _, _ ->
                db.collection("tasks").document(task.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Tugas dihapus", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Tidak", null)
            .show()
    }

    private fun setupNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_tugas

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_beranda -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.nav_jadwal -> {
                    startActivity(Intent(this, JadwalActivity::class.java))
                    finish()
                    true
                }

                R.id.nav_tugas -> true
                R.id.nav_profil -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("START_TAB", R.id.nav_profil)
                    startActivity(intent)
                    finish()
                    true
                }

                else -> false
            }
        }

        binding.fabAddTask.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }

        binding.fabQr.setOnClickListener {
            startActivity(Intent(this, AbsensiHomeActivity::class.java))
        }
    }
}
