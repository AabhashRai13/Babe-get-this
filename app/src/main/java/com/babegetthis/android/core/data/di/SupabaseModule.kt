package com.babegetthis.android.core.data.di

import com.babegetthis.android.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Hilt module that builds the single, app-wide SupabaseClient.
// Think of this as the equivalent of `Supabase.initialize(...)` you'd call once
// in main() in a Flutter app — except here Hilt owns the instance and injects it
// wherever it's needed (e.g. the auth repository), so we never use a global.
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        // Explicit nulls: sync pushes whole rows, and clearing a column
        // (deleted_at = null when an undo revives a shared row) only reaches
        // the server if the null is actually serialized — omitted fields are
        // simply skipped by upserts, which would leave the tombstone in place.
        defaultSerializer = KotlinXSerializer(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = true
            }
        )
        // The Auth plugin manages sign-up/sign-in, the session, and automatic
        // token refresh for us — that's why we're not hand-rolling any of it.
        install(Auth)
        // Postgrest serves the delete_user RPC and shared-list row sync.
        install(Postgrest)
        // Realtime streams postgres_changes for shared lists while a list screen
        // is open. Missed events are NOT replayed on reconnect — the sync
        // engine's catch-up query is the backbone; this is only the fast path.
        install(Realtime)
    }
}
