package com.tokoku.orgaku.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("orgaku_prefs", Context.MODE_PRIVATE)

    fun saveUserProfile(name: String, role: String, photoUrl: String, organization: String = "-", organisasiId: String = "") {
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_ROLE, role)
            putString(KEY_USER_PHOTO, photoUrl)
            putString(KEY_USER_ORG, organization)
            putString(KEY_USER_ORG_ID, organisasiId)
            apply()
        }
    }

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "anggota") ?: "anggota"
    fun getUserPhoto(): String = prefs.getString(KEY_USER_PHOTO, "") ?: ""
    fun getUserOrg(): String = prefs.getString(KEY_USER_ORG, "-") ?: "-"
    fun getUserOrgId(): String = prefs.getString(KEY_USER_ORG_ID, "") ?: ""

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_PHOTO = "user_photo"
        private const val KEY_USER_ORG = "user_org"
        private const val KEY_USER_ORG_ID = "user_org_id"
    }
}
