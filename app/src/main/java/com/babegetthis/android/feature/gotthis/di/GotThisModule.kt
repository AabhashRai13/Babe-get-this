package com.babegetthis.android.feature.gotthis.di

import android.content.Context
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.featureflags.FeatureFlagCache
import com.babegetthis.android.core.reminder.ReminderClock
import com.babegetthis.android.core.sync.data.repository.SyncPointStore
import com.babegetthis.android.feature.gotthis.data.GotThisAccess
import com.babegetthis.android.feature.gotthis.data.GotThisBackfill
import com.babegetthis.android.feature.gotthis.data.GotThisSync
import com.babegetthis.android.feature.gotthis.data.TickStore
import com.babegetthis.android.feature.gotthis.data.local.GotThisPrefs
import com.babegetthis.android.feature.gotthis.data.local.SharedPrefsGotThisPrefs
import com.babegetthis.android.feature.gotthis.data.remote.GotThisRemote
import com.babegetthis.android.feature.gotthis.data.remote.SupabaseGotThisRemote
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
object GotThisModule {

    @Provides
    @Singleton
    fun provideGotThisPrefs(@ApplicationContext context: Context): GotThisPrefs = SharedPrefsGotThisPrefs(context)

    @Provides
    @Singleton
    fun provideGotThisAccess(flags: FeatureFlagCache, prefs: GotThisPrefs): GotThisAccess = GotThisAccess(flags, prefs)

    @Provides
    @Singleton
    fun provideTickStore(
        db: AppDatabase,
        itemDao: ShoppingItemDao,
        listDao: ShoppingListDao,
        clock: ReminderClock,
    ): TickStore = TickStore(db, itemDao, listDao, db.purchaseDao(), clock)

    @Provides
    @Singleton
    fun provideGotThisRemote(client: SupabaseClient): GotThisRemote = SupabaseGotThisRemote(client)

    @Provides
    @Singleton
    fun provideGotThisSync(
        db: AppDatabase,
        remote: GotThisRemote,
        prefs: GotThisPrefs,
        syncPoints: SyncPointStore,
        clock: ReminderClock,
    ): GotThisSync = GotThisSync(db, remote, prefs, syncPoints, clock)

    @Provides
    @Singleton
    fun provideGotThisBackfill(db: AppDatabase, prefs: GotThisPrefs, clock: ReminderClock): GotThisBackfill =
        GotThisBackfill(db, prefs, clock)
}
