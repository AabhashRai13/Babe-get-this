package com.babegetthis.android.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

// EncryptedSharedPreferences keeps its keyset inside the prefs file, encrypted
// with a key that exists only in this device's Keystore. Anything that brings
// the file back without that key (Auto Backup restore, device transfer, a
// Keystore reset) leaves a keyset nothing can decrypt, and create() throws on
// every launch. The contents are unreadable either way, so the file is started
// over rather than crashing the app. Covered by androidTest/PinStoreTest and
// TokenManagerTest, since the Keystore does not exist under Robolectric.
fun openEncryptedPrefs(context: Context, name: String): SharedPreferences =
    runCatching { create(context, name) }.getOrElse { e ->
        if (e !is GeneralSecurityException && e !is IOException) throw e
        context.deleteSharedPreferences(name)
        create(context, name)
    }

private fun create(context: Context, name: String): SharedPreferences = EncryptedSharedPreferences.create(
    context,
    name,
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)
