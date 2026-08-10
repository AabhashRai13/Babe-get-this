package com.babegetthis.android.core.sync.di

import android.content.Context
import com.babegetthis.android.core.auth.data.TokenManager
import com.babegetthis.android.core.sync.SyncKicker
import com.babegetthis.android.core.sync.SyncTrigger
import com.babegetthis.android.core.sync.data.repository.ShareRepository
import com.babegetthis.android.core.sync.data.local.PrefsSyncPointStore
import com.babegetthis.android.core.sync.data.remote.SharedListRemote
import com.babegetthis.android.core.sync.data.remote.SupabaseSharedListRemote
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSharedListRemote(client: SupabaseClient): SharedListRemote =
        SupabaseSharedListRemote(client)

    @Provides
    @Singleton
    fun provideSyncPointStore(@ApplicationContext context: Context): SyncPointStore =
        PrefsSyncPointStore(context)

    @Provides
    @Singleton
    fun provideSyncEngine(
        listDao: ShoppingListDao,
        itemDao: ShoppingItemDao,
        remote: SharedListRemote,
        syncPoints: SyncPointStore,
        tokenManager: TokenManager,
    ): SyncEngine = SyncEngine(listDao, itemDao, remote, syncPoints) { tokenManager.getUserId() }

    @Provides
    @Singleton
    fun provideSyncTrigger(
        engine: SyncEngine,
        authStateManager: com.babegetthis.android.core.auth.data.AuthStateManager,
    ): SyncTrigger = SyncTrigger(engine, authStateManager)

    @Provides
    @Singleton
    fun provideSyncKicker(trigger: SyncTrigger): SyncKicker = trigger

    @Provides
    @Singleton
    fun provideShareRepository(
        listDao: ShoppingListDao,
        itemDao: ShoppingItemDao,
        remote: SharedListRemote,
        engine: SyncEngine,
        tokenManager: TokenManager,
    ): ShareRepository = ShareRepository(listDao, itemDao, remote, engine, { tokenManager.getUserId() })
}
