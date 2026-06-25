package com.babegetthis.android.core.data.di

import com.babegetthis.android.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Hilt module that builds the single, app-wide SupabaseClient.
// Think of this as the equivalent of `Supabase.initialize(...)` you'd call once
// in main() in a Flutter app — except here Hilt owns the instance and injects it
// wherever it's needed (e.g. the auth repository), so we never use a global.
//
// We install only the Auth plugin for now. When realtime shared lists arrive,
// we'll add `install(Realtime)` (and `install(Postgrest)` if we read/write rows)
// to this same builder — that's the whole change.
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        // The Auth plugin manages sign-up/sign-in, the session, and automatic
        // token refresh for us — that's why we're not hand-rolling any of it.
        install(Auth)
    }
}
