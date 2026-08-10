package com.babegetthis.android.core.sync

// Fire-and-forget "push soon" seam. Repositories call this after marking rows
// pendingSync so partner devices see edits promptly — without the UI write
// waiting on the network, and without repositories knowing the engine exists.
// A dropped kick is never data loss: the flag stays set and the next
// foreground/connectivity kick pushes it.
fun interface SyncKicker {
    fun pushSoon()
}
