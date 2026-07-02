package com.tokoku.orgaku

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tokoku.orgaku.data.model.Comment
import com.tokoku.orgaku.data.model.Task
import com.tokoku.orgaku.databinding.ActivityDetailTugasBinding

class DetailTugasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailTugasBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var taskId: String? = null
    private var currentTask: Task? = null

    // User Identity for Comments
    private var currentUserName: String = "User"
    private var currentUserAvatar: String = "avatar_1"
    private var currentUserRole: String = "anggota"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailTugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        taskId = intent.getStringExtra("TASK_ID")

        if (taskId == null) {
            Toast.makeText(this, "ID Tugas tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        fetchUserIdentity()
        setupCommentInput()
        fetchTaskDetails()
        fetchComments()
    }

    private fun fetchUserIdentity() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentUserName = doc.getString("nama") ?: doc.getString("name") ?: "User"
                    currentUserAvatar = doc.getString("avatar_id") ?: "avatar_1"
                    currentUserRole = doc.getString("role") ?: "anggota"
                }
            }
    }

    private fun fetchTaskDetails() {
        db.collection("tasks").document(taskId!!)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DetailTugas", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val task = snapshot.toObject(Task::class.java)
                    task?.id = snapshot.id
                    currentTask = task
                    bindTaskData(task)
                }
            }
    }

    private fun bindTaskData(task: Task?) {
        task?.let {
            binding.tvTaskTitle.text = it.title
            binding.tvDesc.text = it.description
            binding.tvDeadline.text = "Deadline: ${it.deadline}"
            binding.tvPriority.text = it.priority.uppercase()
            binding.tvStatus.text = it.status.replace("_", " ").uppercase()

            // Update Priority Badge Color
            when (it.priority.uppercase()) {
                "HIGH", "TINGGI" -> binding.tvPriority.setBackgroundResource(R.drawable.bg_badge_priority_high)
                "MEDIUM", "SEDANG" -> binding.tvPriority.setBackgroundResource(R.drawable.bg_badge_priority_medium)
                else -> binding.tvPriority.setBackgroundResource(R.drawable.bg_badge_priority_low)
            }

            // Setup Checklist
            binding.rvChecklist.layoutManager = LinearLayoutManager(this)
            binding.rvChecklist.adapter = SubTaskAdapter(it.checklist) { subTask, isChecked ->
                updateSubTaskStatus(subTask.id, isChecked)
            }
        }
    }

    private fun updateSubTaskStatus(subTaskId: String, isChecked: Boolean) {
        val updatedChecklist = currentTask?.checklist?.map {
            if (it.id == subTaskId) it.copy(isCompleted = isChecked) else it
        } ?: return

        db.collection("tasks").document(taskId!!)
            .update("checklist", updatedChecklist)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal update status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchComments() {
        db.collection("tasks").document(taskId!!)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener

                val commentList = mutableListOf<Comment>()
                value?.forEach { doc ->
                    val comment = doc.toObject(Comment::class.java)
                    comment.id = doc.id
                    commentList.add(comment)
                }

                binding.rvComments.layoutManager = LinearLayoutManager(this)
                binding.rvComments.adapter = CommentAdapter(
                    commentList,
                    auth.currentUser?.uid ?: "",
                    onItemLongClick = { comment ->
                        showDeleteCommentDialog(comment)
                    }
                )
            }
    }

    private fun showDeleteCommentDialog(comment: Comment) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Komentar?")
            .setMessage("Apakah Anda yakin ingin menghapus komentar ini?")
            .setPositiveButton("Ya") { _, _ ->
                deleteComment(comment)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteComment(comment: Comment) {
        db.collection("tasks").document(taskId!!)
            .collection("comments")
            .document(comment.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Komentar dihapus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupCommentInput() {
        binding.btnSendComment.setOnClickListener {
            val body = binding.edtComment.text.toString().trim()
            if (body.isEmpty()) return@setOnClickListener

            val user = auth.currentUser
            val newComment = Comment(
                userId = user?.uid ?: "",
                userName = currentUserName,
                userAvatar = currentUserAvatar,
                body = body,
                timestamp = System.currentTimeMillis()
            )

            binding.edtComment.text.clear()
            db.collection("tasks").document(taskId!!)
                .collection("comments")
                .add(newComment)
                .addOnSuccessListener {
                    notifyNewComment(body)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal mengirim komentar", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun notifyNewComment(commentText: String) {
        val task = currentTask ?: return
        val myUid = auth.currentUser?.uid ?: return

        val isKetuaOrAdmin = currentUserRole.equals("ketua", ignoreCase = true) ||
                            currentUserRole.equals("admin", ignoreCase = true)

        // If I am Ketua/Admin, notify the assignee.
        // If I am Anggota, notify the creator (authorId).
        val targetUserId = if (isKetuaOrAdmin) {
            task.assigneeId
        } else {
            task.authorId
        }

        if (targetUserId.isNotEmpty() && targetUserId != myUid) {
            OneSignalHelper.sendNotification(
                "Diskusi Baru di ${task.title}",
                "$currentUserName: $commentText",
                task.id,
                "comment",
                targetUserId
            )
        }
    }
}
