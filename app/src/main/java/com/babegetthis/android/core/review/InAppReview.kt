package com.babegetthis.android.core.review

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException

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
