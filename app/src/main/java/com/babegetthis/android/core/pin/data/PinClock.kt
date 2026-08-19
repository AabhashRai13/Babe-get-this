package com.babegetthis.android.core.pin.data

import android.os.SystemClock
import javax.inject.Inject

// Two clocks so throttling can't be skipped by changing the device date
// (wall) alone or by rebooting (elapsed) alone. Injected so tests can fake it.
interface PinClock {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
}

class SystemPinClock @Inject constructor() : PinClock {
    override fun wallMillis(): Long = System.currentTimeMillis()
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}
