package com.babegetthis.android.core.featureflags

import javax.inject.Inject
import javax.inject.Singleton

// In-memory only, populated once at process start (see BabeGetThisApp).
// Non-realtime by design — a flag flip in Supabase takes effect on the
// user's next cold start, not live. See
// docs/technical-decisions/003-in-app-update-and-feature-flags.md.
@Singleton
class FeatureFlagCache @Inject constructor() {

    @Volatile
    private var flags: Map<String, Boolean> = emptyMap()

    fun update(newFlags: Map<String, Boolean>) {
        flags = newFlags
    }

    fun isEnabled(key: String): Boolean = flags[key] ?: false
}
