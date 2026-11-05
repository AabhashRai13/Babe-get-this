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

    // False until a fetch has succeeded. A failed fetch never calls update, so
    // the cache stays unloaded and "off" can be told apart from "unknown".
    @Volatile
    private var loaded = false

    fun update(newFlags: Map<String, Boolean>) {
        flags = newFlags
        loaded = true
    }

    fun isEnabled(key: String): Boolean = flags[key] ?: false

    /**
     * True only when a fetch has landed and [key] is not on in it (false or
     * absent). An unloaded cache (offline cold start) is never known off, so a
     * kill switch acting on this does not fire just because the network is down.
     */
    fun isKnownOff(key: String): Boolean = loaded && flags[key] != true
}
