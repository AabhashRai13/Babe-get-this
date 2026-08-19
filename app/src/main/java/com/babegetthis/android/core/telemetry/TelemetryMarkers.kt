package com.babegetthis.android.core.telemetry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Remembers which once-only events have already fired.
//
// Activation events are only meaningful if they happen once. "First item
// added" reported on every item would not be an activation metric at all, it
// would be a duplicate of item_added with a misleading name — and it would
// quietly corrupt the funnel it exists to measure.
//
// Plain SharedPreferences, same as PrefsSyncPointStore: these are booleans, not
// secrets, and a Room column would mean a schema change and a migration for
// data that has nothing to do with the user's lists.
@Singleton
class TelemetryMarkers @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences("telemetry_markers", Context.MODE_PRIVATE)

    // Returns true the first time it is called for a given marker and scope,
    // false forever after. Write-then-report, so a crash between the two loses
    // one event rather than reporting it on every launch afterwards.
    //
    // `scope` is what "once" is counted against, and it differs per marker:
    // activation markers scope to the user id, so a second account on the same
    // device still records its own activation; SharedListFirstEdit scopes to
    // the list id, because a joiner's first edit is a fact about that list.
    fun firstTime(marker: Marker, scope: String?): Boolean {
        val key = key(marker, scope)
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    // Read without claiming. Needed because some markers are recorded to be
    // asked about later rather than to gate a single event — JoinedList is set
    // when a join succeeds and read on every subsequent edit to that list.
    fun has(marker: Marker, scope: String?): Boolean =
        prefs.getBoolean(key(marker, scope), false)

    // Record without reporting. Same idea in reverse: JoinedList marks a fact,
    // it does not correspond to an event of its own.
    fun mark(marker: Marker, scope: String?) {
        prefs.edit().putBoolean(key(marker, scope), true).apply()
    }

    private fun key(marker: Marker, scope: String?) = "${marker.name}:${scope ?: ANONYMOUS}"

    private companion object {
        // Signed-out users still add items, and their activation is still worth
        // measuring; it simply cannot be attributed to an account.
        const val ANONYMOUS = "anon"
    }
}

enum class Marker {
    // Activation, scoped to the user id.
    FirstItemAdded,
    FirstListCompleted,

    // Sharing, all scoped to the list id.
    //
    // ShareCodeCreated and JoinedList exist because "owner" and "joiner" are
    // not recorded anywhere locally — a shared list looks identical on both
    // devices once it syncs. Which side of the invite this device was on is
    // only knowable at the moment it happened, so it is written down then.
    ShareCodeCreated,
    JoinedList,
    SharedListFirstEdit,
}
