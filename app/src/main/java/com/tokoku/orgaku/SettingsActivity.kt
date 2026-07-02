package com.tokoku.orgaku

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tokoku.orgaku.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        fetchCurrentSettings()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Change Password
        binding.menuChangePassword.root.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        // Language
        binding.menuLanguage.root.setOnClickListener {
            showLanguageDialog()
        }

        // Theme
        binding.menuTheme.root.setOnClickListener {
            showThemeDialog()
        }

        // Support
        binding.menuSupport.root.setOnClickListener {
            contactSupport()
        }

        // About
        binding.menuAbout.root.setOnClickListener {
            showAboutDialog()
        }

        // Kebijakan Privasi
        binding.menuPrivacy.root.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/orgaku/halaman-muka"))
            startActivity(intent)
        }
    }

    private fun contactSupport() {
        val phoneNumber = "+6285939293755"
        val message = "Halo Support Orgaku, saya ingin bertanya terkait aplikasi..."
        val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${
            java.net.URLEncoder.encode(
                message,
                "UTF-8"
            )
        }"

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse(url)
            intent.setPackage("com.whatsapp")
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to Web
            val webIntent = Intent(Intent.ACTION_VIEW)
            webIntent.data = android.net.Uri.parse(
                "https://wa.me/6285939293755?text=${
                    java.net.URLEncoder.encode(
                        message,
                        "UTF-8"
                    )
                }"
            )
            startActivity(webIntent)
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle("Orgaku v1.0.2  Premium Build")
            .setMessage("Solusi manajemen internal organisasi yang cerdas, cepat, dan terintegrasi.\n\nProudly crafted in Indonesia to empower your organization.")
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun fetchCurrentSettings() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val lang = document.getString("app_language") ?: "id"
                    val theme = document.getString("app_theme") ?: "light"

                    updateSettingsUI(lang, theme)
                }
            }
    }

    private fun updateSettingsUI(lang: String, theme: String) {
        binding.menuLanguage.tvMenuValue.text =
            if (lang == "id") getString(R.string.bahasa_indo) else getString(R.string.bahasa_en)
        binding.menuTheme.tvMenuValue.text =
            if (theme == "light") getString(R.string.tema_terang) else getString(R.string.tema_gelap)
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(getString(R.string.bahasa_indo), getString(R.string.bahasa_en))
        val codes = arrayOf("id", "en")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pilih_bahasa))
            .setItems(languages) { _, which ->
                val selectedLang = codes[which]
                saveSettings("app_language", selectedLang)
                binding.menuLanguage.tvMenuValue.text = languages[which]
                applyLanguage(selectedLang)
            }
            .show()
    }

    private fun applyLanguage(langCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
        // setApplicationLocales automatically triggers activity recreation
    }

    private fun showThemeDialog() {
        val themes = arrayOf(getString(R.string.tema_terang), getString(R.string.tema_gelap))
        val codes = arrayOf("light", "dark")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pilih_tema))
            .setItems(themes) { _, which ->
                val selectedTheme = codes[which]
                saveSettings("app_theme", selectedTheme)
                binding.menuTheme.tvMenuValue.text = themes[which]

                applyTheme(selectedTheme)
            }
            .show()
    }

    private fun applyTheme(theme: String) {
        val mode = if (theme == "dark") {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun saveSettings(field: String, value: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update(field, value)
            .addOnSuccessListener {
                showTopSnackbar(getString(R.string.settings_saved))
            }
            .addOnFailureListener {
                showTopSnackbar(getString(R.string.settings_save_failed))
            }
    }

    private fun showTopSnackbar(message: String) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        val snackView = snack.view

        val params = snackView.layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        } else if (params is CoordinatorLayout.LayoutParams) {
            params.gravity = Gravity.TOP
            params.topMargin = 60
            params.leftMargin = 40
            params.rightMargin = 40
            snackView.layoutParams = params
        }

        snackView.setBackgroundResource(R.drawable.bg_snackbar)
        snackView.elevation = 10f
        snack.setTextColor(Color.WHITE)
        snack.show()
    }
}
