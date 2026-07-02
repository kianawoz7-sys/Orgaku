package com.tokoku.orgaku

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.tokoku.orgaku.databinding.ActivityMainBinding
import com.tokoku.orgaku.ui.dashboard.DashboardFragment
import com.tokoku.orgaku.ui.meeting.JadwalFragment
import com.tokoku.orgaku.ui.profile.ProfilFragment
import com.tokoku.orgaku.ui.task.TugasFragment
import com.tokoku.orgaku.util.SessionManager
import com.tokoku.orgaku.ui.absensi.AbsensiFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager

    // ===================================================================
    // APPROACH B: Hide/Show Strategy + Configuration Change Safe
    //
    // Fragment properties dideklarasikan sebagai lateinit var (BUKAN lazy).
    // Nilainya di-assign di onCreate() berdasarkan savedInstanceState:
    //   - null  → First launch: buat instance baru, add dengan TAG
    //   - !null → Recreated (theme/language change): CARI fragment lama
    //             via findFragmentByTag(), JANGAN buat instance baru!
    // ===================================================================
    private lateinit var fragmentDashboard: DashboardFragment
    private lateinit var fragmentJadwal: JadwalFragment
    private lateinit var fragmentAbsensi: AbsensiFragment
    private lateinit var fragmentTugas: TugasFragment
    private lateinit var fragmentProfil: ProfilFragment

    // Fragment tags — konstanta agar tidak typo
    companion object {
        private const val TAG_DASHBOARD = "TAG_DASHBOARD"
        private const val TAG_JADWAL    = "TAG_JADWAL"
        private const val TAG_ABSENSI   = "TAG_ABSENSI"
        private const val TAG_TUGAS     = "TAG_TUGAS"
        private const val TAG_PROFIL    = "TAG_PROFIL"

        // Key untuk menyimpan tab aktif ke savedInstanceState
        private const val KEY_ACTIVE_TAB = "KEY_ACTIVE_TAB"
    }

    // activeFragment dan currentIndex perlu lateinit/var karena
    // nilainya bergantung pada savedInstanceState
    private lateinit var activeFragment: Fragment
    private var currentIndex = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getAndSaveFcmToken()
        }
    }

    fun setSelectedTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        if (savedInstanceState == null) {
            // ─────────────────────────────────────────────────────────
            // FIRST LAUNCH: Buat instance baru, add ke FM dengan TAG
            // ─────────────────────────────────────────────────────────
            fragmentDashboard = DashboardFragment()
            fragmentJadwal    = JadwalFragment()
            fragmentAbsensi   = AbsensiFragment()
            fragmentTugas     = TugasFragment()
            fragmentProfil    = ProfilFragment()

            val startTab = intent.getIntExtra("START_TAB", R.id.nav_beranda)
            currentIndex  = getTabIndex(startTab)
            activeFragment = resolveFragment(startTab)

            supportFragmentManager.beginTransaction().apply {
                add(R.id.nav_host_fragment, fragmentDashboard, TAG_DASHBOARD)
                add(R.id.nav_host_fragment, fragmentJadwal,    TAG_JADWAL)
                add(R.id.nav_host_fragment, fragmentAbsensi,   TAG_ABSENSI)
                add(R.id.nav_host_fragment, fragmentTugas,     TAG_TUGAS)
                add(R.id.nav_host_fragment, fragmentProfil,    TAG_PROFIL)

                // Sembunyikan semua, lalu tampilkan hanya yang aktif
                hide(fragmentDashboard)
                hide(fragmentJadwal)
                hide(fragmentAbsensi)
                hide(fragmentTugas)
                hide(fragmentProfil)
                show(activeFragment)
            }.commit()

            binding.bottomNav.selectedItemId = startTab

        } else {
            // ─────────────────────────────────────────────────────────
            // RECREATED (theme / language change / rotation):
            // FragmentManager sudah restore fragment dari back stack.
            // JANGAN buat instance baru! Cukup findFragmentByTag().
            // ─────────────────────────────────────────────────────────
            fragmentDashboard = supportFragmentManager
                .findFragmentByTag(TAG_DASHBOARD) as DashboardFragment
            fragmentJadwal    = supportFragmentManager
                .findFragmentByTag(TAG_JADWAL)    as JadwalFragment
            fragmentAbsensi   = supportFragmentManager
                .findFragmentByTag(TAG_ABSENSI)   as AbsensiFragment
            fragmentTugas     = supportFragmentManager
                .findFragmentByTag(TAG_TUGAS)     as TugasFragment
            fragmentProfil    = supportFragmentManager
                .findFragmentByTag(TAG_PROFIL)    as ProfilFragment

            // Pulihkan tab index yang aktif sebelum recreation
            currentIndex   = savedInstanceState.getInt(KEY_ACTIVE_TAB, 0)
            activeFragment = indexToFragment(currentIndex)

            // Sinkronkan state bottomNav tanpa memicu listener
            val activeNavId = indexToNavId(currentIndex)
            binding.bottomNav.selectedItemId = activeNavId
        }

        setupBottomNav()
        askNotificationPermission()
    }

    // Simpan tab yang aktif sebelum Activity di-destroy oleh config change
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_TAB, currentIndex)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: ID nav menu → Fragment instance (sudah di-init)
    // ─────────────────────────────────────────────────────────────────────
    private fun resolveFragment(navId: Int): Fragment {
        return when (navId) {
            R.id.nav_jadwal -> fragmentJadwal
            R.id.nav_tugas  -> fragmentTugas
            R.id.nav_profil -> fragmentProfil
            R.id.nav_qr     -> fragmentAbsensi
            else            -> fragmentDashboard
        }
    }

    // Helper: index tab → Fragment instance
    private fun indexToFragment(index: Int): Fragment {
        return when (index) {
            1    -> fragmentJadwal
            2    -> fragmentAbsensi
            3    -> fragmentTugas
            4    -> fragmentProfil
            else -> fragmentDashboard
        }
    }

    // Helper: index tab → ID menu BottomNav
    private fun indexToNavId(index: Int): Int {
        return when (index) {
            1    -> R.id.nav_jadwal
            2    -> R.id.nav_qr
            3    -> R.id.nav_tugas
            4    -> R.id.nav_profil
            else -> R.id.nav_beranda
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                getAndSaveFcmToken()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            getAndSaveFcmToken()
        }
    }

    private fun getAndSaveFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val token  = task.result
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@addOnCompleteListener
                FirebaseFirestore.getInstance().collection("users")
                    .document(userId)
                    .update("fcmToken", token)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "FCM Token Error: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val startTab = intent.getIntExtra("START_TAB", R.id.nav_beranda)
        binding.bottomNav.selectedItemId = startTab
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val newIndex = getTabIndex(item.itemId)

            // Hindari redundant switching
            if (newIndex == currentIndex) return@setOnItemSelectedListener true

            val targetFragment = resolveFragment(item.itemId)
            val transaction    = supportFragmentManager.beginTransaction()

            // Animasi directional tetap jalan
            if (newIndex > currentIndex) {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }

            // Hide yang lama, show yang baru — TIDAK ada destroy!
            transaction.hide(activeFragment)
            transaction.show(targetFragment)
            transaction.commit()

            activeFragment = targetFragment
            currentIndex   = newIndex
            true
        }

        binding.fabQr.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_qr
        }
    }

    private fun getTabIndex(id: Int): Int {
        return when (id) {
            R.id.nav_beranda -> 0
            R.id.nav_jadwal  -> 1
            R.id.nav_qr      -> 2
            R.id.nav_tugas   -> 3
            R.id.nav_profil  -> 4
            else             -> 0
        }
    }
}
