package com.babegetthis.android.core.pin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Typed wrapper over an EncryptedSharedPreferences file for PIN state. Mirrors
// TokenManager, but MUST be a separate file (bgt_pin_prefs, not bgt_secure_prefs):
// TokenManager.clear() wipes its whole file on logout, and the PIN has to
// survive logout and account switching. Separate files make that impossible to
// get wrong by construction.
@Singleton
class PinStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "bgt_pin_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_RECOVERY_SALT = "recovery_salt"
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_LOCKOUT_WALL = "lockout_until_wall"
        private const val KEY_LOCKOUT_ELAPSED = "lockout_until_elapsed"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(v) = prefs.edit().putString(KEY_PIN_HASH, v).apply()

    var pinSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        set(v) = prefs.edit().putString(KEY_PIN_SALT, v).apply()

    var recoveryHash: String?
        get() = prefs.getString(KEY_RECOVERY_HASH, null)
        set(v) = prefs.edit().putString(KEY_RECOVERY_HASH, v).apply()

    var recoverySalt: String?
        get() = prefs.getString(KEY_RECOVERY_SALT, null)
        set(v) = prefs.edit().putString(KEY_RECOVERY_SALT, v).apply()

    var attempts: Int
        get() = prefs.getInt(KEY_ATTEMPTS, 0)
        set(v) = prefs.edit().putInt(KEY_ATTEMPTS, v).apply()

    // Lockout expiry stored on BOTH clocks. currentTimeMillis is user-settable;
    // elapsedRealtime resets on reboot. Honoring whichever leaves more time
    // means defeating a lockout needs a reboot AND a clock change.
    var lockoutUntilWall: Long
        get() = prefs.getLong(KEY_LOCKOUT_WALL, 0L)
        set(v) = prefs.edit().putLong(KEY_LOCKOUT_WALL, v).apply()

    var lockoutUntilElapsed: Long
        get() = prefs.getLong(KEY_LOCKOUT_ELAPSED, 0L)
        set(v) = prefs.edit().putLong(KEY_LOCKOUT_ELAPSED, v).apply()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
