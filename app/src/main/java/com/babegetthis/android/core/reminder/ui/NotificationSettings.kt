package com.babegetthis.android.core.reminder.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

// The per-app notification page exists from API 26; below that the app details page is the closest.
fun notificationSettingsIntent(packageName: String): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
} else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
}
