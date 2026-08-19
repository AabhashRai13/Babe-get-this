package com.babegetthis.android.core.data.di

import com.babegetthis.android.core.auth.data.AuthRepository
import com.babegetthis.android.core.auth.data.FakeAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Dev flavor — binds the FAKE auth repository.
// No real network calls. Returns hardcoded responses after a delay.
// This module only exists in the dev source set (app/src/dev/).

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository
}
