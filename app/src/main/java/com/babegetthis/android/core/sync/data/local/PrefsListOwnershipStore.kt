package com.babegetthis.android.core.sync.data.local

import android.content.Context
import androidx.core.content.edit
import com.babegetthis.android.core.sync.data.repository.ListOwnershipStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Plain SharedPreferences, like PrefsSyncPointStore: a counter, a flag and a
// set of list ids — nothing secret. Must survive eviction, so not Room.
class PrefsListOwnershipStore(context: Context) : ListOwnershipStore {

    private val prefs = context.getSharedPreferences("list_ownership", Context.MODE_PRIVATE)

    private val notice = MutableStateFlow(prefs.getInt(KEY_ADOPTION_NOTICE, 0))
    override val adoptionNotice: StateFlow<Int> = notice.asStateFlow()

    override fun setAdoptionNotice(count: Int) {
        prefs.edit { putInt(KEY_ADOPTION_NOTICE, count) }
        notice.value = count
    }

    override fun isAccountListsMigrated(): Boolean = prefs.getBoolean(KEY_MIGRATED, false)

    override fun markAccountListsMigrated() {
        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    override fun lockedListIds(): Set<String> = prefs.getStringSet(KEY_LOCKED, emptySet()).orEmpty().toSet()

    override fun rememberLock(listId: String, locked: Boolean) {
        val ids = lockedListIds().let { if (locked) it + listId else it - listId }
        prefs.edit { putStringSet(KEY_LOCKED, ids) }
    }

    override fun forgetAllLocks() {
        prefs.edit { remove(KEY_LOCKED) }
    }

    private companion object {
        const val KEY_ADOPTION_NOTICE = "adoption_notice"
        const val KEY_MIGRATED = "account_lists_migrated"
        const val KEY_LOCKED = "locked_list_ids"
    }
}
