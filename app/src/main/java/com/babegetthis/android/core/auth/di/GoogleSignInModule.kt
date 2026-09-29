package com.babegetthis.android.core.auth.di

import com.babegetthis.android.core.auth.ui.CredentialManagerTokenRequester
import com.babegetthis.android.core.auth.ui.GoogleTokenRequester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Its own module (not folded into the per-flavor AuthModule) so the end-to-end
// suite can replace just the Credential Manager sheet with a fixed token.
@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleSignInModule {
    @Binds
    abstract fun bindGoogleTokenRequester(impl: CredentialManagerTokenRequester): GoogleTokenRequester
}
