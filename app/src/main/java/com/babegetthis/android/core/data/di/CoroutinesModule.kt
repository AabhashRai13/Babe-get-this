package com.babegetthis.android.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
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

// The dispatcher for blocking disk work (SharedPreferences, files). Injected
// rather than referenced as Dispatchers.IO at the call site so a unit test can
// substitute a TestDispatcher and prove the work was dispatched there.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
