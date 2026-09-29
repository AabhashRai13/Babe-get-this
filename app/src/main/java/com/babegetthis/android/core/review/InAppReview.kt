package com.babegetthis.android.core.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException

// The prod package, not BuildConfig.APPLICATION_ID: dev and staging add
// .dev / .staging suffixes, which have no store listing.
private const val PLAY_PACKAGE = "com.babegetthis.android"

// Asks Play to show its rating sheet. Play decides whether it actually appears
// (a per-user quota it does not disclose) and reports nothing back, so this is
// safe to call on every completed list without counting anything ourselves.
// Installs that did not come from Play (dev, staging, tests) get a silent
// no-op. See docs/superpowers/specs/2026-09-25-in-app-review-design.md.
suspend fun requestInAppReview(activity: Activity) {
    try {
        val manager = ReviewManagerFactory.create(activity)
        manager.launchReview(activity, manager.requestReview())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A failed request has no user-visible effect and nothing to retry.
    }
}

// Opens the store listing directly. Backs the Settings row, because the review
// API cannot be driven from a button: Play may silently show nothing.
fun openPlayListing(context: Context) {
    // Pinned to the Play Store app: other stores also answer market:// links,
    // and this row promises Google Play.
    val playStore = Intent(Intent.ACTION_VIEW, "market://details?id=$PLAY_PACKAGE".toUri())
        .setPackage("com.android.vending")
    try {
        context.startActivity(playStore)
    } catch (e: ActivityNotFoundException) {
        // No Play Store on this device; the browser can still show the listing.
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE".toUri()),
            )
        } catch (e: ActivityNotFoundException) {
            // No browser either (kiosk or restricted profile): nothing to open.
        }
    }
}
