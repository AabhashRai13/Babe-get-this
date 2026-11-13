package com.babegetthis.android.feature.gotthis.domain

import java.util.TimeZone

// The phone's current time zone, read at use so a change of zone is picked up.
fun interface ZoneProvider {
    fun current(): TimeZone
}
