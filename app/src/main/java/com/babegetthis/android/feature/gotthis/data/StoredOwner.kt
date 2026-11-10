package com.babegetthis.android.feature.gotthis.data

/**
 * The account whose data is on the phone, read from storage rather than from
 * AuthStateManager, which is only initialised once an activity starts. Null
 * means a guest; a read that fails throws.
 */
fun interface StoredOwner {
    fun ownerId(): String?
}
