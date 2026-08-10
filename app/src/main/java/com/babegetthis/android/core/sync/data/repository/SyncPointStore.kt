package com.babegetthis.android.core.sync.data.repository

// Per-list high-water mark: the newest server updated_at (ISO string) we've
// applied. Catch-up queries resume from here. Stored as the raw ISO string —
// Supabase always returns UTC in one format, so lexicographic max == newest.
interface SyncPointStore {
    fun get(listId: String): String?
    fun set(listId: String, iso: String)

    // Drop one list's mark so the next catchUp pulls everything — join()'s
    // refresh-on-rejoin path.
    fun remove(listId: String)

    // Wipe all marks — part of shared-replica eviction on explicit sign-out.
    fun clear()
}
