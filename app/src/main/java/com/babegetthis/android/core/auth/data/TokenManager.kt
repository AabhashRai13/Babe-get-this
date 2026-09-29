package com.babegetthis.android.core.auth.data

import android.content.Context
import android.content.SharedPreferences
import com.babegetthis.android.core.data.local.openEncryptedPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Securely stores the auth token and user ID using EncryptedSharedPreferences.
// The rest of the app never touches SharedPreferences directly; it goes through this class.

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        private const val PREFS_NAME = "bgt_secure_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }

    // Lazy so we only create the encrypted prefs when first needed
    private val prefs: SharedPreferences by lazy { openEncryptedPrefs(context, PREFS_NAME) }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun saveUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    // clear() wipes all keys including the new name/email — no change needed.
    fun clear() {
        prefs.edit().clear().apply()
    }
}
