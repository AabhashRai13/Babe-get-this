package com.babegetthis.android.core.data.di

import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.RealAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Prod flavor — binds the REAL auth repository.
// Hits the production API server for actual network calls.

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: RealAuthRepository): AuthRepository
}
