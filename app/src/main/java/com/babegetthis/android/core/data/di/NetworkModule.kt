package com.babegetthis.android.core.data.di

import com.babegetthis.android.BuildConfig
import com.babegetthis.android.core.data.network.AuthAuthenticator
import com.babegetthis.android.core.data.network.AuthInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// Hilt module that provides all networking dependencies.
// Like setting up Dio with interceptors and base URL in Flutter.
//
// This module creates a single OkHttpClient and Retrofit instance shared app-wide.
// Individual API service interfaces (like AuthApiService) are created from Retrofit.

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Json parser configuration — lenient so it handles unknown keys gracefully.
    // Like JsonDecoder in Dart with ignoreUnknownKeys.
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // OkHttpClient = the HTTP engine. Like dart:io's HttpClient under Dio.
    // We attach:
    //   - AuthInterceptor: adds Bearer token to every request
    //   - HttpLoggingInterceptor: logs request/response in debug builds (like Dio's LogInterceptor)
    //   - AuthAuthenticator: handles 401 responses (auto-logout)
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authAuthenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Only log network calls in debug builds — never in release
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    // Retrofit = the API client builder. Like Dio() in Flutter.
    // It takes the OkHttpClient as its engine and the base URL from BuildConfig
    // (which changes per flavor: dev/staging/prod).
    //
    // NOTE: Authentication no longer goes through Retrofit — it moved to the
    // Supabase SDK (see SupabaseModule). This Retrofit instance is kept for the
    // upcoming audio-transcribe API, which calls our own Node backend (BASE_URL).
    // The AuthInterceptor still attaches the Supabase access token to those calls.
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
