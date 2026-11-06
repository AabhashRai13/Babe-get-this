package com.babegetthis.android.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

const val PRIVACY_POLICY_URL = "https://babegetthis.com/privacy-policy.html"

// Settings and the lists-moved notice both link here.
fun openPrivacyPolicy(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
    } catch (e: ActivityNotFoundException) {
        // No browser (kiosk or restricted profile): nothing to open.
    }
}
