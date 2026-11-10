package com.babegetthis.android.feature.gotthis.di

import android.content.Context
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.SharedPrefsGotThisPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GotThisModule {

    @Provides
    @Singleton
    fun provideGotThisPrefs(@ApplicationContext context: Context): GotThisPrefs = SharedPrefsGotThisPrefs(context)

    @Provides
    @Singleton
    fun provideGotThisAccess(flags: FeatureFlagCache, prefs: GotThisPrefs): GotThisAccess = GotThisAccess(flags, prefs)
}
