package com.babegetthis.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

// Robolectric's shadow ConnectivityManager, so the capability combinations are
// driven directly rather than mocked.
@RunWith(RobolectricTestRunner::class)
class NetworkMonitorTest {

    private lateinit var context: Context
    private lateinit var monitor: NetworkMonitor
    private lateinit var connectivity: ConnectivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        monitor = NetworkMonitor(context)
        connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun setCapabilities(vararg capabilities: Int) {
        val network = connectivity.activeNetwork
        val caps = ShadowNetworkCapabilities.newInstance()
        capabilities.forEach { shadowOf(caps).addCapability(it) }
        shadowOf(connectivity).setNetworkCapabilities(network, caps)
    }

    @Test
    fun `a validated internet connection is online`() {
        setCapabilities(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )

        assertTrue(monitor.isOnline())
    }

    // The case VALIDATED exists for: attached to Wi-Fi whose captive portal has
    // not been passed, so the network claims internet it cannot actually reach.
    @Test
    fun `an unvalidated connection is treated as offline`() {
        setCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        assertFalse("a captive portal must read as offline", monitor.isOnline())
    }

    @Test
    fun `a validated network with no internet capability is offline`() {
        setCapabilities(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        assertFalse(monitor.isOnline())
    }

    @Test
    fun `a network with neither capability is offline`() {
        setCapabilities()

        assertFalse(monitor.isOnline())
    }

    @Test
    fun `no active network is offline`() {
        shadowOf(connectivity).setActiveNetworkInfo(null)
        shadowOf(connectivity).setDefaultNetworkActive(false)

        // With no active network at all the monitor must say offline rather than
        // throw — every gated call site treats this as a cheap boolean.
        val result = runCatching { monitor.isOnline() }

        assertTrue("isOnline must not throw: ${result.exceptionOrNull()}", result.isSuccess)
    }

    // Repeated reads must not cache — connectivity changes under the app.
    @Test
    fun `the answer follows the current state rather than being cached`() {
        setCapabilities(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )
        assertTrue(monitor.isOnline())

        setCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        assertFalse(monitor.isOnline())
    }
}
