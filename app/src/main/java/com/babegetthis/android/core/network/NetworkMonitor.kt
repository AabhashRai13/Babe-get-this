package com.babegetthis.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Cheap, synchronous "is there usable internet right now?" check, backed by
// ConnectivityManager — the OS's source of truth for connectivity.
//
// We gate genuinely-network calls (auth, voice) on this so an offline user gets
// a clean "No internet connection." immediately, instead of waiting for a long
// timeout or leaking a raw provider exception string. Local/Room calls are NOT
// gated — this app is offline-first and must keep working with no internet.

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isOnline(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        // INTERNET  = the network claims it can reach the internet.
        // VALIDATED = Android actually confirmed it works — this is what catches
        //             "connected to Wi-Fi but the captive portal/router has no
        //             real internet", which a plain INTERNET check would miss.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
