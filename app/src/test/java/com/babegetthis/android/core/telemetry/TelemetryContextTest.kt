package com.babegetthis.android.core.telemetry

import com.babegetthis.android.core.network.NetworkMonitor
import com.babegetthis.android.core.telemetry.model.Screen
import com.babegetthis.android.core.util.Throttle
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

// The data-shape keys (item count, sync queue depth) come from COUNT queries
// against Room. They used to run on every screen change, which on a quick
// back-and-forth is a burst of disk reads for numbers that have not changed.
// A throttle bounds that: at most one refresh per window.
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryContextTest {

    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val itemDao = mockk<ShoppingItemDao>()
    private val listDao = mockk<ShoppingListDao>()
    private val networkMonitor = mockk<NetworkMonitor>()

    private var now = 0L

    private val context = TelemetryContext(
        analytics = mockk(relaxed = true),
        crashReporter = crashReporter,
        networkMonitor = networkMonitor,
        consent = mockk(relaxed = true),
        itemDao = itemDao,
        listDao = listDao,
        applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
        dataShapeRefresh = Throttle(windowMillis = 30_000L) { now },
    )

    init {
        every { networkMonitor.isOnline() } returns true
        coEvery { itemDao.countActiveItems() } returns 12
        coEvery { itemDao.countPendingSync() } returns 1
        coEvery { listDao.countPendingSync() } returns 0
    }

    @Test
    fun `app start applies the persisted consent before anything is recorded`() {
        // A user who opted out last run must not have this launch collected
        // while we get around to asking.
        val consent = mockk<TelemetryConsent>(relaxed = true)
        val context = TelemetryContext(
            analytics = mockk(relaxed = true),
            crashReporter = crashReporter,
            networkMonitor = networkMonitor,
            consent = consent,
            itemDao = itemDao,
            listDao = listDao,
            applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
            dataShapeRefresh = Throttle(windowMillis = 30_000L) { now },
        )

        context.onAppStart()

        verify { consent.applyPersisted() }
        verify { crashReporter.setKey(CrashKey.Flavor, any()) }
        verify { crashReporter.setKey(CrashKey.AuthState, "signed_out") }
    }

    @Test
    fun `a signed-in user is identified by id in both analytics and crash reports`() {
        val analytics = mockk<AnalyticsRepository>(relaxed = true)
        val context = TelemetryContext(
            analytics = analytics,
            crashReporter = crashReporter,
            networkMonitor = networkMonitor,
            consent = mockk(relaxed = true),
            itemDao = itemDao,
            listDao = listDao,
            applicationScope = CoroutineScope(UnconfinedTestDispatcher()),
            dataShapeRefresh = Throttle(windowMillis = 30_000L) { now },
        )

        context.onUserChanged("user-1")

        verify { analytics.setUser("user-1") }
        verify { crashReporter.setUser("user-1") }
        verify { crashReporter.setKey(CrashKey.AuthState, "signed_in") }
    }

    @Test
    fun `signing out clears the identity`() {
        context.onUserChanged(null)

        verify { crashReporter.setUser(null) }
        verify { crashReporter.setKey(CrashKey.AuthState, "signed_out") }
    }

    @Test
    fun `an offline screen change records the network state as offline`() {
        every { networkMonitor.isOnline() } returns false

        context.onScreen(Screen.ShoppingList)

        verify { crashReporter.setKey(CrashKey.NetworkState, "offline") }
    }

    @Test
    fun `the first screen change queries the data shape`() {
        context.onScreen(Screen.ShoppingList)

        coVerify(exactly = 1) { itemDao.countActiveItems() }
        verify { crashReporter.setKey(CrashKey.ItemCountBucket, any()) }
        verify { crashReporter.setKey(CrashKey.SyncQueueDepth, any()) }
    }

    @Test
    fun `screen changes inside the window do not query again`() {
        context.onScreen(Screen.ShoppingList)
        now += 5_000L
        context.onScreen(Screen.ShoppingItems)
        now += 5_000L
        context.onScreen(Screen.ShoppingList)

        coVerify(exactly = 1) { itemDao.countActiveItems() }
        coVerify(exactly = 1) { itemDao.countPendingSync() }
        coVerify(exactly = 1) { listDao.countPendingSync() }
    }

    @Test
    fun `a screen change after the window queries again`() {
        context.onScreen(Screen.ShoppingList)
        now += 30_000L
        context.onScreen(Screen.ShoppingItems)

        coVerify(exactly = 2) { itemDao.countActiveItems() }
    }

    @Test
    fun `the per-screen keys are still set on every change`() {
        // Only the database reads are throttled. Where the user is and whether
        // they are online are cheap and must stay current.
        context.onScreen(Screen.ShoppingList)
        context.onScreen(Screen.ShoppingItems)

        verify(exactly = 2) { crashReporter.setKey(CrashKey.LastScreen, any()) }
        verify(exactly = 2) { crashReporter.setKey(CrashKey.NetworkState, any()) }
    }
}
