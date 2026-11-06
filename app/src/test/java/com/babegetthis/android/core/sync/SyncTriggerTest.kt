package com.babegetthis.android.core.sync

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.sync.data.repository.SyncEngine
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// Robolectric because the three triggers are platform callbacks: the shadow
// ConnectivityManager hands back the registered network callback, and a real
// ActivityController drives Application.ActivityLifecycleCallbacks.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncTriggerTest {

    private val engine = mockk<SyncEngine>(relaxed = true)
    private val authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    private val authStateManager =
        mockk<AuthStateManager> { every { authState } returns this@SyncTriggerTest.authState }
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val trigger = SyncTrigger(engine, authStateManager, CoroutineScope(UnconfinedTestDispatcher()))

    @Before
    fun setUp() {
        // The shadow has no active network by default, so registering does not
        // fire onAvailable on its own — each test decides what fires.
        trigger.register(app)
    }

    private fun networkCallback(): ConnectivityManager.NetworkCallback {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        return shadowOf(connectivity).networkCallbacks.single()
    }

    @Test
    fun `pushSoon pushes without catching up`() {
        // Repositories kick this after a local edit; the partner's device is
        // the one that pulls, ours already has the data.
        trigger.pushSoon()

        coVerify(exactly = 1) { engine.push() }
        coVerify(exactly = 0) { engine.catchUpAllShared() }
    }

    @Test
    fun `signing in pushes and catches up`() {
        // Discovery is what materialises the account's shared lists after
        // login, and login changes neither foreground state nor connectivity.
        authState.value = AuthState.Authenticated("user-1")

        coVerify(exactly = 1) { engine.push() }
        coVerify(exactly = 1) { engine.catchUpAllShared() }
    }

    @Test
    fun `signing out is not a kick`() {
        authState.value = AuthState.Authenticated("user-1")
        authState.value = AuthState.Unauthenticated

        coVerify(exactly = 1) { engine.catchUpAllShared() }
    }

    @Test
    fun `connectivity returning kicks`() {
        networkCallback().onAvailable(mockk())

        coVerify(exactly = 1) { engine.push() }
        coVerify(exactly = 1) { engine.catchUpAllShared() }
    }

    @Test
    fun `the rest of the activity lifecycle is not a kick`() {
        // Only the 0 → 1 started transition means "foregrounded". Create,
        // resume, pause, save, stop and destroy all pass through without one.
        Robolectric.buildActivity(Activity::class.java)
            .create().start().resume().pause().saveInstanceState(Bundle()).stop().destroy()

        coVerify(exactly = 1) { engine.catchUpAllShared() }
    }

    @Test
    fun `the app coming to the foreground kicks once per foreground`() {
        val first = Robolectric.buildActivity(Activity::class.java).create().start()
        // A second Activity starting while the first is up is not a new
        // foreground.
        val second = Robolectric.buildActivity(Activity::class.java).create().start()
        coVerify(exactly = 1) { engine.catchUpAllShared() }

        // Backgrounded, then foregrounded again.
        first.stop()
        second.stop()
        first.restart()

        coVerify(exactly = 2) { engine.catchUpAllShared() }
    }
}
