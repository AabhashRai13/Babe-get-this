package com.babegetthis.android.core.sync.data.local

import android.content.Context
import com.babegetthis.android.core.sync.data.repository.SyncPointStore

// Plain SharedPreferences (not encrypted — these are just timestamps), keyed
// by list id. A Room column was the alternative; prefs keep the schema
// untouched and the store trivially fakeable.
class PrefsSyncPointStore(context: Context) : SyncPointStore {

    private val prefs = context.getSharedPreferences("sync_points", Context.MODE_PRIVATE)

    override fun get(listId: String): String? = prefs.getString(listId, null)

    override fun set(listId: String, iso: String) {
        prefs.edit().putString(listId, iso).apply()
    }

    override fun remove(listId: String) {
        prefs.edit().remove(listId).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
