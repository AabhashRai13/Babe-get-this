package com.babegetthis.android.core.sync.data.repository

import kotlinx.coroutines.flow.StateFlow

// Small device-local facts about list ownership that must outlive the rows
// they describe (docs/technical-decisions/008). Prefs, not Room: eviction
// deletes the rows, and these have to survive it. An interface so the engine
// and repositories can be tested against a fake, like SyncPointStore.
interface ListOwnershipStore {

    // Guest lists moved into the account that the user hasn't been told about
    // yet. Observable: the claim runs in the background while the lists screen
    // is showing.
    val adoptionNotice: StateFlow<Int>
    fun setAdoptionNotice(count: Int)

    // The first claim after the ownership update also takes the existing shared
    // replicas, which have no recorded owner. Only once.
    fun isAccountListsMigrated(): Boolean
    fun markAccountListsMigrated()

    // Ids of lists the user locked on this device. The lock is per-device and
    // never syncs, so it would be lost when sign-out evicts the list; the list
    // is re-locked when it comes back.
    fun lockedListIds(): Set<String>
    fun rememberLock(listId: String, locked: Boolean)
    fun forgetAllLocks()
}
