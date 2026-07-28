package com.babegetthis.android.core.featureflags.data

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FeatureFlagRepository"

@Serializable
private data class FeatureFlagRow(val key: String, val enabled: Boolean)

@Singleton
class FeatureFlagRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    // Best-effort: on any failure (offline, table not created yet) flags come
    // back empty, which FeatureFlagCache treats as "everything off" — never
    // blocks app startup.
    suspend fun fetchFlags(): Map<String, Boolean> = try {
        supabaseClient.postgrest["feature_flags"]
            .select()
            .decodeList<FeatureFlagRow>()
            .associate { it.key to it.enabled }
    } catch (e: Exception) {
        Log.e(TAG, "fetchFlags failed", e)
        emptyMap()
    }
}
