package com.tokoku.orgaku.ui.task

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.tokoku.orgaku.AddTaskActivity
import com.tokoku.orgaku.DetailTugasActivity
import com.tokoku.orgaku.R
import com.tokoku.orgaku.TaskCardAdapter
import com.tokoku.orgaku.data.model.Task
import com.tokoku.orgaku.databinding.FragmentTugasBinding
import com.tokoku.orgaku.util.SessionManager

class TugasFragment : Fragment() {

    private var _binding: FragmentTugasBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TaskCardAdapter
    private lateinit var sessionManager: SessionManager

    private var allTasks = mutableListOf<Task>()
    private var currentUserRole: String = "anggota"

    // GUARD: Pastikan fetch hanya terjadi SEKALI. Hide/Show tidak akan trigger ulang.
    private var dataLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTugasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(requireContext())

        setupRecyclerView()
        setupStatusToggle()

        // Hanya fetch data pertama kali saat fragment dibuat.
        // Kalau dataLoaded = true, berarti fragment hanya di-hide bukan di-destroy.
        // Re-fetch saat kembali terlihat ditangani oleh onHiddenChanged().
        if (!dataLoaded) {
            checkUserRole()
            loadCachedTasks()
            fetchTasks()
            dataLoaded = true
        }

        binding.fabAddTask.setOnClickListener {
            startActivity(Intent(requireContext(), AddTaskActivity::class.java))
        }
    }

    // Dipanggil saat tab di-switch → re-fetch agar data selalu fresh
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && dataLoaded && _binding != null) {
            fetchTasks()
        }
    }

    // Dipanggil saat kembali dari Activity lain (misal AddTaskActivity → MainActivity).
    // Ini memastikan tugas baru langsung muncul tanpa perlu relog.
    override fun onResume() {
        super.onResume()
        if (dataLoaded && _binding != null && !isHidden) {
            fetchTasks()
        }
    }

    private fun loadCachedTasks() {
        val uid = auth.currentUser?.uid ?: run {
            allTasks.clear()
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.rvTasks.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[loadCachedTasks] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    allTasks.clear()
                    binding.shimmerView.stopShimmer()
                    binding.shimmerView.visibility = View.GONE
                    binding.rvTasks.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                db.collection("tasks")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("deadline", Query.Direction.ASCENDING)
                    .get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        if (!snapshot.isEmpty) {
                            allTasks.clear()
                            snapshot.forEach { doc ->
                                val task = doc.toObject(Task::class.java)
                                task.id = doc.id
                                allTasks.add(task)
                            }

                            // ANTI-FLICKER: Bypass shimmer immediately and show records smoothly
                            binding.shimmerView.stopShimmer()
                            binding.shimmerView.visibility = View.GONE
                            binding.rvTasks.visibility = View.VISIBLE
                            filterTasks(binding.toggleGroupStatus.checkedButtonId)
                        } else {
                            // No cache: show shimmer while waiting
                            binding.shimmerView.visibility = View.VISIBLE
                            binding.shimmerView.startShimmer()
                            binding.rvTasks.visibility = View.GONE
                        }
                    }
                    .addOnFailureListener {
                        if (_binding != null) {
                            binding.shimmerView.visibility = View.VISIBLE
                            binding.shimmerView.startShimmer()
                        }
                    }
            }
            .addOnFailureListener {
                if (_binding != null) {
                    binding.shimmerView.visibility = View.VISIBLE
                    binding.shimmerView.startShimmer()
                }
            }
    }

    private fun setupRecyclerView() {
        adapter = TaskCardAdapter(
            emptyList(),
            currentUserRole,
            onItemClick = { task ->
                val intent = Intent(requireContext(), DetailTugasActivity::class.java)
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
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
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
                if (_binding == null) return@addOnSuccessListener
                val role = document.getString("role") ?: "anggota"
                currentUserRole = role

                if (role.equals("ketua", ignoreCase = true) || role.equals(
                        "admin",
                        ignoreCase = true
                    )
                ) {
                    binding.fabAddTask.visibility = View.VISIBLE
                } else {
                    binding.fabAddTask.visibility = View.GONE
                }

                // Refresh list to apply role-based visibility in adapter
                filterTasks(binding.toggleGroupStatus.checkedButtonId)
            }
    }

    private fun fetchTasks() {
        val uid = auth.currentUser?.uid ?: run {
            allTasks.clear()
            filterTasks(binding.toggleGroupStatus.checkedButtonId)
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[fetchTasks] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    allTasks.clear()
                    filterTasks(binding.toggleGroupStatus.checkedButtonId)
                    return@addOnSuccessListener
                }

                // SERVER SYNC (Silent Background Update)
                db.collection("tasks")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("deadline", Query.Direction.ASCENDING)
                    .get(Source.SERVER)
                    .addOnSuccessListener { value ->
                        if (_binding == null) return@addOnSuccessListener

                        val tasks = value?.documents?.mapNotNull { doc ->
                            val task = doc.toObject(Task::class.java)
                            task?.id = doc.id
                            task
                        } ?: emptyList()

                        Log.d("DEBUG_ORG", "[fetchTasks] Server returned ${tasks.size} tasks for org $organisasiId")

                        // Always apply server result — even 0 tasks must clear cache + show empty state
                        allTasks.clear()
                        allTasks.addAll(tasks)
                        filterTasks(binding.toggleGroupStatus.checkedButtonId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("DEBUG_ORG", "[fetchTasks] Query failed: ${e.message}")
                    }
            }
    }

    private fun filterTasks(checkedId: Int) {
        if (_binding == null) return

        // Stop Shimmer
        binding.shimmerView.stopShimmer()
        binding.shimmerView.visibility = View.GONE

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
        // STEP 1: Optimistic local update — UI langsung responsif tanpa tunggu server
        val index = allTasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            allTasks[index] = allTasks[index].copy(status = newStatus)
            // Refresh tampilan berdasarkan filter yang aktif saat ini
            filterTasks(binding.toggleGroupStatus.checkedButtonId)
        }

        // STEP 2: Sinkronisasi ke Firestore di background (silent)
        db.collection("tasks").document(taskId)
            .update("status", newStatus)
            .addOnFailureListener { e ->
                // Rollback: kembalikan status lama di memori jika Firestore gagal
                if (index != -1 && _binding != null) {
                    val oldStatus = when (newStatus.lowercase()) {
                        "in_progress" -> "todo"
                        "done"        -> "in_progress"
                        else          -> "todo"
                    }
                    allTasks[index] = allTasks[index].copy(status = oldStatus)
                    filterTasks(binding.toggleGroupStatus.checkedButtonId)
                }
                Toast.makeText(requireContext(), "Gagal update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteDialog(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Tugas")
            .setMessage("Yakin ingin menghapus tugas ini?")
            .setPositiveButton("Ya") { _, _ ->
                db.collection("tasks").document(task.id)
                    .delete()
                    .addOnSuccessListener {
                        // Hapus dari memori dan refresh UI tanpa re-fetch
                        allTasks.removeAll { it.id == task.id }
                        filterTasks(binding.toggleGroupStatus.checkedButtonId)
                        Toast.makeText(requireContext(), "Tugas dihapus", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            "Gagal menghapus: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton("Tidak", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
