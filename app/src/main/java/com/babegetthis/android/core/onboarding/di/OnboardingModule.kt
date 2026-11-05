package com.babegetthis.android.core.onboarding.di

import android.content.Context
import com.babegetthis.android.core.onboarding.data.repository.WelcomeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {

    // Plain prefs: a single flag, nothing secret. An update keeps the original
    // firstInstallTime and moves lastUpdateTime; a fresh install has them equal.
    @Provides
    @Singleton
    fun provideWelcomeRepository(@ApplicationContext context: Context): WelcomeRepository = WelcomeRepository(
        prefs = context.getSharedPreferences("welcome", Context.MODE_PRIVATE),
        isUpdateInstall = {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime != info.lastUpdateTime
        },
    )
}
