package com.babegetthis.android.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

// A coroutine scope that lives as long as the whole app, NOT tied to any one
// screen/ViewModel. Use it for fire-and-forget work that must finish even after
// the screen that triggered it is gone — e.g. deleting an empty list as the
// user navigates away (viewModelScope would be cancelled mid-delete).

// SupervisorJob = one failed child job doesn't cancel its siblings.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
