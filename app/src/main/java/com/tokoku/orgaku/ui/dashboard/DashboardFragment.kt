package com.tokoku.orgaku.ui.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.tokoku.orgaku.DetailMeetingActivity
import com.tokoku.orgaku.Document
import com.tokoku.orgaku.DocumentOrgActivity
import com.tokoku.orgaku.InviteMemberActivity
import com.tokoku.orgaku.JadwalActivity
import com.tokoku.orgaku.NotificationActivity
import com.tokoku.orgaku.R
import com.tokoku.orgaku.TugasActivity
import com.tokoku.orgaku.data.model.Task
import com.tokoku.orgaku.databinding.FragmentDashboardBinding
import com.tokoku.orgaku.databinding.ItemDocumentBinding
import com.tokoku.orgaku.databinding.ItemTaskBinding
import com.tokoku.orgaku.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sessionManager: SessionManager

    private val latestTasksList = mutableListOf<Task>()
    private val latestDocsList = mutableListOf<Document>()

    private var taskAdapter: TaskPreviewAdapter? = null
    private var docAdapter: DocumentPreviewAdapter? = null

    // GUARD: Pastikan fetch Firestore hanya terjadi SEKALI.
    // Hide/Show strategy tidak akan re-trigger onViewCreated setelah inisialisasi pertama.
    private var dataLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(requireContext())

        setupDate()
        setupRecyclerViews()
        setupNavigation()

        binding.ivProfile.setOnClickListener {
            (requireActivity() as? com.tokoku.orgaku.MainActivity)?.setSelectedTab(R.id.nav_profil)
        }

        // Hanya fetch data pertama kali. Kalau dataLoaded = true, berarti
        // fragment ini sudah punya data dan hanya di-hide, bukan di-destroy.
        if (!dataLoaded) {
            loadCachedData()
            refreshAllData()
            dataLoaded = true
        }
        // Jika sudah punya data, RecyclerView adapter tidak perlu di-clear.
        // Data tetap intact di latestTasksList & latestDocsList.

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshAllData()
        }
    }

    // Dipanggil saat tab di-switch via Hide/Show.
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Saat dashboard muncul kembali, refresh hanya bagian kecil seperti tanggal
        if (!hidden && dataLoaded && _binding != null) {
            setupDate()
        }
    }

    private fun loadCachedData() {
        val name = sessionManager.getUserName()
        val role = sessionManager.getUserRole()
        val org = sessionManager.getUserOrg()
        val photo = sessionManager.getUserPhoto()

        if (name.isNotEmpty()) {
            // Bind cached data immediately
            binding.tvGreeting.text = getString(R.string.greeting_format, name)
            binding.badgeKetua.text = role.uppercase()
            binding.tvOrgName.text = org

            if (photo.startsWith("http")) {
                Glide.with(this).load(photo).circleCrop().into(binding.ivProfile)
            } else if (photo.isNotEmpty()) {
                val resId = resources.getIdentifier(photo, "drawable", requireContext().packageName)
                binding.ivProfile.setImageResource(if (resId != 0) resId else R.drawable.avatar_1)
            }

            // ANTI-FLICKER: Make layout VISIBLE instantly and bypass shimmer entirely
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.cardOrg.visibility = View.VISIBLE
            binding.mainContent.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerViews() {
        taskAdapter = TaskPreviewAdapter(latestTasksList)
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = taskAdapter
        binding.rvTasks.isNestedScrollingEnabled = false

        docAdapter = DocumentPreviewAdapter(latestDocsList)
        binding.rvDocs.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvDocs.adapter = docAdapter
        binding.rvDocs.isNestedScrollingEnabled = false
    }

    private fun refreshAllData() {
        startLoading()
        fetchDashboardData()
        fetchStats()
        fetchNextMeeting()
        fetchLatestTasks()
        fetchLatestDocuments()
        fetchUserAvatars()
    }

    private fun startLoading() {
        val hasCache = sessionManager.getUserName().isNotEmpty()

        if (!hasCache) {
            // First launch, no cache → tampilkan Shimmer Skeleton
            // HANYA terjadi saat !dataLoaded + cache kosong.
            // Tab-switch tidak pernah masuk sini karena dataLoaded = true.
            binding.shimmerView.alpha = 1f
            binding.shimmerView.visibility = View.VISIBLE
            binding.shimmerView.startShimmer()
            binding.cardOrg.visibility = View.GONE
            binding.mainContent.visibility = View.GONE
        } else {
            // Ada cache → bypass shimmer sepenuhnya, konten langsung tampil
            binding.shimmerView.stopShimmer()
            binding.shimmerView.visibility = View.GONE
            binding.cardOrg.visibility = View.VISIBLE
            binding.mainContent.visibility = View.VISIBLE
        }
    }

    private fun stopLoading() {
        if (_binding == null) return

        binding.swipeRefreshLayout.isRefreshing = false

        // Jika skeleton sedang tampil → fade-out skeleton, fade-in konten
        if (binding.shimmerView.visibility == View.VISIBLE) {
            binding.shimmerView.stopShimmer()

            // Siapkan konten untuk fade-in (alpha=0 dulu, lalu visible)
            binding.cardOrg.alpha = 0f
            binding.mainContent.alpha = 0f
            binding.cardOrg.visibility = View.VISIBLE
            binding.mainContent.visibility = View.VISIBLE

            // Animasi: shimmer fade-out + konten fade-in secara bersamaan
            val fadeOutSkeleton = ObjectAnimator.ofFloat(binding.shimmerView, "alpha", 1f, 0f)
                .apply { duration = 250 }
            val fadeInCard = ObjectAnimator.ofFloat(binding.cardOrg, "alpha", 0f, 1f)
                .apply { duration = 300 }
            val fadeInContent = ObjectAnimator.ofFloat(binding.mainContent, "alpha", 0f, 1f)
                .apply { duration = 300 }

            AnimatorSet().apply {
                playTogether(fadeOutSkeleton, fadeInCard, fadeInContent)
                start()
            }

            binding.shimmerView.visibility = View.GONE
        } else {
            // Konten sudah tampil (dari cache) — cukup pastikan visible tanpa animasi
            binding.cardOrg.visibility = View.VISIBLE
            binding.mainContent.visibility = View.VISIBLE
        }
    }

    private fun setupDate() {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id"))
        val currentDate = sdf.format(Date())
        binding.tvDate.text = currentDate
    }

    private fun fetchDashboardData() {
        val user = auth.currentUser ?: run {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val uid = user.uid

        db.collection("users").document(uid).get(Source.SERVER)
            .addOnCompleteListener { task ->
                if (_binding == null) return@addOnCompleteListener

                if (task.isSuccessful) {
                    val document = task.result
                    if (document != null && document.exists()) {
                        val role = document.getString("role") ?: "anggota"
                        val rawNama =
                            document.getString("nama") ?: document.getString("name") ?: "User"
                        val avatarId = document.getString("avatar_id") ?: "avatar_1"
                        val orgId = document.getString("organisasiId")
                        val fallbackOrg =
                            document.getString("organisasi") ?: document.getString("organization")
                            ?: "-"

                        val firstName = if (rawNama.isNotEmpty()) {
                            rawNama.trim().split(" ")[0].lowercase()
                                .replaceFirstChar { it.uppercase() }
                        } else {
                            "User"
                        }

                        val fallbackDisplay = if (fallbackOrg.isEmpty()) "-" else fallbackOrg

                        val updateUI = { displayOrg: String ->
                            if (_binding != null) {
                                // Update Cache
                                sessionManager.saveUserProfile(
                                    firstName,
                                    role,
                                    avatarId,
                                    displayOrg,
                                    orgId ?: ""
                                )

                                binding.tvGreeting.text =
                                    getString(R.string.greeting_format, firstName)
                                binding.badgeKetua.text = role.uppercase()
                                binding.tvOrgName.text = displayOrg

                                if (avatarId.startsWith("http")) {
                                    Glide.with(this@DashboardFragment)
                                        .load(avatarId)
                                        .placeholder(R.drawable.avatar_1)
                                        .circleCrop()
                                        .into(binding.ivProfile)
                                } else {
                                    val resId = resources.getIdentifier(
                                        avatarId,
                                        "drawable",
                                        requireContext().packageName
                                    )
                                    if (resId != 0) {
                                        binding.ivProfile.setImageResource(resId)
                                    } else {
                                        binding.ivProfile.setImageResource(R.drawable.avatar_1)
                                    }
                                }

                                if (role.equals("ketua", ignoreCase = true) || role.equals(
                                        "admin",
                                        ignoreCase = true
                                    ) || role.equals("chairman", ignoreCase = true)
                                ) {
                                    binding.cardAdminMenu.visibility = View.VISIBLE
                                } else {
                                    binding.cardAdminMenu.visibility = View.GONE
                                }

                                stopLoading()
                            }
                        }

                        if (!orgId.isNullOrEmpty()) {
                            db.collection("organizations").document(orgId).get()
                                .addOnSuccessListener { orgDoc ->
                                    val realOrgName = if (orgDoc != null && orgDoc.exists()) {
                                        orgDoc.getString("name") ?: orgDoc.getString("nama")
                                        ?: "Nama Organisasi"
                                    } else {
                                        fallbackDisplay
                                    }
                                    updateUI(realOrgName)
                                }
                                .addOnFailureListener {
                                    updateUI(fallbackDisplay)
                                }
                        } else {
                            updateUI(fallbackDisplay)
                        }
                    } else {
                        stopLoading()
                    }
                } else {
                    stopLoading()
                }
            }
    }

    private fun fetchStats() {
        val uid = auth.currentUser?.uid ?: run {
            if (_binding != null) {
                binding.tvActiveMembers.text = getString(R.string.active_members_format, 0)
                binding.tvTugasCount.text = "0"
                binding.tvDocCount.text = "0"
                binding.tvRapatCount.text = "0"
            }
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[fetchStats] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    binding.tvActiveMembers.text = getString(R.string.active_members_format, 0)
                    binding.tvTugasCount.text = "0"
                    binding.tvDocCount.text = "0"
                    binding.tvRapatCount.text = "0"
                    return@addOnSuccessListener
                }

                db.collection("users").whereEqualTo("organisasiId", organisasiId).get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        binding.tvActiveMembers.text =
                            getString(R.string.active_members_format, snapshot.size())
                    }

                db.collection("tasks").whereEqualTo("organisasiId", organisasiId).get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        val activeTasks =
                            snapshot.documents.count { it.getString("status") != "done" }
                        binding.tvTugasCount.text = activeTasks.toString()
                    }

                db.collection("documents").whereEqualTo("organisasiId", organisasiId).get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        binding.tvDocCount.text = snapshot.size().toString()
                    }

                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                val startOfWeek =
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

                db.collection("meetings")
                    .whereEqualTo("organisasiId", organisasiId)
                    .whereGreaterThanOrEqualTo("date", startOfWeek)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        binding.tvRapatCount.text = snapshot.size().toString()
                    }
            }
    }

    private fun fetchNextMeeting() {
        val uid = auth.currentUser?.uid ?: run {
            hideNextMeetingSection()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[fetchNextMeeting] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    hideNextMeetingSection()
                    return@addOnSuccessListener
                }

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Scope by org only — do NOT filter by userId.
                // Meetings may not be stamped with a userId, so that filter silently returns 0.
                val query = db.collection("meetings")
                    .whereEqualTo("organisasiId", organisasiId)
                    .whereGreaterThanOrEqualTo("date", today)
                    .orderBy("date", Query.Direction.ASCENDING)
                    .limit(1)

                // 1. STEP 1: CACHE FIRST (Instant UI)
                query.get(Source.CACHE).addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    if (!snapshot.isEmpty) {
                        updateMeetingUI(snapshot)
                    }
                }.addOnCompleteListener {
                    // 2. STEP 2: SERVER SYNC (Silent Background Update)
                    query.get(Source.SERVER).addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        Log.d(
                            "DEBUG_ORG",
                            "[fetchNextMeeting] Server returned ${snapshot.size()} meetings for org $organisasiId"
                        )
                        updateMeetingUI(snapshot)
                    }.addOnFailureListener { e ->
                        Log.e("DEBUG_ORG", "[fetchNextMeeting] Query failed: ${e.message}")
                        hideNextMeetingSection()
                    }
                }
            }
            .addOnFailureListener {
                hideNextMeetingSection()
            }
    }

    private fun hideNextMeetingSection() {
        if (_binding == null) return
        binding.cardMeeting.visibility = View.GONE
    }

    private fun showNextMeetingSection() {
        if (_binding == null) return
        binding.cardMeeting.visibility = View.VISIBLE
    }

    private fun updateMeetingUI(snapshot: com.google.firebase.firestore.QuerySnapshot?) {
        if (snapshot == null || snapshot.isEmpty) {
            hideNextMeetingSection()
            return
        }

        showNextMeetingSection()

        val doc = snapshot.documents[0]
        val meetingId = doc.id
        val title = doc.getString("title") ?: ""
        val dateStr = doc.getString("date") ?: ""
        val location = doc.getString("location") ?: ""
        val startTime = doc.getString("startTime") ?: ""
        val endTime = doc.getString("endTime") ?: ""
        val description = doc.getString("description") ?: ""

        binding.tvMeetingTitle.text = title
        binding.tvMeetingLoc.text = location

        // Defensively hide meeting type row as requested
        binding.layoutStatus.visibility = View.GONE

        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date

                val dayFormat = SimpleDateFormat("EEE,", Locale.forLanguageTag("id"))
                val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.forLanguageTag("id"))

                binding.tvMeetingDayName.text = dayFormat.format(date)
                binding.tvMeetingDateNumber.text =
                    String.format(Locale.getDefault(), "%02d", cal.get(Calendar.DAY_OF_MONTH))
                binding.tvMeetingMonthYear.text = monthYearFormat.format(date)
            }
        } catch (e: Exception) {
            binding.tvMeetingDayName.text = "---"
            binding.tvMeetingDateNumber.text = "00"
            binding.tvMeetingMonthYear.text = "N/A"
        }

        binding.btnMeetingDetail.setOnClickListener {
            val intent = Intent(requireContext(), DetailMeetingActivity::class.java).apply {
                putExtra("EXTRA_MEETING_ID", meetingId)
                putExtra("EXTRA_TITLE", title)
                putExtra("EXTRA_DATE", dateStr)
                putExtra("EXTRA_DESC", description)
                putExtra("EXTRA_LOCATION", location)
                putExtra("EXTRA_START_TIME", startTime)
                putExtra("EXTRA_END_TIME", endTime)
            }
            startActivity(intent)
        }
    }

    private fun fetchUserAvatars() {
        val uid = auth.currentUser?.uid ?: run {
            val avatarViews = listOf(binding.ivAvatar1, binding.ivAvatar2, binding.ivAvatar3)
            avatarViews.forEach { it.visibility = View.GONE }
            binding.cardMoreMembers.visibility = View.GONE
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[fetchUserAvatars] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    val avatarViews =
                        listOf(binding.ivAvatar1, binding.ivAvatar2, binding.ivAvatar3)
                    avatarViews.forEach { it.visibility = View.GONE }
                    binding.cardMoreMembers.visibility = View.GONE
                    return@addOnSuccessListener
                }

                db.collection("users").whereEqualTo("organisasiId", organisasiId).limit(10).get()
                    .addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener

                        val users = snapshot.documents
                        val totalUsers = snapshot.size()
                        val avatarViews =
                            listOf(binding.ivAvatar1, binding.ivAvatar2, binding.ivAvatar3)

                        avatarViews.forEach { it.visibility = View.GONE }
                        binding.cardMoreMembers.visibility = View.GONE

                        users.take(3).forEachIndexed { index, doc ->
                            val avatarId = doc.getString("avatar_id") ?: "avatar_1"
                            val view = avatarViews[index]
                            view.visibility = View.VISIBLE
                            if (avatarId.startsWith("http")) {
                                Glide.with(this@DashboardFragment).load(avatarId).circleCrop()
                                    .into(view)
                            } else {
                                val resId = resources.getIdentifier(
                                    avatarId,
                                    "drawable",
                                    requireContext().packageName
                                )
                                view.setImageResource(if (resId != 0) resId else R.drawable.avatar_1)
                            }
                        }

                        if (totalUsers > 3) {
                            binding.cardMoreMembers.visibility = View.VISIBLE
                            binding.tvMoreMembers.text = "+${totalUsers - 3}"
                        }
                    }
            }
    }

    private fun fetchLatestTasks() {
        val uid = auth.currentUser?.uid ?: run {
            latestTasksList.clear()
            taskAdapter?.notifyDataSetChanged()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[fetchLatestTasks] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    latestTasksList.clear()
                    taskAdapter?.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                val query = db.collection("tasks")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(5)

                // 1. Cache First
                query.get(Source.CACHE).addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    if (!snapshot.isEmpty) {
                        updateTasksUI(snapshot)
                    }
                }.addOnCompleteListener {
                    // 2. Server Sync
                    query.get(Source.SERVER).addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        updateTasksUI(snapshot)
                    }
                }
            }
    }

    private fun updateTasksUI(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        latestTasksList.clear()
        val items = snapshot.documents.mapNotNull { doc ->
            val task = doc.toObject(Task::class.java)
            task?.id = doc.id
            task
        }
        latestTasksList.addAll(items)
        taskAdapter?.notifyDataSetChanged()
    }

    private fun fetchLatestDocuments() {
        val uid = auth.currentUser?.uid ?: run {
            latestDocsList.clear()
            docAdapter?.notifyDataSetChanged()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val organisasiId = userDoc.getString("organisasiId")
                Log.d("DEBUG_ORG", "[fetchLatestDocuments] Extracted OrgID: $organisasiId")

                if (organisasiId.isNullOrEmpty() || organisasiId == "-") {
                    latestDocsList.clear()
                    docAdapter?.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                val query = db.collection("documents")
                    .whereEqualTo("organisasiId", organisasiId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(3)

                // 1. Cache First
                query.get(Source.CACHE).addOnSuccessListener { snapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    if (!snapshot.isEmpty) {
                        updateDocsUI(snapshot)
                    }
                }.addOnCompleteListener {
                    // 2. Server Sync
                    query.get(Source.SERVER).addOnSuccessListener { snapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        updateDocsUI(snapshot)
                    }
                }
            }
    }

    private fun updateDocsUI(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        latestDocsList.clear()
        val items = snapshot.documents.mapNotNull { doc ->
            try {
                val d = doc.toObject(Document::class.java)
                d?.id = doc.id
                if (d?.createdAt == null || d.createdAt.toString().isEmpty()) null else d
            } catch (e: Exception) {
                null
            }
        }
        latestDocsList.addAll(items)
        docAdapter?.notifyDataSetChanged()
    }

    private fun setupNavigation() {
        binding.btnKelolaAnggota.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Fitur Kelola Anggota segera hadir",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.btnUndangAnggota.setOnClickListener {
            val orgName = sessionManager.getUserOrg()
            // Kirim org name via Intent; InviteMemberActivity akan fetch orgId dari Firestore
            // jika extra EXTRA_ORGANISASI_ID tidak tersedia.
            val intent = Intent(requireContext(), InviteMemberActivity::class.java).apply {
                putExtra(InviteMemberActivity.EXTRA_ORGANISASI, orgName)
            }
            startActivity(intent)
        }
        binding.btnUploadDoc.setOnClickListener {
            startActivity(Intent(requireContext(), DocumentOrgActivity::class.java))
        }

        val toJadwal = View.OnClickListener {
            startActivity(Intent(requireContext(), JadwalActivity::class.java))
        }
        binding.btnSeeAllRapat.setOnClickListener(toJadwal)
        // Note: btnMeetingDetail click is handled inside fetchNextMeeting for specific meeting data

        binding.btnSeeAllTugas.setOnClickListener {
            startActivity(Intent(requireContext(), TugasActivity::class.java))
        }
        binding.btnSeeAllDocs.setOnClickListener {
            startActivity(Intent(requireContext(), DocumentOrgActivity::class.java))
        }

        binding.btnNotification.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationActivity::class.java))
        }
    }

    inner class TaskPreviewAdapter(private val tasks: List<Task>) :
        RecyclerView.Adapter<TaskPreviewAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val task = tasks[position]
            val context = holder.binding.root.context

            holder.binding.tvTaskTitle.text = task.title
            holder.binding.tvDeadline.text = getString(R.string.deadline_format, task.deadline)
            holder.binding.tvPriority.text = task.priority.uppercase()

            // Frontend Mapping for 3-State UI (using String from Firebase)
            when (task.status.lowercase()) {
                "in_progress" -> {
                    holder.binding.ivStatusIndicator.setImageResource(R.drawable.ic_status_progress)
                    holder.binding.tvTaskTitle.paintFlags =
                        holder.binding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    holder.binding.tvTaskTitle.alpha = 1.0f
                    holder.binding.metaLayout.alpha = 1.0f
                }

                "done", "completed" -> {
                    holder.binding.ivStatusIndicator.setImageResource(R.drawable.ic_status_done)
                    holder.binding.tvTaskTitle.paintFlags =
                        holder.binding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    holder.binding.tvTaskTitle.alpha = 0.7f
                    holder.binding.metaLayout.alpha = 0.7f
                }

                else -> { // "todo"
                    holder.binding.ivStatusIndicator.setImageResource(R.drawable.ic_status_todo)
                    holder.binding.tvTaskTitle.paintFlags =
                        holder.binding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    holder.binding.tvTaskTitle.alpha = 1.0f
                    holder.binding.metaLayout.alpha = 1.0f
                }
            }

            val priorityLower = task.priority.lowercase()
            val textColor: Int
            val bgRes: Int

            when (priorityLower) {
                "high", "tinggi" -> {
                    textColor = context.getColor(R.color.priority_high_text)
                    bgRes = R.drawable.bg_badge_priority_high
                }

                "medium", "sedang" -> {
                    textColor = context.getColor(R.color.priority_medium_text)
                    bgRes = R.drawable.bg_badge_priority_medium
                }

                else -> {
                    textColor = context.getColor(R.color.status_green_text)
                    bgRes = R.drawable.bg_badge_priority_low
                }
            }

            holder.binding.tvPriority.setTextColor(textColor)
            holder.binding.tvPriority.setBackgroundResource(bgRes)

            holder.itemView.setOnClickListener {
                if (task.id.isNotEmpty()) {
                    val intent =
                        Intent(requireContext(), com.tokoku.orgaku.DetailTugasActivity::class.java)
                    intent.putExtra("TASK_ID", task.id)
                    startActivity(intent)
                }
            }
        }

        override fun getItemCount() = tasks.size
    }

    inner class DocumentPreviewAdapter(private val docs: List<Document>) :
        RecyclerView.Adapter<DocumentPreviewAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemDocumentBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val params = binding.root.layoutParams
            params.width = (resources.displayMetrics.widthPixels * 0.75).toInt()
            binding.root.layoutParams = params
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val doc = docs[position]
            val context = holder.binding.root.context

            holder.binding.tvDocTitle.text = doc.title
            holder.binding.tvDocCategory.text = doc.category

            val isFolder = doc.driveUrl.contains("drive.google.com/drive/folders")
            holder.binding.ivDocIcon.setImageResource(if (isFolder) R.drawable.ic_folder else R.drawable.ic_document)

            when (doc.category.lowercase()) {
                "proposal" -> {
                    holder.binding.cardIcon.setCardBackgroundColor(context.getColor(R.color.blue_proposal))
                    holder.binding.ivDocIcon.setColorFilter(context.getColor(R.color.white))
                }

                "lpj" -> {
                    holder.binding.cardIcon.setCardBackgroundColor(context.getColor(R.color.orange_lpj))
                    holder.binding.ivDocIcon.setColorFilter(context.getColor(R.color.white))
                }

                "surat" -> {
                    holder.binding.cardIcon.setCardBackgroundColor(context.getColor(R.color.purple_surat))
                    holder.binding.ivDocIcon.setColorFilter(context.getColor(R.color.white))
                }

                else -> {
                    holder.binding.cardIcon.setCardBackgroundColor(context.getColor(R.color.badge_bg_blue))
                    holder.binding.ivDocIcon.setImageResource(R.drawable.ic_globe)
                    holder.binding.ivDocIcon.setColorFilter(context.getColor(R.color.primary_dark))
                }
            }

            holder.binding.btnOpen.setOnClickListener {
                if (doc.driveUrl.isNotEmpty()) {
                    val builder = androidx.browser.customtabs.CustomTabsIntent.Builder()
                    builder.setToolbarColor(context.getColor(R.color.primary_dark))
                    builder.setShowTitle(true)
                    val customTabsIntent = builder.build()
                    customTabsIntent.launchUrl(context, android.net.Uri.parse(doc.driveUrl))
                }
            }
        }

        override fun getItemCount() = docs.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
